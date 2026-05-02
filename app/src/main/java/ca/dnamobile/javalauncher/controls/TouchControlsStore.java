package ca.dnamobile.javalauncher.controls;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ca.dnamobile.javalauncher.feature.log.Logging;

public final class TouchControlsStore {
    private static final String TAG = "TouchControlsStore";

    /**
     * Runtime copy stored under:
     *   /data/data/<package>/files/touch_controls/default_touch.json
     *
     * This is the file users edit at runtime. The bundled asset is only copied here
     * when this runtime file does not exist yet or is empty.
     */
    private static final String DEFAULT_FILE = "default_touch.json";

    /**
     * Release default bundled in the APK.
     *
     * Put your real default layout here:
     *   app/src/main/assets/touch_controls/default_touch.json
     *
     * This path is intentionally first because this is the name you are using in the
     * project assets folder. The extra candidates only keep older local test names from
     * breaking if you still have them in a branch.
     */
    private static final String DEFAULT_ASSET = "touch_controls/default_touch.json";
    private static final String[] DEFAULT_ASSET_CANDIDATES = new String[]{
            DEFAULT_ASSET,
            "touch_controls/default.json",
            "touch_controls/default_touch_controls.json",
            "default_touch.json",
            "default.json",
            "default_touch_controls.json"
    };

    private TouchControlsStore() {
    }

    @NonNull
    public static File getControlsDir(@NonNull Context context) {
        File dir = new File(context.getFilesDir(), "touch_controls");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    @NonNull
    public static File getDefaultLayoutFile(@NonNull Context context) {
        return new File(getControlsDir(context), DEFAULT_FILE);
    }

    @NonNull
    public static File ensureDefaultLayout(@NonNull Context context) {
        File target = getDefaultLayoutFile(context);

        // Do not overwrite this file once it exists. After first launch it is the user's
        // editable copy. If they edit the default layout, their changes must win.
        if (!target.isFile() || target.length() == 0) {
            try {
                TouchControlsLayoutData bundled = loadBundledDefaultLayout(context);
                saveLayout(target, bundled);
                Logging.i(TAG, "Created default touch controls from bundled asset: " + DEFAULT_ASSET);
            } catch (Throwable assetThrowable) {
                Logging.e(TAG, "Bundled default_touch.json missing or invalid. Falling back to emergency in-code layout.", assetThrowable);
                try {
                    saveLayout(target, TouchControlsLayoutData.defaultLayout());
                } catch (Throwable fallbackThrowable) {
                    Logging.e(TAG, "Unable to create fallback default touch controls", fallbackThrowable);
                }
            }
        }

        return target;
    }

    @NonNull
    public static File getSelectedLayoutFile(@NonNull Context context) {
        File defaultFile = ensureDefaultLayout(context);
        String selected = ControlsPreferences.getSelectedLayoutPath(context);
        if (selected != null) {
            File selectedFile = new File(selected);
            if (selectedFile.isFile()) return selectedFile;
        }

        ControlsPreferences.setSelectedLayoutPath(context, defaultFile.getAbsolutePath());
        return defaultFile;
    }

    @NonNull
    public static TouchControlsLayoutData loadSelectedLayout(@NonNull Context context) {
        return loadLayout(getSelectedLayoutFile(context));
    }

    @NonNull
    public static TouchControlsLayoutData loadLayout(@NonNull File file) {
        try {
            String text = readText(file);
            return TouchControlsLayoutData.fromJson(new JSONObject(text));
        } catch (Throwable throwable) {
            Logging.e(TAG, "Unable to read touch layout " + file.getAbsolutePath(), throwable);
            return TouchControlsLayoutData.defaultLayout();
        }
    }

    public static void saveLayout(@NonNull File file, @NonNull TouchControlsLayoutData data) throws Exception {
        writeText(file, data.toJson().toString(2));
    }

    @NonNull
    public static File saveImportedLayout(@NonNull Context context, @NonNull Uri uri) throws Exception {
        String source = readUriText(context, uri);
        TouchControlsLayoutData data = TouchControlsLayoutData.fromJson(new JSONObject(source));
        String cleanName = sanitizeFileName(data.name);
        if (cleanName.isEmpty()) cleanName = "imported_controls";
        File target = uniqueFile(getControlsDir(context), cleanName, ".json");
        saveLayout(target, data);
        ControlsPreferences.setSelectedLayoutPath(context, target.getAbsolutePath());
        return target;
    }

    @NonNull
    public static List<File> listLayouts(@NonNull Context context) {
        ensureDefaultLayout(context);
        ArrayList<File> layouts = new ArrayList<>();
        File[] files = getControlsDir(context).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".json")) {
                    layouts.add(file);
                }
            }
        }
        return layouts;
    }

    @NonNull
    public static String readText(@NonNull File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return readStreamText(input);
        }
    }

    @NonNull
    private static TouchControlsLayoutData loadBundledDefaultLayout(@NonNull Context context) throws Exception {
        Throwable lastError = null;

        for (String assetPath : DEFAULT_ASSET_CANDIDATES) {
            try (InputStream input = context.getAssets().open(assetPath)) {
                String text = readStreamText(input);

                // Important: parse through the same importer as manual Import.
                // This keeps JavaLauncher JSON and Zalith/Mojo/Amethyst-style
                // mControlDataList / mJoystickDataList layouts working the same way.
                TouchControlsLayoutData data = TouchControlsLayoutData.fromJson(new JSONObject(text));
                if (data.name == null || data.name.trim().isEmpty()) {
                    data.name = "Default Touch Controls";
                }
                Logging.i(TAG, "Loaded bundled default touch controls from asset: " + assetPath);
                return data;
            } catch (Throwable throwable) {
                lastError = throwable;
            }
        }

        throw new IllegalStateException("No bundled default touch layout found. Expected " + DEFAULT_ASSET, lastError);
    }

    @NonNull
    private static String readUriText(@NonNull Context context, @NonNull Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("Unable to open selected controls file.");
            return readStreamText(input);
        }
    }

    @NonNull
    private static String readStreamText(@NonNull InputStream input) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        StringBuilder builder = new StringBuilder();
        int read;
        while ((read = input.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private static void writeText(@NonNull File file, @NonNull String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @NonNull
    private static File uniqueFile(@NonNull File dir, @NonNull String base, @NonNull String suffix) {
        File file = new File(dir, base + suffix);
        int index = 2;
        while (file.exists()) {
            file = new File(dir, base + "_" + index + suffix);
            index++;
        }
        return file;
    }

    @NonNull
    private static String sanitizeFileName(@NonNull String name) {
        return name.trim().replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_");
    }
}
