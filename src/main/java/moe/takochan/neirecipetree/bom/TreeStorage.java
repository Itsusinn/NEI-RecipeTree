package moe.takochan.neirecipetree.bom;

import java.io.File;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps the list of opened recipe trees across game restarts.
 *
 * <p>
 * Only the tree goal and batch size are written; the recipe chain is rebuilt from NEI on demand
 * when the tree is opened. The goal is enough to restore the inventory button's tree list, and
 * keeping the file small avoids a long recipe scan while the game is still starting.
 */
public final class TreeStorage {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final String FILE_NAME = "neirecipetree_trees.dat";
    private static final int FORMAT_VERSION = 2;
    private static final int NBT_COMPOUND = 10;

    private static File file;
    private static boolean loading;
    private static boolean loaded;

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
                ItemStack goal = readGoal(entry.getCompoundTag("goal"));
                if (goal == null || goal.getItem() == null) {
                    // The item no longer exists, for example after a mod was removed
                    continue;
                }
                MaterialTree tree = new MaterialTree(goal, false);
                tree.batches = Math.max(1L, entry.getLong("batches"));
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
     * Reads a goal written by this version or by the old numeric-id format.
     */
    private static ItemStack readGoal(NBTTagCompound goal) {
        String name = goal.getString("name");
        if (!name.isEmpty()) {
            Object registered = Item.itemRegistry.getObject(name);
            if (registered instanceof Item) {
                ItemStack stack = new ItemStack(
                    (Item) registered,
                    Math.max(1, goal.getInteger("count")),
                    goal.getInteger("damage"));
                if (goal.hasKey("tag", NBT_COMPOUND)) {
                    stack.setTagCompound(
                        (NBTTagCompound) goal.getCompoundTag("tag")
                            .copy());
                }
                return stack;
            }
        }
        // Format v1 stored only the numeric id; keep reading old files
        return ItemStack.loadItemStackFromNBT(goal);
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

            NBTTagList entries = new NBTTagList();
            for (MaterialTree tree : BoM.trees) {
                if (tree == null || tree.goal == null
                    || tree.goal.ingredient == null
                    || tree.goal.ingredient.getItem() == null) {
                    continue;
                }
                ItemStack ingredient = tree.goal.ingredient;
                NBTTagCompound goal = new NBTTagCompound();
                // Keep the numeric fields too, so a registry name that disappeared in a later
                // session can still fall back to the old loader
                ingredient.writeToNBT(goal);
                String name = Item.itemRegistry.getNameForObject(ingredient.getItem());
                if (name != null) {
                    goal.setString("name", name);
                }
                goal.setInteger("count", Math.max(1, ingredient.stackSize));
                goal.setInteger("damage", ingredient.getItemDamage());
                if (ingredient.getTagCompound() != null) {
                    goal.setTag(
                        "tag",
                        ingredient.getTagCompound()
                            .copy());
                }

                NBTTagCompound entry = new NBTTagCompound();
                entry.setTag("goal", goal);
                entry.setLong("batches", Math.max(1L, tree.batches));
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
