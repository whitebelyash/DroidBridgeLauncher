package ca.dnamobile.javalauncher.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;

import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.utils.path.PathManager;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;

/**
 * Shared runtime validation helpers for JavaLauncher game launches.
 *
 * Important for Android 14-16:
 * - Game launches use VMLauncher/JLI, not an external bin/java process.
 * - Java 8 runtime packs may not expose a standalone bin/java in the final layout.
 * - Java 8 should be considered installed when the VM/class-library files exist:
 *   rt.jar + libjvm.so + the MultiRTUtils version marker.
 *
 * Forge/NeoForge installer launches still require Java 17+/21+ bin/java and are
 * handled by JavaGameLauncher.launchRawJavaArgs(), not by this Java 8 path.
 */
public final class RuntimeCompat {
    private static final String TAG = "RuntimeCompat";
    public static final String PATCH_ID = "JRE8_ANDROID_14_16_V31";

    private RuntimeCompat() {
    }

    @NonNull
    public static File getRuntimeDirectory(@NonNull String runtimeName) {
        try {
            File dir = MultiRTUtils.getRuntimeDir(runtimeName);
            if (dir != null) return dir;
        } catch (Throwable ignored) {
        }
        return new File(PathManager.DIR_MULTIRT_HOME, runtimeName);
    }

    @NonNull
    public static File resolveRuntimeForJava(int javaMajor) {
        String[] preferred = preferredRuntimes(javaMajor);
        for (String name : preferred) {
            File dir = getRuntimeDirectory(name);
            if (isRuntimeInstalledForJava(name, dir, javaMajor)) {
                Logging.i(TAG, "Runtime patch active: " + PATCH_ID + " selected " + name + " -> " + dir.getAbsolutePath());
                return dir;
            }
            Logging.i(TAG, "Runtime " + name + " is not usable for Java " + javaMajor + ": " + describeRuntimeState(name, dir));
        }
        throw new IllegalStateException("No launchable internal Java runtime is installed for Java " + javaMajor);
    }

    @NonNull
    private static String[] preferredRuntimes(int javaMajor) {
        if (javaMajor >= 25) {
            return new String[]{"Internal-25", "Internal-21", "Internal-17", "Internal-8"};
        } else if (javaMajor >= 21) {
            return new String[]{"Internal-21", "Internal-25", "Internal-17", "Internal-8"};
        } else if (javaMajor >= 17) {
            return new String[]{"Internal-17", "Internal-21", "Internal-25", "Internal-8"};
        }
        return new String[]{"Internal-8", "Internal-17", "Internal-21", "Internal-25"};
    }

    @NonNull
    public static File normalizeRuntimeHome(@NonNull String runtimeName, @NonNull File runtimeHome, int javaMajor) {
        if (isJava8Runtime(runtimeName) || javaMajor <= 8) {
            File java8Home = findJava8Home(runtimeHome);
            return java8Home != null ? java8Home : runtimeHome;
        }

        File modernHome = findModernJavaHome(runtimeHome);
        return modernHome != null ? modernHome : runtimeHome;
    }

    public static boolean isRuntimeInstalledForDisplay(@NonNull String runtimeName) {
        File dir = getRuntimeDirectory(runtimeName);
        int javaMajor = javaMajorForRuntimeName(runtimeName);
        return isRuntimeInstalledForJava(runtimeName, dir, javaMajor);
    }

    public static boolean isRuntimeInstalledForJava(@NonNull String runtimeName, @NonNull File runtimeHome, int javaMajor) {
        if (!runtimeHome.isDirectory()) return false;

        if (isJava8Runtime(runtimeName) || javaMajor <= 8) {
            String marker = safeRuntimeVersion(runtimeName);
            File javaHome = findJava8Home(runtimeHome);
            return !isBlank(marker)
                    && javaHome != null
                    && findLibJvm(javaHome) != null
                    && findFileNamed(javaHome, "rt.jar", 8) != null;
        }

        File javaHome = findModernJavaHome(runtimeHome);
        return javaHome != null
                && findJavaBinary(javaHome) != null
                && findLibJvm(javaHome) != null
                && findFileNamed(javaHome, "modules", 8) != null;
    }

    @NonNull
    public static String describeRuntimeState(@NonNull String runtimeName, @NonNull File runtimeHome) {
        if (!runtimeHome.exists()) return "missing folder " + runtimeHome.getAbsolutePath();
        if (!runtimeHome.isDirectory()) return "not a directory " + runtimeHome.getAbsolutePath();

        String marker = safeRuntimeVersion(runtimeName);
        File java8Home = findJava8Home(runtimeHome);
        File modernHome = findModernJavaHome(runtimeHome);
        File javaHome = java8Home != null ? java8Home : modernHome;
        File java = javaHome != null ? findJavaBinary(javaHome) : null;
        File libJvm = javaHome != null ? findLibJvm(javaHome) : null;
        File rtJar = javaHome != null ? findFileNamed(javaHome, "rt.jar", 8) : null;
        File modules = javaHome != null ? findFileNamed(javaHome, "modules", 8) : null;

        return "path=" + runtimeHome.getAbsolutePath()
                + ", marker=" + marker
                + ", javaHome=" + (javaHome != null ? javaHome.getAbsolutePath() : "<missing>")
                + ", binJava=" + (java != null ? java.getAbsolutePath() : "<missing>")
                + ", libjvm=" + (libJvm != null ? libJvm.getAbsolutePath() : "<missing>")
                + ", rt.jar=" + (rtJar != null ? rtJar.getAbsolutePath() : "<missing>")
                + ", modules=" + (modules != null ? modules.getAbsolutePath() : "<missing>");
    }

    @Nullable
    public static File findJavaBinary(@NonNull File javaHome) {
        File direct = new File(javaHome, "bin/java");
        if (direct.isFile()) return direct;
        return findFileNamed(javaHome, "java", 4);
    }

    @Nullable
    public static File findJava8Home(@NonNull File runtimeHome) {
        File direct = new File(runtimeHome, "lib/rt.jar");
        if (direct.isFile()) return runtimeHome;

        File nestedJre = new File(runtimeHome, "jre/lib/rt.jar");
        if (nestedJre.isFile()) return new File(runtimeHome, "jre");

        File rtJar = findFileNamed(runtimeHome, "rt.jar", 10);
        if (rtJar == null) return null;

        File libDir = rtJar.getParentFile();
        if (libDir == null || !"lib".equals(libDir.getName())) return null;

        File javaHome = libDir.getParentFile();
        return javaHome != null && javaHome.isDirectory() ? javaHome : null;
    }

    @Nullable
    public static File findModernJavaHome(@NonNull File runtimeHome) {
        File direct = new File(runtimeHome, "lib/modules");
        if (direct.isFile()) return runtimeHome;

        File nested = new File(runtimeHome, "jre/lib/modules");
        if (nested.isFile()) return new File(runtimeHome, "jre");

        File modules = findFileNamed(runtimeHome, "modules", 10);
        if (modules == null) return null;

        File libDir = modules.getParentFile();
        if (libDir == null || !"lib".equals(libDir.getName())) return null;

        File javaHome = libDir.getParentFile();
        return javaHome != null && javaHome.isDirectory() ? javaHome : null;
    }

    @Nullable
    public static File findLibJvm(@NonNull File javaHome) {
        ArrayList<File> candidates = new ArrayList<>();
        candidates.add(new File(javaHome, "lib/server/libjvm.so"));
        candidates.add(new File(javaHome, "lib/client/libjvm.so"));
        candidates.add(new File(javaHome, "lib/aarch64/server/libjvm.so"));
        candidates.add(new File(javaHome, "lib/aarch64/client/libjvm.so"));
        candidates.add(new File(javaHome, "lib/arm64/server/libjvm.so"));
        candidates.add(new File(javaHome, "lib/arm64/client/libjvm.so"));
        candidates.add(new File(javaHome, "lib/arm64-v8a/server/libjvm.so"));
        candidates.add(new File(javaHome, "lib/arm64-v8a/client/libjvm.so"));
        candidates.add(new File(javaHome, "lib/arm/server/libjvm.so"));
        candidates.add(new File(javaHome, "lib/arm/client/libjvm.so"));
        candidates.add(new File(javaHome, "lib/x86_64/server/libjvm.so"));
        candidates.add(new File(javaHome, "lib/x86_64/client/libjvm.so"));
        candidates.add(new File(javaHome, "lib/i386/server/libjvm.so"));
        candidates.add(new File(javaHome, "lib/i386/client/libjvm.so"));

        for (File candidate : candidates) {
            if (candidate.isFile()) return candidate;
        }
        return findFileNamed(javaHome, "libjvm.so", 10);
    }

    @Nullable
    public static File findFileNamed(@NonNull File root, @NonNull String name, int depthLeft) {
        if (depthLeft < 0 || !root.isDirectory()) return null;
        File[] children = root.listFiles();
        if (children == null) return null;

        for (File child : children) {
            if (child.isFile() && name.equals(child.getName())) return child;
            if (child.isDirectory()) {
                File found = findFileNamed(child, name, depthLeft - 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static int javaMajorForRuntimeName(@NonNull String runtimeName) {
        if (runtimeName.contains("25")) return 25;
        if (runtimeName.contains("21")) return 21;
        if (runtimeName.contains("17")) return 17;
        if (runtimeName.contains("8")) return 8;
        return 8;
    }

    private static boolean isJava8Runtime(@NonNull String runtimeName) {
        return runtimeName.contains("8");
    }

    @Nullable
    private static String safeRuntimeVersion(@NonNull String runtimeName) {
        try {
            return emptyToNull(MultiRTUtils.readInternalRuntimeVersion(runtimeName));
        } catch (Throwable throwable) {
            return null;
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
