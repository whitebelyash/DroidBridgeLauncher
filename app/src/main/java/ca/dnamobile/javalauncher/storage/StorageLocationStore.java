package ca.dnamobile.javalauncher.storage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.dnamobile.javalauncher.R;
import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.utils.path.PathManager;

/**
 * Stores launcher storage roots selected from the Storage Locations dialog.
 *
 * Java/Minecraft still need normal java.io.File paths, so selected tree URIs are
 * resolved to filesystem-backed launcher homes when possible. If Android only
 * gives us a content URI that cannot be mapped to a writable File path, the row
 * stays visible in the dialog but PathManager falls back to default storage.
 */
public final class StorageLocationStore {
    public static final String DEFAULT_LOCATION_ID = "default";

    private static final String PREFS_NAME = "storage_locations";
    private static final String KEY_LOCATIONS = "locations_json";
    private static final String KEY_SELECTED_LOCATION_ID = "selected_location_id";

    private StorageLocationStore() {
    }

    @NonNull
    public static List<StorageLocation> getLocations(@NonNull Context context) {
        Context appContext = context.getApplicationContext();

        ArrayList<StorageLocation> locations = new ArrayList<>();
        File defaultHome = PathManager.getDefaultLauncherHome(appContext);
        File defaultMinecraftHome = new File(defaultHome, ".minecraft");
        locations.add(new StorageLocation(
                DEFAULT_LOCATION_ID,
                appContext.getString(R.string.storage_default_name),
                defaultMinecraftHome.getAbsolutePath(),
                null,
                defaultHome.getAbsolutePath(),
                defaultMinecraftHome.getAbsolutePath(),
                true,
                true
        ));

        JSONArray array = readLocationArray(appContext);
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject object = array.getJSONObject(i);
                String id = object.optString("id", "");
                String name = object.optString("name", "");
                String uri = object.optString("uri", "");
                if (id.isEmpty() || uri.isEmpty()) continue;

                Uri treeUri = Uri.parse(uri);
                File launcherHome = resolveTreeUriToLauncherHome(appContext, treeUri);
                File minecraftHome = launcherHome != null ? new File(launcherHome, ".minecraft") : null;
                boolean usable = launcherHome != null && isUsableLauncherHome(launcherHome);

                String summary;
                if (usable && minecraftHome != null) {
                    summary = minecraftHome.getAbsolutePath();
                } else if (launcherHome != null && minecraftHome != null) {
                    summary = minecraftHome.getAbsolutePath()
                            + "\nThis folder is not writable through normal File access yet.";
                } else {
                    summary = uri + "\nThis provider cannot be used for Minecraft files yet.";
                }

                locations.add(new StorageLocation(
                        id,
                        name.isEmpty() ? appContext.getString(R.string.storage_scoped_name) : name,
                        summary,
                        uri,
                        launcherHome != null ? launcherHome.getAbsolutePath() : null,
                        minecraftHome != null ? minecraftHome.getAbsolutePath() : null,
                        false,
                        usable
                ));
            } catch (Throwable throwable) {
                Logging.i("StorageLocationStore", "Skipping broken storage location: " + throwable.getMessage());
            }
        }

        return locations;
    }

    @NonNull
    public static StorageLocation addTreeUri(@NonNull Context context, @NonNull Uri treeUri) {
        Context appContext = context.getApplicationContext();
        String uriString = treeUri.toString();
        String id = buildLocationId(uriString);
        String displayName = buildDisplayName(appContext, treeUri);

        JSONArray array = readLocationArray(appContext);
        JSONArray rewritten = new JSONArray();
        boolean replaced = false;

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject object = array.getJSONObject(i);
                if (id.equals(object.optString("id", ""))) {
                    rewritten.put(toJson(id, displayName, uriString));
                    replaced = true;
                } else {
                    rewritten.put(object);
                }
            } catch (Throwable ignored) {
            }
        }

        if (!replaced) {
            rewritten.put(toJson(id, displayName, uriString));
        }

        getPrefs(appContext).edit().putString(KEY_LOCATIONS, rewritten.toString()).apply();

        for (StorageLocation location : getLocations(appContext)) {
            if (id.equals(location.getId())) return location;
        }
        return new StorageLocation(id, displayName, uriString, uriString, false);
    }


    /**
     * Removes a user-added storage location from the launcher list.
     *
     * This only forgets the saved SAF/tree URI entry and releases the persisted
     * permission when Android allows it. It never deletes files from storage.
     * The default location is protected and cannot be removed.
     */
    public static boolean removeLocation(@NonNull Context context, @NonNull String id) {
        Context appContext = context.getApplicationContext();
        if (DEFAULT_LOCATION_ID.equals(id)) {
            return false;
        }

        JSONArray array = readLocationArray(appContext);
        JSONArray rewritten = new JSONArray();
        boolean removed = false;
        String removedUriString = "";

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject object = array.getJSONObject(i);
                if (id.equals(object.optString("id", ""))) {
                    removed = true;
                    removedUriString = object.optString("uri", "");
                } else {
                    rewritten.put(object);
                }
            } catch (Throwable ignored) {
            }
        }

        if (!removed) {
            return false;
        }

        SharedPreferences.Editor editor = getPrefs(appContext)
                .edit()
                .putString(KEY_LOCATIONS, rewritten.toString());

        if (id.equals(getSelectedLocationId(appContext))) {
            editor.putString(KEY_SELECTED_LOCATION_ID, DEFAULT_LOCATION_ID);
        }

        editor.apply();
        releasePersistablePermissionIfPossible(appContext, removedUriString);
        return true;
    }

    public static void setSelectedLocationId(@NonNull Context context, @NonNull String id) {
        getPrefs(context).edit().putString(KEY_SELECTED_LOCATION_ID, id).apply();
    }

    @NonNull
    public static String getSelectedLocationId(@NonNull Context context) {
        String id = getPrefs(context).getString(KEY_SELECTED_LOCATION_ID, DEFAULT_LOCATION_ID);
        return id == null || id.isEmpty() ? DEFAULT_LOCATION_ID : id;
    }

    @NonNull
    public static StorageLocation getSelectedLocation(@NonNull Context context) {
        String selectedId = getSelectedLocationId(context);
        List<StorageLocation> locations = getLocations(context);

        for (StorageLocation location : locations) {
            if (selectedId.equals(location.getId())) {
                return location;
            }
        }

        setSelectedLocationId(context, DEFAULT_LOCATION_ID);
        return locations.get(0);
    }

    @Nullable
    public static String getSelectedTreeUriString(@NonNull Context context) {
        StorageLocation location = getSelectedLocation(context);
        return location.isDefaultLocation() ? null : location.getUriString();
    }

    @NonNull
    public static File getSelectedLauncherHome(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        StorageLocation selected = getSelectedLocation(appContext);

        if (!selected.isDefaultLocation()
                && selected.isUsableForFileLaunch()
                && selected.getLauncherHomePath() != null
                && !selected.getLauncherHomePath().trim().isEmpty()) {
            return new File(selected.getLauncherHomePath());
        }

        if (!selected.isDefaultLocation()) {
            Logging.i("StorageLocationStore", "Selected storage is not usable as a File path yet; falling back to default: "
                    + selected.getSummary());
        }
        return PathManager.getDefaultLauncherHome(appContext);
    }


    @NonNull
    public static File getSelectedMinecraftHome(@NonNull Context context) {
        return new File(getSelectedLauncherHome(context), ".minecraft");
    }

    /**
     * The dialog can remember multiple folders, but the main instance list should
     * show only the currently selected storage location. Otherwise choosing a
     * scoped/SD/USB location still appears to show files from default storage.
     */
    @NonNull
    public static List<File> getVisibleLauncherHomes(@NonNull Context context) {
        ArrayList<File> homes = new ArrayList<>();
        StorageLocation selected = getSelectedLocation(context);

        if (!selected.isDefaultLocation()
                && selected.getLauncherHomePath() != null
                && !selected.getLauncherHomePath().trim().isEmpty()) {
            homes.add(new File(selected.getLauncherHomePath().trim()));
            return homes;
        }

        homes.add(PathManager.getDefaultLauncherHome(context.getApplicationContext()));
        return homes;
    }

    @NonNull
    public static List<File> getVisibleMinecraftHomes(@NonNull Context context) {
        ArrayList<File> homes = new ArrayList<>();
        for (File launcherHome : getVisibleLauncherHomes(context)) {
            homes.add(new File(launcherHome, ".minecraft"));
        }
        return homes;
    }

    @NonNull
    public static List<File> getAllLauncherHomes(@NonNull Context context) {
        ArrayList<File> homes = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (StorageLocation location : getLocations(context)) {
            String path = location.getLauncherHomePath();
            if (path == null || path.trim().isEmpty()) continue;
            File home = new File(path.trim());
            addHomeIfMissing(homes, seen, home);
        }

        addHomeIfMissing(homes, seen, getSelectedLauncherHome(context));
        addHomeIfMissing(homes, seen, PathManager.getDefaultLauncherHome(context));
        return homes;
    }

    @NonNull
    public static List<File> getAllMinecraftHomes(@NonNull Context context) {
        ArrayList<File> homes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (File launcherHome : getAllLauncherHomes(context)) {
            File minecraftHome = new File(launcherHome, ".minecraft");
            String path = minecraftHome.getAbsolutePath();
            if (seen.add(path)) homes.add(minecraftHome);
        }
        return homes;
    }

    @Nullable
    public static File resolveTreeUriToLauncherHome(@NonNull Context context, @NonNull Uri uri) {
        File selectedDirectory = resolveTreeUriToFile(context, uri);
        if (selectedDirectory == null) return null;
        return normalizeSelectedDirectoryToLauncherHome(selectedDirectory);
    }

    @Nullable
    public static File resolveTreeUriToFile(@NonNull Context context, @NonNull Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                return path == null || path.trim().isEmpty() ? null : new File(path);
            }

            String documentId = DocumentsContract.getTreeDocumentId(uri);
            if (documentId == null || documentId.trim().isEmpty()) {
                return null;
            }

            String volume = documentId;
            String relative = "";
            int split = documentId.indexOf(':');
            if (split >= 0) {
                volume = documentId.substring(0, split);
                relative = split + 1 < documentId.length() ? documentId.substring(split + 1) : "";
            }

            File base;
            if ("primary".equalsIgnoreCase(volume)) {
                base = Environment.getExternalStorageDirectory();
            } else if ("home".equalsIgnoreCase(volume)) {
                base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            } else {
                base = new File("/storage", volume);
            }

            return relative.trim().isEmpty() ? base : new File(base, relative);
        } catch (Throwable throwable) {
            Logging.i("StorageLocationStore", "Unable to resolve tree URI " + uri + ": " + throwable.getMessage());
            return null;
        }
    }

    @NonNull
    private static File normalizeSelectedDirectoryToLauncherHome(@NonNull File selectedDirectory) {
        if (".minecraft".equals(selectedDirectory.getName())) {
            File parent = selectedDirectory.getParentFile();
            if (parent != null) return parent;
        }
        return selectedDirectory;
    }

    private static boolean isUsableLauncherHome(@NonNull File launcherHome) {
        try {
            if (!launcherHome.exists() && !launcherHome.mkdirs()) return false;
            if (!launcherHome.isDirectory()) return false;

            File minecraftHome = new File(launcherHome, ".minecraft");
            if (!minecraftHome.exists() && !minecraftHome.mkdirs()) return false;
            if (!minecraftHome.isDirectory()) return false;

            File probe = new File(minecraftHome, ".javalauncher_storage_probe");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(probe, false)) {
                output.write('1');
            }
            //noinspection ResultOfMethodCallIgnored
            probe.delete();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }


    private static void releasePersistablePermissionIfPossible(@NonNull Context context, @Nullable String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            return;
        }

        try {
            Uri uri = Uri.parse(uriString);
            context.getApplicationContext()
                    .getContentResolver()
                    .releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
        } catch (Throwable throwable) {
            Logging.i("StorageLocationStore", "Unable to release storage URI permission: " + throwable.getMessage());
        }
    }

    @NonNull
    private static SharedPreferences getPrefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static JSONArray readLocationArray(@NonNull Context context) {
        String raw = getPrefs(context).getString(KEY_LOCATIONS, "[]");
        try {
            return new JSONArray(raw == null || raw.isEmpty() ? "[]" : raw);
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    @NonNull
    private static JSONObject toJson(@NonNull String id, @NonNull String name, @NonNull String uri) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("name", name);
            object.put("uri", uri);
        } catch (Throwable ignored) {
        }
        return object;
    }

    @NonNull
    private static String buildLocationId(@NonNull String uriString) {
        return "tree:" + Integer.toHexString(uriString.hashCode());
    }

    @NonNull
    private static String buildDisplayName(@NonNull Context context, @NonNull Uri uri) {
        File resolved = resolveTreeUriToLauncherHome(context, uri);
        if (resolved != null) {
            String name = resolved.getName();
            if (name != null && !name.trim().isEmpty()) return name;
        }

        String segment = uri.getLastPathSegment();
        if (segment == null || segment.trim().isEmpty()) {
            return context.getString(R.string.storage_scoped_name);
        }

        int split = segment.indexOf(':');
        if (split >= 0 && split + 1 < segment.length()) {
            String path = segment.substring(split + 1);
            if (!path.trim().isEmpty()) {
                return path;
            }
        }

        return segment.replace(':', '/');
    }

    private static void addHomeIfMissing(@NonNull List<File> homes, @NonNull Set<String> seen, @Nullable File home) {
        if (home == null) return;
        String path = home.getAbsolutePath();
        if (seen.add(path)) homes.add(home);
    }
}
