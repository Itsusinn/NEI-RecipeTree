package moe.takochan.neirecipetree.bom;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;
import moe.takochan.neirecipetree.recipe.RecipeLookup;

/**
 * Keeps the list of opened recipe trees across game restarts.
 *
 * <p>
 * Each tree stores its goal, batch size, and the recipes chosen for its nodes. The recipe chain is
 * still built lazily when the tree is opened, but the saved choices are applied first, so a tree
 * comes back with the same branches instead of being re-resolved from NEI defaults.
 */
public final class TreeStorage {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final String FILE_NAME = "neirecipetree_trees.dat";
    private static final int FORMAT_VERSION = 4;
    private static final int NBT_COMPOUND = 10;

    private static File file;
    private static boolean loading;
    private static boolean loaded;
    private static final Map<MaterialTree, List<PendingResolution>> pendingResolutions = new IdentityHashMap<>();

    private static final class PendingResolution {

        final ItemStackKey key;
        /** NEI's stable recipe identity, serialized with {@link RecipeId#toJsonObject()}. */
        final String recipeIdJson;
        /** Legacy identity used by format v3 files and as a fallback for recipes without a RecipeId. */
        final String handlerId;
        final int recipeIndex;
        final boolean cleared;

        PendingResolution(ItemStackKey key, String recipeIdJson, String handlerId, int recipeIndex, boolean cleared) {
            this.key = key;
            this.recipeIdJson = recipeIdJson;
            this.handlerId = handlerId;
            this.recipeIndex = recipeIndex;
            this.cleared = cleared;
        }
    }

    private TreeStorage() {}

    /**
     * Points the store at {@code configDir}; {@link #load()} restores it once the item registry is
     * up.
     */
    public static void init(File configDir) {
        if (configDir == null) {
            return;
        }
        file = new File(configDir, FILE_NAME);
    }

    /**
     * Rebuilds the in-memory tree list from disk. Trees are restored without calculating their
     * recipe chain: the chain is built by {@link BoM#activate(MaterialTree)} or by the tree screen,
     * so startup does not pay for every saved tree.
     */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File source = file;
        if (source == null || !source.isFile()) {
            return;
        }
        loading = true;
        try {
            NBTTagCompound root = CompressedStreamTools.read(source);
            NBTTagList entries = root.getTagList("trees", NBT_COMPOUND);
            int restored = 0;
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                ItemStack goal = readStack(entry.getCompoundTag("goal"));
                if (goal == null || goal.getItem() == null) {
                    // The item no longer exists, for example after a mod was removed
                    continue;
                }
                MaterialTree tree = new MaterialTree(goal, false);
                tree.batches = Math.max(1L, entry.getLong("batches"));
                readResolutions(tree, entry.getTagList("resolutions", NBT_COMPOUND));
                BoM.trees.add(tree);
                restored++;
            }
            if (!BoM.trees.isEmpty()) {
                int active = Math.max(0, Math.min(BoM.trees.size() - 1, root.getInteger("active")));
                BoM.tree = BoM.trees.get(active);
            }
            if (restored > 0) {
                LOG.info("Restored {} recipe tree(s)", restored);
            }
        } catch (Exception e) {
            LOG.warn("Could not read {}: {}", source, e.toString());
        } finally {
            loading = false;
        }
    }

    /**
     * Reads a stack written by this version or by the old numeric-id format.
     */
    private static ItemStack readStack(NBTTagCompound tag) {
        String name = tag.getString("name");
        if (!name.isEmpty()) {
            Object registered = Item.itemRegistry.getObject(name);
            if (registered instanceof Item) {
                ItemStack stack = new ItemStack(
                    (Item) registered,
                    Math.max(1, tag.getInteger("count")),
                    tag.getInteger("damage"));
                if (tag.hasKey("tag", NBT_COMPOUND)) {
                    stack.setTagCompound(
                        (NBTTagCompound) tag.getCompoundTag("tag")
                            .copy());
                }
                return stack;
            }
        }
        // Format v1/v2 stored only the numeric id; keep reading old files
        return ItemStack.loadItemStackFromNBT(tag);
    }

    private static void readResolutions(MaterialTree tree, NBTTagList entries) {
        if (entries.tagCount() == 0) {
            return;
        }
        List<PendingResolution> pending = new ArrayList<>();
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            ItemStackKey key = ItemStackKey.of(readStack(entry.getCompoundTag("key")));
            if (key == null) {
                continue;
            }
            if (entry.getBoolean("cleared")) {
                pending.add(new PendingResolution(key, null, null, -1, true));
            } else {
                // Stored as bytes, not an NBT string: recipe ids embed every ingredient's NBT and a
                // large GregTech recipe can exceed the 64 KiB limit of a string tag.
                byte[] recipeIdBytes = entry.getByteArray("recipeId");
                String recipeIdJson = recipeIdBytes.length == 0 ? ""
                    : new String(recipeIdBytes, StandardCharsets.UTF_8);
                pending.add(
                    new PendingResolution(
                        key,
                        recipeIdJson,
                        entry.getString("handler"),
                        entry.getInteger("index"),
                        false));
            }
        }
        if (!pending.isEmpty()) {
            pendingResolutions.put(tree, pending);
        }
    }

    /**
     * Applies the saved node recipes to {@code tree} before it is recalculated. Called when the tree
     * is first opened; resolving a handler is deferred until this point because NEI recipe handlers
     * are not available while the tree list itself is being loaded.
     */
    public static void applyResolutions(MaterialTree tree) {
        List<PendingResolution> pending = pendingResolutions.remove(tree);
        if (pending == null) {
            return;
        }
        int applied = 0;
        for (PendingResolution resolution : pending) {
            try {
                if (resolution.cleared) {
                    tree.resolutions.put(resolution.key, null);
                    continue;
                }
                NEIRecipeRef ref = resolveRecipeId(resolution.recipeIdJson);
                if (ref == null) {
                    ref = resolveRecipe(resolution.key, resolution.handlerId, resolution.recipeIndex);
                }
                if (ref == null) {
                    LOG.debug(
                        "Could not restore recipe {}:{} for {}",
                        resolution.handlerId,
                        resolution.recipeIndex,
                        resolution.key.toItemStack()
                            .getDisplayName());
                    continue;
                }
                tree.resolutions.put(resolution.key, ref);
                applied++;
            } catch (Exception e) {
                LOG.warn("Could not restore a saved recipe: {}", e.toString());
            }
        }
        if (applied > 0) {
            LOG.debug("Restored {} node recipe selection(s)", applied);
        }
    }

    private static NEIRecipeRef resolveRecipe(ItemStackKey key, String handlerId, int recipeIndex) {
        if (handlerId == null || handlerId.isEmpty() || recipeIndex < 0) {
            return null;
        }
        // Prefer the filtered list: the recipe must actually produce this node's item
        for (NEIRecipeRef ref : RecipeLookup.findRecipes(key.toItemStack())) {
            if (ref.recipeIndex == recipeIndex && handlerId.equals(ref.handler.getHandlerId())) {
                return ref;
            }
        }
        // Secondary outputs are not always returned by the item lookup, so fall back to the full
        // handler list and trust the stored handler id/index
        if (GuiCraftingRecipe.craftinghandlers != null) {
            for (ICraftingHandler handler : GuiCraftingRecipe.craftinghandlers) {
                if (handlerId.equals(handler.getHandlerId()) && recipeIndex < handler.numRecipes()) {
                    return new NEIRecipeRef(handler, recipeIndex);
                }
            }
        }
        return null;
    }

    /**
     * Restores a recipe through NEI's own stable identity.
     *
     * <p>
     * The handler id is only {@code getClass().getName()} by default, so every GregTech machine
     * handler shares {@code gregtech.nei.GTNEIDefaultHandler}; pairing it with a recipe index can
     * bind the saved choice to a different machine after a restart. A {@link RecipeId} keeps the
     * handler registration name and the actual ingredients/result, which is what NEI bookmarks use
     * to find a recipe again in a later session.
     */
    private static NEIRecipeRef resolveRecipeId(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonObject object = new JsonParser().parse(json)
                .getAsJsonObject();
            RecipeHandlerRef ref = RecipeHandlerRef.of(RecipeId.of(object));
            if (ref != null) {
                return new NEIRecipeRef(ref.handler, ref.recipeIndex);
            }
        } catch (Exception e) {
            LOG.debug("Could not resolve a saved recipe id: {}", e.toString());
        }
        return null;
    }

    /**
     * Serializes a recipe with NEI's stable identity, or {@code null} when the handler cannot
     * describe the recipe that way.
     */
    private static String recipeIdJson(NEIRecipeRef ref) {
        if (ref == null || ref.handler == null) {
            return null;
        }
        try {
            RecipeId id = RecipeId.of(ref.handler, ref.recipeIndex);
            return id == null ? null
                : id.toJsonObject()
                    .toString();
        } catch (Exception e) {
            LOG.debug(
                "Could not serialize recipe {}:{}: {}",
                ref.handler.getHandlerId(),
                ref.recipeIndex,
                e.toString());
            return null;
        }
    }

    private static NBTTagCompound writeStack(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        // Keep the numeric fields too, so a registry name that disappeared in a later session can
        // still fall back to the old loader
        stack.writeToNBT(tag);
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        if (name != null) {
            tag.setString("name", name);
        }
        tag.setInteger("count", Math.max(1, stack.stackSize));
        tag.setInteger("damage", stack.getItemDamage());
        if (stack.getTagCompound() != null) {
            tag.setTag(
                "tag",
                stack.getTagCompound()
                    .copy());
        }
        return tag;
    }

    /**
     * Writes the current tree list. Called whenever a tree is added, removed, or selected.
     */
    public static void save() {
        if (!loaded || loading) {
            return;
        }
        File target = file;
        if (target == null) {
            return;
        }
        try {
            NBTTagCompound root = new NBTTagCompound();
            root.setInteger("version", FORMAT_VERSION);

            // A save can happen before a restored tree has been opened (for example when the T
            // button appends a tree). Apply its pending choices first so they are not overwritten.
            for (MaterialTree tree : BoM.trees) {
                applyResolutions(tree);
            }

            NBTTagList entries = new NBTTagList();
            for (MaterialTree tree : BoM.trees) {
                if (tree == null || tree.goal == null
                    || tree.goal.ingredient == null
                    || tree.goal.ingredient.getItem() == null) {
                    continue;
                }
                ItemStack ingredient = tree.goal.ingredient;
                NBTTagCompound goal = writeStack(ingredient);

                NBTTagList resolutionTags = new NBTTagList();
                for (Map.Entry<ItemStackKey, NEIRecipeRef> resolution : tree.resolutions.entrySet()) {
                    ItemStackKey key = resolution.getKey();
                    if (key == null) {
                        continue;
                    }
                    NBTTagCompound resolutionTag = new NBTTagCompound();
                    resolutionTag.setTag("key", writeStack(key.toItemStack()));
                    NEIRecipeRef ref = resolution.getValue();
                    if (ref == null) {
                        resolutionTag.setBoolean("cleared", true);
                    } else {
                        String recipeId = recipeIdJson(ref);
                        if (recipeId != null) {
                            resolutionTag.setByteArray("recipeId", recipeId.getBytes(StandardCharsets.UTF_8));
                        }
                        String handlerId = ref.handler.getHandlerId();
                        resolutionTag.setString("handler", handlerId == null ? "" : handlerId);
                        resolutionTag.setInteger("index", ref.recipeIndex);
                    }
                    resolutionTags.appendTag(resolutionTag);
                }

                NBTTagCompound entry = new NBTTagCompound();
                entry.setTag("goal", goal);
                entry.setLong("batches", Math.max(1L, tree.batches));
                entry.setTag("resolutions", resolutionTags);
                entries.appendTag(entry);
            }
            root.setTag("trees", entries);
            root.setInteger("active", BoM.tree == null ? 0 : Math.max(0, BoM.trees.indexOf(BoM.tree)));

            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                LOG.warn("Could not create config directory {}", parent);
                return;
            }
            CompressedStreamTools.write(root, target);
        } catch (Exception e) {
            LOG.warn("Could not save {}: {}", target, e.toString());
        }
    }
}
