package ca.dnamobile.javalauncher.feature.unpack;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;

import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.utils.path.PathManager;

/**
 * Unpacks internal Java runtimes from assets.
 *
 * Java 8 packs from Pojav/Zalith store core jars as *.jar.pack in universal.tar.xz.
 * MultiRTUtils.postPrepare() must run unpack200 or rt.jar will never exist.
 */
public class UnpackJreTask extends AbstractUnpackTask {
    private static final String TAG = "UnpackJreTask";

    private final Context context;
    private final Jre jre;

    private AssetManager assetManager;
    private String launcherRuntimeVersion;
    private boolean checkFailed;

    public UnpackJreTask(@NonNull Context context, @NonNull Jre jre) {
        this.context = context;
        this.jre = jre;

        try {
            assetManager = context.getAssets();
            launcherRuntimeVersion = Tools.read(assetManager.open(jre.jrePath + "/version"));
        } catch (Throwable throwable) {
            checkFailed = true;
            Logging.e(TAG, "Failed to read bundled runtime version for " + jre.jreName, throwable);
        }
    }

    public boolean isCheckFailed() {
        return checkFailed;
    }

    @Override
    public boolean isNeedUnpack() {
        if (checkFailed) return false;

        try {
            File runtimeHome = MultiRTUtils.getRuntimeHome(jre.jreName);

            if (runtimeHome.exists()) {
                MultiRTUtils.normalizeRuntimeDirIfNeeded(runtimeHome);
                try {
                    MultiRTUtils.postPrepare(jre.jreName);
                } catch (Throwable throwable) {
                    Logging.e(TAG, "postPrepare failed for existing runtime " + jre.jreName, throwable);
                }
            }

            if (runtimeHome.exists() && !hasRequiredRuntimeFiles(runtimeHome, jre.jreName)) {
                Logging.i(TAG, jre.jreName + " is installed but incomplete. Deleting and reinstalling: "
                        + runtimeHome.getAbsolutePath());
                logRuntimeTree(runtimeHome);
                PathManager.deleteQuietly(runtimeHome);
                return true;
            }

            String installedRuntimeVersion = MultiRTUtils.readInternalRuntimeVersion(jre.jreName);
            if (!launcherRuntimeVersion.equals(installedRuntimeVersion)) {
                if (runtimeHome.exists()) {
                    Logging.i(TAG, jre.jreName + " version mismatch. Deleting old runtime: "
                            + runtimeHome.getAbsolutePath());
                    PathManager.deleteQuietly(runtimeHome);
                }
                return true;
            }

            return false;
        } catch (Throwable throwable) {
            Logging.e("CheckInternalRuntime", Tools.printToString(throwable));

            try {
                File runtimeHome = MultiRTUtils.getRuntimeHome(jre.jreName);
                if (!hasRequiredRuntimeFiles(runtimeHome, jre.jreName)) {
                    if (runtimeHome.exists()) {
                        logRuntimeTree(runtimeHome);
                        PathManager.deleteQuietly(runtimeHome);
                    }
                    return true;
                }
            } catch (Throwable ignored) {
            }

            return false;
        }
    }

    @Override
    public void run() {
        if (listener != null) listener.onTaskStart();

        try {
            try (InputStream universal = assetManager.open(jre.jrePath + "/universal.tar.xz");
                 InputStream bin = openBinPack()) {
                MultiRTUtils.installRuntimeNamedBinpack(
                        universal,
                        bin,
                        jre.jreName,
                        launcherRuntimeVersion
                );
            }

            MultiRTUtils.postPrepare(jre.jreName);

            File runtimeHome = MultiRTUtils.getRuntimeHome(jre.jreName);
            MultiRTUtils.normalizeRuntimeDirIfNeeded(runtimeHome);

            if (!hasRequiredRuntimeFiles(runtimeHome, jre.jreName)) {
                logRuntimeTree(runtimeHome);
                throw new IllegalStateException(
                        jre.jreName + " unpack completed but required files are still missing. runtimeHome="
                                + runtimeHome.getAbsolutePath()
                );
            }
        } catch (Throwable throwable) {
            Logging.e("UnpackJREAuto", "Internal JRE unpack failed for " + jre.jreName, throwable);
        }

        if (listener != null) listener.onTaskEnd();
    }

    @NonNull
    private InputStream openBinPack() throws IOException {
        String primary = Architecture.archAsString(Tools.DEVICE_ARCHITECTURE);

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(primary);

        if ("arm64-v8a".equals(primary)) {
            candidates.add("arm64");
            candidates.add("aarch64");
        } else if ("arm64".equals(primary)) {
            candidates.add("arm64-v8a");
            candidates.add("aarch64");
        } else if ("aarch64".equals(primary)) {
            candidates.add("arm64");
            candidates.add("arm64-v8a");
        }

        if ("armeabi-v7a".equals(primary)) {
            candidates.add("arm");
        } else if ("arm".equals(primary)) {
            candidates.add("armeabi-v7a");
        }

        IOException last = null;
        for (String arch : candidates) {
            String path = jre.jrePath + "/bin-" + arch + ".tar.xz";
            try {
                Logging.i(TAG, "Trying runtime bin pack: " + path);
                return assetManager.open(path);
            } catch (IOException e) {
                last = e;
            }
        }

        throw last != null ? last : new IOException("Unable to open runtime bin pack for " + primary);
    }

    private static boolean hasRequiredRuntimeFiles(@NonNull File runtimeHome, @NonNull String runtimeName) {
        if (!runtimeHome.isDirectory()) return false;

        boolean isJava8 = runtimeName.contains("8");

        if (isJava8) {
            File javaHome = findJava8Home(runtimeHome);
            return javaHome != null && findLibJvm(javaHome) != null;
        }

        File javaHome = findJava9PlusHome(runtimeHome);
        return javaHome != null && findLibJvm(javaHome) != null;
    }

    @Nullable
    public static File findJava8Home(@NonNull File runtimeHome) {
        File direct = new File(runtimeHome, "lib/rt.jar");
        if (direct.isFile()) return runtimeHome;

        File nestedJre = new File(runtimeHome, "jre/lib/rt.jar");
        if (nestedJre.isFile()) return new File(runtimeHome, "jre");

        File rtJar = findFileNamed(runtimeHome, "rt.jar", 6);
        if (rtJar == null) return null;

        File libDir = rtJar.getParentFile();
        if (libDir == null || !"lib".equals(libDir.getName())) return null;

        return libDir.getParentFile();
    }

    @Nullable
    private static File findJava9PlusHome(@NonNull File runtimeHome) {
        File modules = new File(runtimeHome, "lib/modules");
        if (modules.isFile()) return runtimeHome;

        File found = findFileNamed(runtimeHome, "modules", 6);
        if (found == null) return null;

        File libDir = found.getParentFile();
        if (libDir == null || !"lib".equals(libDir.getName())) return null;

        return libDir.getParentFile();
    }

    @Nullable
    private static File findFileNamed(@NonNull File root, @NonNull String name, int depthLeft) {
        if (depthLeft < 0 || !root.isDirectory()) return null;

        File[] children = root.listFiles();
        if (children == null) return null;

        for (File child : children) {
            if (child.isFile() && child.getName().equals(name)) {
                return child;
            }
            if (child.isDirectory()) {
                File found = findFileNamed(child, name, depthLeft - 1);
                if (found != null) return found;
            }
        }

        return null;
    }

    private static File findLibJvm(@NonNull File javaHome) {
        File[] candidates = new File[]{
                new File(javaHome, "lib/server/libjvm.so"),
                new File(javaHome, "lib/client/libjvm.so"),
                new File(javaHome, "lib/aarch64/server/libjvm.so"),
                new File(javaHome, "lib/aarch64/client/libjvm.so"),
                new File(javaHome, "lib/arm/server/libjvm.so"),
                new File(javaHome, "lib/arm/client/libjvm.so"),
                new File(javaHome, "lib/arm64/server/libjvm.so"),
                new File(javaHome, "lib/arm64/client/libjvm.so"),
                new File(javaHome, "lib/arm64-v8a/server/libjvm.so"),
                new File(javaHome, "lib/arm64-v8a/client/libjvm.so"),
                new File(javaHome, "lib/x86_64/server/libjvm.so"),
                new File(javaHome, "lib/x86_64/client/libjvm.so"),
                new File(javaHome, "lib/i386/server/libjvm.so"),
                new File(javaHome, "lib/i386/client/libjvm.so")
        };

        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }

        return null;
    }

    private static void logRuntimeTree(@NonNull File runtimeHome) {
        Logging.i(TAG, "Runtime tree for " + runtimeHome.getAbsolutePath() + ":");
        logRuntimeTree(runtimeHome, 0, 3);
    }

    private static void logRuntimeTree(@NonNull File file, int depth, int maxDepth) {
        if (depth > maxDepth || !file.exists()) return;

        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < depth; i++) prefix.append("  ");

        Logging.i(TAG, prefix + file.getName() + (file.isDirectory() ? "/" : ""));

        if (!file.isDirectory()) return;

        File[] children = file.listFiles();
        if (children == null) return;

        int count = 0;
        for (File child : children) {
            if (count++ > 80) {
                Logging.i(TAG, prefix + "  ...");
                break;
            }
            logRuntimeTree(child, depth + 1, maxDepth);
        }
    }
}
