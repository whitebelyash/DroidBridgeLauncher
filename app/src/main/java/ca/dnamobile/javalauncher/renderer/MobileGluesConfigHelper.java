package ca.dnamobile.javalauncher.renderer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

/**
 * MobileGlues optional configuration helper.
 *
 * Important for Google Play builds:
 * - JavaLauncher must never request MANAGE_EXTERNAL_STORAGE / All files access.
 * - MobileGlues does not require JavaLauncher to read /sdcard/MG/config.json to launch.
 * - If the config is unavailable, MobileGlues falls back to its defaults or the user can
 *   open the MobileGlues plugin app to edit/save plugin-side settings.
 */
public final class MobileGluesConfigHelper {
    private static final String CONFIG_DIR_NAME = "MG";
    private static final String CONFIG_FILE_NAME = "config.json";

    private static final String[] MOBILE_GLUES_PACKAGES = new String[]{
            "com.fcl.plugin.mobileglues",
            "com.fcl.plugin.renderer.mobileglues",
            "com.mio.plugin.renderer.mobileglues"
    };

    private MobileGluesConfigHelper() {
    }

    public static boolean isMobileGluesRenderer(@Nullable RendererInterface renderer) {
        if (renderer == null) return false;
        String combined = (safe(renderer.getUniqueIdentifier()) + " "
                + safe(renderer.getRendererName()) + " "
                + safe(renderer.getRendererId()) + " "
                + safe(renderer.getRendererLibrary()))
                .toLowerCase(Locale.ROOT);
        return combined.contains("mobileglues") || combined.contains("mobile glues");
    }

    @NonNull
    public static File getConfigDirectory() {
        return new File(Environment.getExternalStorageDirectory(), CONFIG_DIR_NAME);
    }

    @NonNull
    public static File getConfigFile() {
        return new File(getConfigDirectory(), CONFIG_FILE_NAME);
    }

    /**
     * Compatibility method kept so older call sites keep compiling.
     *
     * This intentionally returns true. MobileGlues can launch without JavaLauncher having
     * broad storage permission, and Google Play builds should never show an All files access
     * prompt for this renderer.
     */
    public static boolean hasStorageAccess(@NonNull Context context) {
        return true;
    }

    /**
     * New explicit name for pre-launch checks. Always false for MobileGlues.
     */
    public static boolean shouldShowStorageAccessPrompt(@NonNull Context context,
                                                        @Nullable RendererInterface renderer) {
        return false;
    }

    public static boolean canReadConfigFile() {
        File configFile = getConfigFile();
        return configFile.isFile() && configFile.canRead();
    }

    /**
     * Compatibility method kept so older call sites keep compiling.
     *
     * This no longer returns ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION or
     * ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION. If a caller still invokes this, it opens
     * the MobileGlues plugin app instead of Android's All files access screen.
     */
    @NonNull
    public static Intent buildStorageAccessIntent(@NonNull Context context) {
        Intent pluginIntent = buildOpenPluginIntent(context);
        if (pluginIntent != null) return pluginIntent;

        Intent fallback = new Intent(Intent.ACTION_MAIN);
        fallback.addCategory(Intent.CATEGORY_LAUNCHER);
        fallback.setPackage(MOBILE_GLUES_PACKAGES[0]);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return fallback;
    }

    @Nullable
    public static Intent buildOpenPluginIntent(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        for (String packageName : MOBILE_GLUES_PACKAGES) {
            try {
                Intent intent = packageManager.getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    return intent;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @NonNull
    public static String buildSettingsSummary(@NonNull Context context, @Nullable RendererInterface renderer) {
        if (!isMobileGluesRenderer(renderer)) {
            return "No external MobileGlues configuration is used by this renderer.";
        }

        File configFile = getConfigFile();
        if (!canReadConfigFile()) {
            return "MobileGlues config path: " + configFile.getAbsolutePath()
                    + "\nJavaLauncher does not require All files access for MobileGlues."
                    + "\nIf this config is unavailable, MobileGlues can still launch with defaults."
                    + "\nOpen the MobileGlues plugin app to change MobileGlues settings.";
        }

        try {
            JSONObject json = new JSONObject(readFile(configFile));
            StringBuilder out = new StringBuilder();
            out.append("MobileGlues config path: ").append(configFile.getAbsolutePath()).append('\n');
            appendKnown(out, json, "enableANGLE", "ANGLE");
            appendKnown(out, json, "enableNoError", "Ignore GL errors");
            appendKnown(out, json, "enableExtComputeShader", "ARB_compute_shader");
            appendKnown(out, json, "enableExtTimerQuery", "timer_query");
            appendKnown(out, json, "enableExtDirectStateAccess", "direct_state_access");
            appendKnown(out, json, "maxGlslCacheSize", "GLSL cache MB");
            appendKnown(out, json, "multidrawMode", "MultiDraw mode");
            appendKnown(out, json, "angleDepthClearFixMode", "ANGLE depth clear fix");
            appendKnown(out, json, "bufferCoherentAsFlush", "Coherent buffer as flush");
            appendKnown(out, json, "customGLVersion", "Custom GL version");
            appendKnown(out, json, "fsr1Setting", "FSR1");
            appendKnown(out, json, "hideMGEnvLevel", "Hide environment level");

            boolean hasUnknown = false;
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (isKnownKey(key)) continue;
                if (!hasUnknown) {
                    out.append("Other values:\n");
                    hasUnknown = true;
                }
                out.append("• ").append(key).append(" = ").append(json.opt(key)).append('\n');
            }
            return out.toString().trim();
        } catch (Throwable throwable) {
            return "MobileGlues config path: " + configFile.getAbsolutePath()
                    + "\nThe config exists, but JavaLauncher could not parse it: " + throwable.getMessage()
                    + "\nMobileGlues can still launch with defaults.";
        }
    }

    private static void appendKnown(@NonNull StringBuilder out,
                                    @NonNull JSONObject json,
                                    @NonNull String key,
                                    @NonNull String label) {
        if (!json.has(key)) return;
        out.append("• ").append(label).append(" = ").append(json.opt(key)).append('\n');
    }

    private static boolean isKnownKey(@NonNull String key) {
        return "enableANGLE".equals(key)
                || "enableNoError".equals(key)
                || "enableExtGL43".equals(key)
                || "enableExtComputeShader".equals(key)
                || "enableExtTimerQuery".equals(key)
                || "enableExtDirectStateAccess".equals(key)
                || "maxGlslCacheSize".equals(key)
                || "multidrawMode".equals(key)
                || "angleDepthClearFixMode".equals(key)
                || "bufferCoherentAsFlush".equals(key)
                || "customGLVersion".equals(key)
                || "fsr1Setting".equals(key)
                || "hideMGEnvLevel".equals(key);
    }

    @NonNull
    private static String readFile(@NonNull File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = input.read(buffer, offset, buffer.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        }
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}
