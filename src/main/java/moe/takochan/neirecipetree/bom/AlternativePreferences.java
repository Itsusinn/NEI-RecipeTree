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

import moe.takochan.neirecipetree.PersistenceLoader;
import moe.takochan.neirecipetree.recipe.FluidStacks;
import moe.takochan.neirecipetree.recipe.ItemStackKey;

/**
 * Remembers which alternative the player picked for a substitutable slot.
 *
 * <p>
 * Choices are keyed by the slot's primary item, so every recipe offering the same slot (and every
 * tree rebuilt later on) reuses the pick instead of falling back to the first alternative. Picking
 * the primary item again forgets the entry, and the whole map is written to the config directory so
 * the preference survives a restart. Stacks are stored by registry name, because GTNH's EndlessIDs
 * can remap numeric item ids between sessions.
 */
public final class AlternativePreferences {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final String FILE_NAME = "neirecipetree_alternatives.dat";
    private static final int FORMAT_VERSION = 3;
    private static final int NBT_COMPOUND = 10;

    private static final Map<ItemStackKey, ItemStackKey> choices = new HashMap<>();
    private static File file;
    private static volatile boolean loaded;
    private static volatile Map<ItemStackKey, ItemStackKey> snapshot;

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
        load();
        if (permutations == null || permutations.length <= 1 || choices.isEmpty()) {
            return -1;
        }
        ItemStackKey wanted = choices.get(keyOf(permutations[0]));
        if (wanted == null) {
            return -1;
        }
        for (int i = 0; i < permutations.length; i++) {
            if (wanted.equals(keyOf(permutations[i]))) {
                if (i > 0) {
                    LOG.debug(
                        "Restored alternative {} for slot {}",
                        permutations[i].getDisplayName(),
                        permutations[0].getDisplayName());
                }
                return i;
            }
        }
        LOG.debug(
            "No remembered alternative {} in slot {}",
            wanted.toItemStack()
                .getDisplayName(),
            permutations[0].getDisplayName());
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
        // Load first: saving below rewrites the whole file, so an unread store would be wiped.
        load();
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
        load();
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
     * Reads the stored choices, joining the background deserialization if it has not finished yet.
     */
    public static void load() {
        if (loaded) {
            return;
        }
        PersistenceLoader.awaitAlternatives();
        if (loaded) {
            return;
        }
        synchronized (AlternativePreferences.class) {
            if (loaded) {
                return;
            }
            Map<ItemStackKey, ItemStackKey> data = snapshot;
            if (data == null) {
                data = readSnapshot(file);
            }
            if (data != null && !data.isEmpty()) {
                choices.putAll(data);
                LOG.info("Loaded {} remembered alternative choice(s)", data.size());
            }
            loaded = true;
        }
    }

    /** Starts deserializing the alternative store on the background loader thread. */
    public static void loadAsync() {
        Map<ItemStackKey, ItemStackKey> data = readSnapshot(file);
        snapshot = data;
        if (data != null) {
            LOG.debug("Deserialized {} alternative choice(s) in the background", data.size());
        }
    }

    /**
     * Reads the stored choices into plain data. Runs on the background loader, so it must not touch
     * the live {@link #choices} map.
     */
    private static Map<ItemStackKey, ItemStackKey> readSnapshot(File source) {
        if (source == null || !source.isFile()) {
            return new HashMap<>();
        }
        try {
            NBTTagCompound root = CompressedStreamTools.read(source);
            NBTTagList entries = root.getTagList("entries", NBT_COMPOUND);
            Map<ItemStackKey, ItemStackKey> result = new HashMap<>();
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                ItemStack slot = TreeStorage.readStack(entry.getCompoundTag("slot"));
                ItemStack chosen = TreeStorage.readStack(entry.getCompoundTag("chosen"));
                if (slot == null || chosen == null) {
                    // The item no longer exists, for example after a mod was removed
                    continue;
                }
                ItemStackKey slotKey = keyOf(slot);
                ItemStackKey chosenKey = keyOf(chosen);
                if (slotKey != null && chosenKey != null) {
                    result.put(slotKey, chosenKey);
                }
            }
            return result;
        } catch (Exception e) {
            LOG.warn("Could not read {}: {}", source, e.toString());
            return null;
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
                    TreeStorage.writeStack(
                        choice.getKey()
                            .toItemStack()));
                entry.setTag(
                    "chosen",
                    TreeStorage.writeStack(
                        choice.getValue()
                            .toItemStack()));
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
