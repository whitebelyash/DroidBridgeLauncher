/*
package ca.dnamobile.javalauncher.logs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ca.dnamobile.javalauncher.R;
import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.utils.path.PathManager;
import net.kdt.pojavlaunch.Logger;

public final class LauncherLogManager {
    private static final String PREFS = "launcher_logs";
    private static final String KEY_KEEP_LOG_HISTORY = "keep_log_history";

    private static boolean nativeLogStarted = false;
    private static File activeLatestLogFile = null;

    private LauncherLogManager() {
    }

    public static boolean isKeepLogHistoryEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_KEEP_LOG_HISTORY, true);
    }

    public static void setKeepLogHistoryEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEEP_LOG_HISTORY, enabled).apply();
    }

    @NonNull
    public static File getLatestLogFile(@NonNull Context context) {
        PathManager.initContextConstants(context);
        return new File(PathManager.DIR_MINECRAFT_HOME, "latestlog.txt");
    }

    @NonNull
    public static File getLogHistoryDirectory(@NonNull Context context) {
        PathManager.initContextConstants(context);
        File dir = new File(PathManager.DIR_MINECRAFT_HOME, "launcher_log");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static synchronized void beginLatestLog(@NonNull Context context, @NonNull String versionId) {
        File latest = getLatestLogFile(context);
        File parent = latest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        boolean firstStartForThisProcess = !nativeLogStarted;
        boolean switchingFile = activeLatestLogFile == null || !activeLatestLogFile.equals(latest);

        if (firstStartForThisProcess || switchingFile || latest.length() == 0) {
            try (FileOutputStream out = new FileOutputStream(latest, false)) {
                String header = "JavaLauncher latestlog.txt\n"
                        + "Version: " + versionId + "\n"
                        + "Started: " + new Date() + "\n"
                        + "----------------------------------------\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable throwable) {
                Logging.e("LauncherLogManager", "Failed to initialize latestlog.txt", throwable);
            }
        }

        if (!nativeLogStarted) {
            Logger.beginLog(latest);
            nativeLogStarted = true;
            activeLatestLogFile = latest;
        } else {
            append("----------------------------------------");
            append("New launch started for " + versionId + " at " + new Date());
        }
    }

    public static void append(@NonNull String text) {
        try {
            Logger.appendToLog(text.endsWith("\n") ? text : text + "\n");
        } catch (Throwable throwable) {
            Logging.e("LauncherLogManager", "appendToLog failed", throwable);
            appendFallback(text);
        }
    }

    private static void appendFallback(@NonNull String text) {
        File latest = activeLatestLogFile;
        if (latest == null) return;
        try (FileOutputStream out = new FileOutputStream(latest, true)) {
            out.write((text.endsWith("\n") ? text : text + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public static void preserveLatestLogIfEnabled(@NonNull Context context, @NonNull String versionId) {
        if (!isKeepLogHistoryEnabled(context)) return;

        File latest = getLatestLogFile(context);
        if (!latest.isFile() || latest.length() <= 0) return;

        String safeVersion = versionId.replaceAll("[^A-Za-z0-9._-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File target = new File(getLogHistoryDirectory(context), "latestlog-" + safeVersion + "-" + stamp + ".txt");

        try {
            copyFile(latest, target);
            Logging.i("LauncherLogManager", "Saved launch log history: " + target.getAbsolutePath());
        } catch (Throwable throwable) {
            Logging.e("LauncherLogManager", "Failed to save launch log history", throwable);
        }
    }

    public static void shareLatestLog(@NonNull Activity activity) {
        File latest = getLatestLogFile(activity);
        if (!latest.isFile() || latest.length() <= 0) {
            Toast.makeText(activity, R.string.log_latest_missing, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    latest
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.button_share_latest_log)));
        } catch (Throwable throwable) {
            Logging.e("LauncherLogManager", "Failed to share latestlog.txt", throwable);
            Toast.makeText(activity, throwable.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void copyFile(@NonNull File source, @NonNull File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
*/
package ca.dnamobile.javalauncher.logs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import ca.dnamobile.javalauncher.R;
import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.utils.path.PathManager;
import net.kdt.pojavlaunch.Logger;

public final class LauncherLogManager {
    private static final String PREFS = "launcher_logs";
    private static final String KEY_KEEP_LOG_HISTORY = "keep_log_history";
    private static final String KEY_LAST_LATEST_LOG_PATH = "last_latest_log_path";

    private static boolean nativeLogStarted = false;
    @Nullable
    private static File activeLatestLogFile = null;

    private LauncherLogManager() {
    }

    public static boolean isKeepLogHistoryEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_KEEP_LOG_HISTORY, true);
    }

    public static void setKeepLogHistoryEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEEP_LOG_HISTORY, enabled).apply();
    }

    /**
     * Returns latestlog.txt for the currently selected storage location.
     *
     * Note: this intentionally reinitializes PathManager from the selected storage
     * preference. Launch-time code should use beginLatestLog(), which respects the
     * already-active instance storage root set by LaunchGame.
     */
    @NonNull
    public static File getLatestLogFile(@NonNull Context context) {
        PathManager.initContextConstants(context);
        return new File(PathManager.DIR_MINECRAFT_HOME, "latestlog.txt");
    }

    /**
     * Returns the best latestlog.txt candidate without blindly assuming the current
     * selected storage is where the most recent game was launched.
     */
    @NonNull
    public static File resolveLatestLogFile(@NonNull Context context) {
        File currentNative = Logger.getCurrentLogFile();
        if (isUsableLog(currentNative)) return currentNative;

        if (isUsableLog(activeLatestLogFile)) return activeLatestLogFile;

        String persistedPath = prefs(context).getString(KEY_LAST_LATEST_LOG_PATH, "");
        if (persistedPath != null && !persistedPath.trim().isEmpty()) {
            File persisted = new File(persistedPath.trim());
            if (isUsableLog(persisted)) return persisted;
        }

        File newest = null;
        for (File candidate : buildLatestLogCandidates(context)) {
            if (!isUsableLog(candidate)) continue;
            if (newest == null || candidate.lastModified() > newest.lastModified()) {
                newest = candidate;
            }
        }

        if (newest != null) return newest;

        // Last fallback: selected storage path. This may not exist yet.
        return getLatestLogFile(context);
    }

    @NonNull
    public static File getLogHistoryDirectory(@NonNull Context context) {
        PathManager.initContextConstants(context);
        File dir = new File(PathManager.DIR_MINECRAFT_HOME, "launcher_log");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    @NonNull
    private static File getLogHistoryDirectoryForLatest(@NonNull Context context, @NonNull File latest) {
        File minecraftHome = latest.getParentFile();
        File dir = minecraftHome != null ? new File(minecraftHome, "launcher_log") : getLogHistoryDirectory(context);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static synchronized void beginLatestLog(@NonNull Context context, @NonNull String versionId) {
        File latest = getLatestLogFileForActivePath(context);
        rememberLatestLogPath(context, latest);

        File parent = latest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        boolean firstStartForThisProcess = !nativeLogStarted;
        boolean switchingFile = activeLatestLogFile == null || !activeLatestLogFile.equals(latest);

        if (firstStartForThisProcess || switchingFile || latest.length() == 0) {
            try (FileOutputStream out = new FileOutputStream(latest, false)) {
                String header = "JavaLauncher latestlog.txt\n"
                        + "Version: " + versionId + "\n"
                        + "Started: " + new Date() + "\n"
                        + "Storage: " + (parent != null ? parent.getAbsolutePath() : latest.getAbsolutePath()) + "\n"
                        + "----------------------------------------\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable throwable) {
                Logging.e("LauncherLogManager", "Failed to initialize latestlog.txt", throwable);
            }
        }

        if (!nativeLogStarted || switchingFile) {
            Logger.beginLog(latest);
            nativeLogStarted = true;
            activeLatestLogFile = latest;
        } else {
            append("----------------------------------------");
            append("New launch started for " + versionId + " at " + new Date());
        }
    }

    public static void append(@NonNull String text) {
        try {
            Logger.appendToLog(text.endsWith("\n") ? text : text + "\n");
        } catch (Throwable throwable) {
            Logging.e("LauncherLogManager", "appendToLog failed", throwable);
            appendFallback(text);
        }
    }

    private static void appendFallback(@NonNull String text) {
        File latest = activeLatestLogFile;
        if (latest == null) return;
        try (FileOutputStream out = new FileOutputStream(latest, true)) {
            out.write((text.endsWith("\n") ? text : text + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public static void preserveLatestLogIfEnabled(@NonNull Context context, @NonNull String versionId) {
        if (!isKeepLogHistoryEnabled(context)) return;

        File latest = resolveLatestLogFile(context);
        if (!latest.isFile() || latest.length() <= 0) return;

        String safeVersion = versionId.replaceAll("[^A-Za-z0-9._-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File target = new File(getLogHistoryDirectoryForLatest(context, latest), "latestlog-" + safeVersion + "-" + stamp + ".txt");

        try {
            copyFile(latest, target);
            Logging.i("LauncherLogManager", "Saved launch log history: " + target.getAbsolutePath());
        } catch (Throwable throwable) {
            Logging.e("LauncherLogManager", "Failed to save launch log history", throwable);
        }
    }

    public static void shareLatestLog(@NonNull Activity activity) {
        File latest = resolveLatestLogFile(activity);
        if (!latest.isFile() || latest.length() <= 0) {
            Toast.makeText(activity, R.string.log_latest_missing, Toast.LENGTH_LONG).show();
            return;
        }

        rememberLatestLogPath(activity, latest);

        // Copy to app cache first. This avoids FileProvider root problems when the
        // original log lives on a custom/scoped storage path.
        File shareFile = new File(activity.getCacheDir(), "shared_logs/latestlog.txt");
        try {
            copyFile(latest, shareFile);
            shareFile.setReadable(true, false);
            shareTextFile(activity, shareFile);
            return;
        } catch (Throwable throwable) {
            Logging.e("LauncherLogManager", "Failed to share cached latestlog.txt", throwable);
        }

        // Fallback: try the original file directly.
        try {
            shareTextFile(activity, latest);
        } catch (Throwable throwable) {
            Logging.e("LauncherLogManager", "Failed to share latestlog.txt", throwable);
            Toast.makeText(activity, throwable.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void shareTextFile(@NonNull Activity activity, @NonNull File file) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                file
        );

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.button_share_latest_log)));
    }

    @NonNull
    private static File getLatestLogFileForActivePath(@NonNull Context context) {
        if (PathManager.DIR_MINECRAFT_HOME == null || PathManager.DIR_MINECRAFT_HOME.trim().isEmpty()) {
            PathManager.initContextConstants(context);
        }
        return new File(PathManager.DIR_MINECRAFT_HOME, "latestlog.txt");
    }

    @NonNull
    private static ArrayList<File> buildLatestLogCandidates(@NonNull Context context) {
        ArrayList<File> candidates = new ArrayList<>();

        addCandidate(candidates, activeLatestLogFile);

        String persistedPath = prefs(context).getString(KEY_LAST_LATEST_LOG_PATH, "");
        if (persistedPath != null && !persistedPath.trim().isEmpty()) {
            addCandidate(candidates, new File(persistedPath.trim()));
        }

        try {
            PathManager.initContextConstants(context);
            addCandidate(candidates, new File(PathManager.DIR_MINECRAFT_HOME, "latestlog.txt"));
        } catch (Throwable ignored) {
        }

        try {
            File defaultHome = PathManager.getDefaultLauncherHome(context);
            addCandidate(candidates, new File(new File(defaultHome, ".minecraft"), "latestlog.txt"));
        } catch (Throwable ignored) {
        }

        return candidates;
    }

    private static void addCandidate(@NonNull ArrayList<File> candidates, @Nullable File file) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        for (File existing : candidates) {
            if (existing.getAbsolutePath().equals(path)) return;
        }
        candidates.add(file);
    }

    private static boolean isUsableLog(@Nullable File file) {
        return file != null && file.isFile() && file.length() > 0L;
    }

    private static void rememberLatestLogPath(@NonNull Context context, @NonNull File latest) {
        prefs(context).edit().putString(KEY_LAST_LATEST_LOG_PATH, latest.getAbsolutePath()).apply();
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void copyFile(@NonNull File source, @NonNull File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
