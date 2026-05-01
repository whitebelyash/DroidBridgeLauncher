package ca.dnamobile.javalauncher.feature.unpack;

import androidx.annotation.Nullable;

import ca.dnamobile.javalauncher.R;

public enum Components {
    OTHER_LOGIN("other_login", "authlib-injector", R.string.splash_screen_authlib_injector, false),
    CACIOCAVALLO("caciocavallo", "caciocavallo", R.string.splash_screen_cacio, false),
    CACIOCAVALLO17("caciocavallo17", "caciocavallo 17", R.string.splash_screen_cacio, false),

    // LWJGL components must live under the app-private files directory so libjvm can dlopen them
    // through the classloader namespace without hitting the external-storage restriction.
    LWJGL3("lwjgl3.3.3", "LWJGL 3.3.3", R.string.splash_screen_lwjgl, true),
    LWJGL341("lwjgl3.4.1", "LWJGL 3.4.1", R.string.splash_screen_lwjgl, true),

    // Launcher support components (MioLibPatcher.jar, Forge installer, etc.) are expected from
    // DIR_DATA/components, not from context.filesDir/components.
    COMPONENTS("components", "Launcher Components", R.string.splash_screen_components, false);

    public final String component;
    public final String displayName;
    @Nullable
    public final Integer summary;
    public final boolean privateDirectory;

    Components(String component, String displayName, @Nullable Integer summary, boolean privateDirectory) {
        this.component = component;
        this.displayName = displayName;
        this.summary = summary;
        this.privateDirectory = privateDirectory;
    }
}
