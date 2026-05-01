package net.kdt.pojavlaunch;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;

import ca.dnamobile.javalauncher.feature.log.Logging;

public final class Logger {
    private static final String TAG = "PojavLogger";
    private static volatile File currentLogFile;
    private static final CopyOnWriteArrayList<eventLogListener> LOG_LISTENERS = new CopyOnWriteArrayList<>();
    private static final eventLogListener DISPATCHER = line -> {
        for (eventLogListener listener : LOG_LISTENERS) {
            try {
                listener.onEventLogged(line);
            } catch (Throwable throwable) {
                Log.w(TAG, "Log listener failed", throwable);
            }
        }
    };

    static {
        try {
            System.loadLibrary("pojavexec");
        } catch (Throwable ignored) {
        }
    }

    private Logger() {
    }

    public interface eventLogListener {
        void onEventLogged(String line);
    }

    public static void beginLog(@NonNull File logFile) {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        currentLogFile = logFile;

        try {
            begin(logFile.getAbsolutePath());
            Log.i(TAG, "Native log redirection started at " + logFile.getAbsolutePath());
        } catch (Throwable throwable) {
            Log.e(TAG, "Native log redirection failed", throwable);
            Logging.e(TAG, "Native log redirection failed", throwable);
        }
    }

    @Nullable
    public static File getCurrentLogFile() {
        return currentLogFile;
    }

    public static void addLogListener(@Nullable eventLogListener listener) {
        if (listener == null) return;
        if (!LOG_LISTENERS.contains(listener)) {
            LOG_LISTENERS.add(listener);
        }
        installDispatcherIfNeeded();
    }

    public static void removeLogListener(@Nullable eventLogListener listener) {
        if (listener == null) return;
        LOG_LISTENERS.remove(listener);
        if (LOG_LISTENERS.isEmpty()) {
            try {
                setLogListener(null);
            } catch (Throwable throwable) {
                Log.w(TAG, "Unable to clear native log listener", throwable);
            }
        }
    }

    public static void clearLogListeners() {
        LOG_LISTENERS.clear();
        try {
            setLogListener(null);
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to clear native log listeners", throwable);
        }
    }

    private static void installDispatcherIfNeeded() {
        try {
            setLogListener(DISPATCHER);
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to install native log dispatcher", throwable);
        }
    }

    public static native void begin(String logPath);

    public static native void appendToLog(String text);

    public static native void setLogListener(eventLogListener listener);
}
