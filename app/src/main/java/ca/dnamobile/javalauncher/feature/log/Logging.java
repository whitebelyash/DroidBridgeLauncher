package ca.dnamobile.javalauncher.feature.log;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Logging {
    private static final String DEFAULT_TAG = "JavaLauncher";

    private Logging() {
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        Log.i(tag, message);
    }

    public static void i(@NonNull String message) {
        Log.i(DEFAULT_TAG, message);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        Log.e(tag, message);
    }

    public static void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        Log.e(tag, message, throwable);
    }

    public static void e(@NonNull String message, @Nullable Throwable throwable) {
        Log.e(DEFAULT_TAG, message, throwable);
    }
}
