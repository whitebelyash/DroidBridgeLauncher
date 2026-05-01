package net.kdt.pojavlaunch.utils;

import android.content.Context;

import ca.dnamobile.javalauncher.feature.log.Logging;

public final class JREUtils {
    private static final String TAG = "JREUtils";

    private JREUtils() {
    }

    public static native void setupBridgeWindow(Object surface);
    public static native void releaseBridgeWindow();
    public static native void setupExitMethod(Context context);
    public static native void initializeGameExitHook();
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native int chdir(String path);
    public static native boolean dlopen(String name);
    public static native int[] renderAWTScreenFrame();

    static {
        loadOptional("pojavexec");
        loadOptional("pojavexec_awt");
        loadOptional("exithook");
    }

    private static void loadOptional(String name) {
        try {
            System.loadLibrary(name);
            Logging.i(TAG, "Loaded native library: " + name);
        } catch (Throwable throwable) {
            Logging.e(TAG, "Could not load native library: " + name, throwable);
        }
    }
}
