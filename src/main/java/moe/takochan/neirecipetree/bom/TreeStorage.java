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

import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import moe.takochan.neirecipetree.PersistenceLoader;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;

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
    private static final int FORMAT_VERSION = 7;
    private static final int NBT_COMPOUND = 10;

    /** Which screen the inventory button should reopen after the player closes the GUI. */
    public enum View {

        SELECTOR,
        TREE
    }

    private static File file;
    private static volatile boolean loaded;
    private static volatile TreeSnapshot snapshot;
    private static View lastView = View.SELECTOR;
    private static final Map<MaterialTree, List<PendingResolution>> pendingResolutions = new IdentityHashMap<>();

    private static final class PendingResolution {

        final ItemStackKey key;
        /** NEI's stable recipe identity, already parsed from its stored JSON. */
        final RecipeId recipeId;
        final boolean cleared;

        PendingResolution(ItemStackKey key, RecipeId recipeId, boolean cleared) {
            this.key = key;
            this.recipeId = recipeId;
            this.cleared = cleared;
        }
    }

    /** Plain data produced by the background reader; no live tree objects are created there. */
    private static final class TreeSnapshot {

        final List<SavedTree> trees;
        final int active;
        final View view;

        TreeSnapshot(List<SavedTree> trees, int active, View view) {
            this.trees = trees;
            this.active = active;
            this.view = view;
        }
    }

    private static final class SavedTree {

        final ItemStack goal;
        final long batches;
        final List<PendingResolution> resolutions;
        final List<ItemStack> removedMaterials;

        SavedTree(ItemStack goal, long batches, List<PendingResolution> resolutions, List<ItemStack> removedMaterials) {
            this.goal = goal;
            this.batches = batches;
            this.resolutions = resolutions;
            this.removedMaterials = removedMaterials;
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

    /** Starts deserializing the save file on the background loader thread. */
    public static void loadAsync() {
        TreeSnapshot data = readSnapshot(file);
        snapshot = data;
        if (data != null) {
            LOG.debug("Deserialized {} saved tree(s) in the background", data.trees.size());
        }
    }

    /**
     * Restores the in-memory tree list, joining the background deserialization if it has not
     * finished yet. Trees are restored without calculating their recipe chain: the chain is built
     * by {@link BoM#activate(MaterialTree)} or by the tree screen, so startup does not pay for every
     * saved tree.
     */
    public static void load() {
        if (loaded) {
            return;
        }
        PersistenceLoader.awaitTrees();
        if (loaded) {
            return;
        }
        synchronized (TreeStorage.class) {
            if (loaded) {
                return;
            }
            TreeSnapshot data = snapshot;
            if (data == null) {
                data = readSnapshot(file);
            }
            if (data != null) {
                applySnapshot(data);
            }
            loaded = true;
        }
    }

    private static void applySnapshot(TreeSnapshot data) {
        BoM.trees.clear();
        for (SavedTree saved : data.trees) {
            MaterialTree tree = new MaterialTree(saved.goal, false);
            tree.batches = saved.batches;
            List<ItemStackKey> removedKeys = new ArrayList<>();
            for (ItemStack removed : saved.removedMaterials) {
                ItemStackKey key = ItemStackKey.of(removed);
                if (key != null && tree.removedMaterials.add(key)) {
                    removedKeys.add(key);
                }
            }
            tree.loadRemovalHistory(removedKeys);
            if (!saved.resolutions.isEmpty()) {
                pendingResolutions.put(tree, saved.resolutions);
            }
            BoM.trees.add(tree);
        }
        if (!BoM.trees.isEmpty()) {
            int active = Math.max(0, Math.min(BoM.trees.size() - 1, data.active));
            BoM.tree = BoM.trees.get(active);
        }
        lastView = data.view == View.TREE && BoM.tree != null ? View.TREE : View.SELECTOR;
        if (!data.trees.isEmpty()) {
            LOG.info("Restored {} recipe tree(s)", data.trees.size());
        }
    }

    /** The screen the inventory button opened last: the active tree or the tree list. */
    public static View getLastView() {
        return lastView;
    }

    public static void setLastView(View view) {
        lastView = view == null ? View.SELECTOR : view;
    }

    /**
     * Reads the whole save file into plain data. Runs on the background loader, so it must not touch
     * the live tree list or NEI handlers.
     */
    private static TreeSnapshot readSnapshot(File source) {
        if (source == null || !source.isFile()) {
            return new TreeSnapshot(new ArrayList<>(), 0, View.SELECTOR);
        }
        try {
            NBTTagCompound root = CompressedStreamTools.read(source);
            NBTTagList entries = root.getTagList("trees", NBT_COMPOUND);
            List<SavedTree> trees = new ArrayList<>();
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                ItemStack goal = readStack(entry.getCompoundTag("goal"));
                if (goal == null || goal.getItem() == null) {
                    // The item no longer exists, for example after a mod was removed
                    continue;
                }
                trees.add(
                    new SavedTree(
                        goal,
                        Math.max(1L, entry.getLong("batches")),
                        readResolutions(entry.getTagList("resolutions", NBT_COMPOUND)),
                        readRemovedMaterials(entry.getTagList("removed", NBT_COMPOUND))));
            }
            return new TreeSnapshot(
                trees,
                root.getInteger("active"),
                root.getInteger("lastView") == 1 ? View.TREE : View.SELECTOR);
        } catch (Exception e) {
            LOG.warn("Could not read {}: {}", source, e.toString());
            return null;
        }
    }

    private static List<ItemStack> readRemovedMaterials(NBTTagList entries) {
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < entries.tagCount(); i++) {
            ItemStack stack = readStack(entries.getCompoundTagAt(i));
            if (stack != null && stack.getItem() != null) {
                removed.add(stack);
            }
        }
        return removed;
    }

    private static List<PendingResolution> readResolutions(NBTTagList entries) {
        List<PendingResolution> pending = new ArrayList<>();
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            ItemStackKey key = ItemStackKey.of(readStack(entry.getCompoundTag("key")));
            if (key == null) {
                continue;
            }
            if (entry.getBoolean("cleared")) {
                pending.add(new PendingResolution(key, null, true));
                continue;
            }
            // Stored as bytes, not an NBT string: recipe ids embed every ingredient's NBT and a
            // large GregTech recipe can exceed the 64 KiB limit of a string tag.
            RecipeId recipeId = parseRecipeId(entry.getByteArray("recipeId"));
            if (recipeId != null) {
                pending.add(new PendingResolution(key, recipeId, false));
            }
        }
        return pending;
    }

    private static RecipeId parseRecipeId(byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        try {
            JsonObject object = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
            return RecipeId.of(object);
        } catch (Exception e) {
            LOG.debug("Could not parse a saved recipe id: {}", e.toString());
            return null;
        }
    }

    /**
     * Reads a stack written by {@link #writeStack(ItemStack)}.
     */
    static ItemStack readStack(NBTTagCompound tag) {
        String name = tag.getString("name");
        if (name.isEmpty()) {
            return null;
        }
        Object registered = Item.itemRegistry.getObject(name);
        if (!(registered instanceof Item)) {
            return null;
        }
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

    /**
     * Applies the saved node recipes to {@code tree} before it is recalculated. Called when the tree
     * is first opened; resolving a {@link RecipeId} to a handler happens here because NEI's handlers
     * are not thread-safe.
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
                RecipeHandlerRef handlerRef = RecipeHandlerRef.of(resolution.recipeId);
                if (handlerRef == null) {
                    LOG.debug(
                        "Could not restore recipe for {}",
                        resolution.key.toItemStack()
                            .getDisplayName());
                    continue;
                }
                tree.resolutions.put(resolution.key, new NEIRecipeRef(handlerRef.handler, handlerRef.recipeIndex));
                applied++;
            } catch (Exception e) {
                LOG.warn("Could not restore a saved recipe: {}", e.toString());
            }
        }
        if (applied > 0) {
            LOG.debug("Restored {} node recipe selection(s)", applied);
        }
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

    static NBTTagCompound writeStack(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
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
        if (!loaded) {
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
                        resolutionTags.appendTag(resolutionTag);
                        continue;
                    }
                    String recipeId = recipeIdJson(ref);
                    if (recipeId == null) {
                        LOG.debug(
                            "Skipping recipe without a stable id: {}:{}",
                            ref.handler.getHandlerId(),
                            ref.recipeIndex);
                        continue;
                    }
                    resolutionTag.setByteArray("recipeId", recipeId.getBytes(StandardCharsets.UTF_8));
                    resolutionTags.appendTag(resolutionTag);
                }

                NBTTagCompound entry = new NBTTagCompound();
                entry.setTag("goal", goal);
                entry.setLong("batches", Math.max(1L, tree.batches));
                entry.setTag("resolutions", resolutionTags);

                NBTTagList removedTags = new NBTTagList();
                for (ItemStackKey key : tree.getRemovalHistory()) {
                    if (key != null) {
                        removedTags.appendTag(writeStack(key.toItemStack()));
                    }
                }
                entry.setTag("removed", removedTags);
                entries.appendTag(entry);
            }
            root.setTag("trees", entries);
            root.setInteger("active", BoM.tree == null ? 0 : Math.max(0, BoM.trees.indexOf(BoM.tree)));
            root.setInteger("lastView", lastView == View.TREE ? 1 : 0);

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
