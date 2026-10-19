package moe.takochan.neirecipetree.bom;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import moe.takochan.neirecipetree.recipe.FluidStacks;
import moe.takochan.neirecipetree.recipe.ItemStackKey;

/**
 * Remembers which alternative the player picked for a substitutable slot.
 *
 * <p>
 * Choices are keyed by the slot's primary item, so every recipe offering the same slot (and every
 * tree rebuilt later on) reuses the pick instead of falling back to the first alternative. Picking
 * the primary item again forgets the entry, and the whole map is written to the config directory so
 * the preference survives a restart.
 */
public final class AlternativePreferences {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final String FILE_NAME = "neirecipetree_alternatives.dat";
    private static final int FORMAT_VERSION = 1;
    private static final int NBT_COMPOUND = 10;

    private static final Map<ItemStackKey, ItemStackKey> choices = new HashMap<>();
    private static File file;

    private AlternativePreferences() {}

    /**
     * Points the store at {@code configDir}; {@link #load()} fills it once the item registry is up.
     */
    public static void init(File configDir) {
        if (configDir == null) {
            return;
        }
        file = new File(configDir, FILE_NAME);
    }

    /**
     * Index of the remembered alternative inside {@code permutations}; {@code -1} when the slot has
     * no usable preference.
     */
    public static int findRemembered(ItemStack[] permutations) {
        if (permutations == null || permutations.length <= 1 || choices.isEmpty()) {
            return -1;
        }
        ItemStackKey wanted = choices.get(keyOf(permutations[0]));
        if (wanted == null) {
            return -1;
        }
        for (int i = 0; i < permutations.length; i++) {
            if (wanted.equals(keyOf(permutations[i]))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Stores the pick for the slot described by {@code permutations}. Choosing the primary item
     * again drops the entry, so the slot goes back to its default.
     */
    public static void remember(ItemStack[] permutations, int chosenIndex) {
        if (permutations == null || permutations.length <= 1 || chosenIndex < 0 || chosenIndex >= permutations.length) {
            return;
        }
        ItemStackKey slot = keyOf(permutations[0]);
        if (slot == null) {
            return;
        }
        boolean changed;
        if (chosenIndex == 0) {
            changed = choices.remove(slot) != null;
        } else {
            ItemStackKey chosen = keyOf(permutations[chosenIndex]);
            changed = chosen != null && !chosen.equals(choices.put(slot, chosen));
        }
        if (changed) {
            save();
        }
    }

    public static void forgetAll() {
        if (choices.isEmpty()) {
            return;
        }
        choices.clear();
        save();
    }

    /**
     * Slot keys ignore fluid volumes, so a fluid slot keeps matching even when another recipe asks
     * for a different amount of it.
     */
    private static ItemStackKey keyOf(ItemStack stack) {
        return ItemStackKey.of(FluidStacks.aggregationKey(stack));
    }

    /**
     * Reads the stored choices. Called from the init phase, once every mod's items are registered,
     * because entries reference items by id.
     */
    public static void load() {
        File source = file;
        if (source == null || !source.isFile()) {
            return;
        }
        try {
            NBTTagCompound root = CompressedStreamTools.read(source);
            NBTTagList entries = root.getTagList("entries", NBT_COMPOUND);
            int loaded = 0;
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                ItemStack slot = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("slot"));
                ItemStack chosen = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("chosen"));
                if (slot == null || chosen == null) {
                    // The item no longer exists, for example after a mod was removed
                    continue;
                }
                ItemStackKey slotKey = keyOf(slot);
                ItemStackKey chosenKey = keyOf(chosen);
                if (slotKey != null && chosenKey != null) {
                    choices.put(slotKey, chosenKey);
                    loaded++;
                }
            }
            if (loaded > 0) {
                LOG.info("Loaded {} remembered alternative choice(s)", loaded);
            }
        } catch (Exception e) {
            LOG.warn("Could not read {}: {}", source, e.toString());
        }
    }

    private static void save() {
        File target = file;
        if (target == null) {
            return;
        }
        try {
            NBTTagCompound root = new NBTTagCompound();
            root.setInteger("version", FORMAT_VERSION);
            NBTTagList entries = new NBTTagList();
            for (Map.Entry<ItemStackKey, ItemStackKey> choice : choices.entrySet()) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setTag(
                    "slot",
                    choice.getKey()
                        .toItemStack()
                        .writeToNBT(new NBTTagCompound()));
                entry.setTag(
                    "chosen",
                    choice.getValue()
                        .toItemStack()
                        .writeToNBT(new NBTTagCompound()));
                entries.appendTag(entry);
            }
            root.setTag("entries", entries);

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
