package ca.dnamobile.javalauncher.renderer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

public final class MobileGluesConfigHelper {
    private static final String CONFIG_DIR_NAME = "MG";
    private static final String CONFIG_FILE_NAME = "config.json";

    private MobileGluesConfigHelper() {
    }

    public static boolean isMobileGluesRenderer(@Nullable RendererInterface renderer) {
        if (renderer == null) return false;
        String combined = (renderer.getUniqueIdentifier() + " "
                + renderer.getRendererName() + " "
                + renderer.getRendererId() + " "
                + renderer.getRendererLibrary()).toLowerCase(Locale.ROOT);
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

    public static boolean hasStorageAccess(@NonNull Context context) {
        File configFile = getConfigFile();
        if (configFile.isFile() && configFile.canRead()) return true;

        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    public static Intent buildStorageAccessIntent(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return intent;
            } catch (Throwable ignored) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return intent;
            }
        }

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @NonNull
    public static String buildSettingsSummary(@NonNull Context context, @Nullable RendererInterface renderer) {
        if (!isMobileGluesRenderer(renderer)) {
            return "No external MobileGlues configuration is used by this renderer.";
        }

        File configFile = getConfigFile();
        if (!hasStorageAccess(context)) {
            return "MobileGlues config path: " + configFile.getAbsolutePath()
                    + "\nJavaLauncher does not have storage access yet, so MobileGlues may fall back to default settings.";
        }

        if (!configFile.isFile()) {
            return "MobileGlues config path: " + configFile.getAbsolutePath()
                    + "\nNo config.json was found yet. Open the MobileGlues app, save its settings, then refresh here.";
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
                    + "\nThe config exists, but JavaLauncher could not parse it: " + throwable.getMessage();
        }
    }

    private static void appendKnown(@NonNull StringBuilder out, @NonNull JSONObject json, @NonNull String key, @NonNull String label) {
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
}
