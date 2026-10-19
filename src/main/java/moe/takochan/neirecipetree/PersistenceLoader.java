package moe.takochan.neirecipetree;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import moe.takochan.neirecipetree.bom.AlternativePreferences;
import moe.takochan.neirecipetree.bom.TreeStorage;

/**
 * Deserializes the two save files on a background thread once every mod's registries are complete.
 *
 * <p>
 * Reading the NBT files, building the stored item stacks and parsing the NEI recipe ids are all pure
 * data work, so they are started from the post-init phase instead of being deferred to the first
 * time the player opens the inventory. Consumers join the corresponding future with
 * {@link #awaitTrees()} / {@link #awaitAlternatives()}, which is a no-op once the background work
 * has finished. Resolving a recipe id to a NEI handler still happens on the client thread when a
 * tree is opened, because NEI's handlers are not thread-safe.
 */
public final class PersistenceLoader {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "neirecipetree-saves");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static Future<?> treesFuture;
    private static Future<?> alternativesFuture;
    private static boolean started;

    private PersistenceLoader() {}

    /**
     * Starts deserializing both save files in the background. Must run after item registration, so
     * the stores are started from the post-init phase.
     */
    public static void startAsync() {
        if (started) {
            return;
        }
        started = true;
        treesFuture = EXECUTOR.submit(TreeStorage::loadAsync);
        alternativesFuture = EXECUTOR.submit(AlternativePreferences::loadAsync);
    }

    public static void awaitTrees() {
        await(treesFuture, "tree");
    }

    public static void awaitAlternatives() {
        await(alternativesFuture, "alternative");
    }

    private static void await(Future<?> future, String what) {
        if (future == null) {
            return;
        }
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        } catch (ExecutionException e) {
            LOG.warn("Could not deserialize the {} save: {}", what, e.getCause() == null ? e : e.getCause());
        }
    }
}
