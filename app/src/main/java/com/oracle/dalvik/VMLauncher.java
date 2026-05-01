package com.oracle.dalvik;

import androidx.annotation.NonNull;

public final class VMLauncher {
    private VMLauncher() {}

    public static native int launchJVM(@NonNull String[] args);
}
