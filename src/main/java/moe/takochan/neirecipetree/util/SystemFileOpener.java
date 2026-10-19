package moe.takochan.neirecipetree.util;

import java.awt.Desktop;
import java.io.File;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Launches the operating system's default handler for a file.
 *
 * <p>
 * AWT is not usable on every 1.7.10 setup (headless launches, some Linux environments), so the
 * {@link Desktop} API is only the first attempt and each platform's shell command is used as the
 * fallback.
 */
public final class SystemFileOpener {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");

    private SystemFileOpener() {}

    /**
     * Opens the file with the application registered for its type on this system.
     *
     * @return {@code true} when a handler was started
     */
    public static boolean openFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        if (openWithDesktop(file)) {
            return true;
        }
        if (isWindows()) {
            // FileProtocolHandler resolves through the shell's file association, like a double click
            return run("rundll32.exe", "url.dll,FileProtocolHandler", file.getAbsolutePath());
        }
        if (isMac()) {
            return run("open", file.getAbsolutePath());
        }
        return run("xdg-open", file.getAbsolutePath());
    }

    /**
     * Opens the folder containing {@code file}, selecting the file itself where the platform allows
     * it.
     *
     * @return {@code true} when the folder was opened
     */
    public static boolean revealFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        File folder = file.getAbsoluteFile()
            .getParentFile();
        if (folder == null) {
            return false;
        }
        if (isWindows()) {
            // One single token, so the path may contain spaces: explorer parses "/select,<path>"
            return run("explorer.exe", "/select," + file.getAbsolutePath());
        }
        if (isMac()) {
            return run("open", "-R", file.getAbsolutePath());
        }
        // No portable "select this file" hint on Linux; opening the folder is the best we can do
        return run("xdg-open", folder.getAbsolutePath());
    }

    /**
     * Opens the file, falling back to revealing its folder when no handler could be started.
     *
     * @return {@code true} when the file could be opened at least through its folder
     */
    public static boolean openOrReveal(File file) {
        if (openFile(file)) {
            return true;
        }
        return revealFile(file);
    }

    private static boolean openWithDesktop(File file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop desktop = Desktop.getDesktop();
            if (desktop == null || !desktop.isSupported(Desktop.Action.OPEN)) {
                return false;
            }
            desktop.open(file);
            return true;
        } catch (Exception | LinkageError e) {
            // Headless environments, missing AWT toolkits and unregistered file types land here
            LOG.debug("Desktop.open() unavailable for {}: {}", file, e.toString());
            return false;
        }
    }

    private static boolean run(String... command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (Exception e) {
            LOG.debug("Could not start '{}': {}", String.join(" ", command), e.toString());
            return false;
        }
    }

    private static String osName() {
        String name = System.getProperty("os.name");
        return name != null ? name.toLowerCase(Locale.ROOT) : "";
    }

    private static boolean isWindows() {
        return osName().contains("win");
    }

    private static boolean isMac() {
        String name = osName();
        return name.contains("mac") || name.contains("darwin");
    }
}
