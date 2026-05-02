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
 * Play-compliant scoped-storage rule:
 * - Default storage uses Android's app-specific external files directory.
 * - User-picked storage saves the SAF tree URI and shows the user's picked path.
 * - Minecraft/Forge/NeoForge still require normal java.io.File paths, so custom
 *   SAF locations use an app-private mirror for runtime/install work, then the
 *   mirror is synced to the picked SAF tree with ContentResolver/DocumentsContract.
 * - Never rewrite a custom picked folder to Android/data as if it were the user's
 *   chosen folder.
 */
public final class StorageLocationStore {
    public static final String DEFAULT_LOCATION_ID = "default";

    private static final String TAG = "StorageLocationStore";
    private static final String PREFS_NAME = "storage_locations";
    private static final String KEY_LOCATIONS = "locations_json";
    private static final String KEY_SELECTED_LOCATION_ID = "selected_location_id";

    private static final String JSON_ID = "id";
    private static final String JSON_NAME = "name";
    private static final String JSON_URI = "uri";

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

        JSONArray array = readAndCleanLocationArray(appContext);
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject object = array.getJSONObject(i);
                String id = object.optString(JSON_ID, "");
                String name = object.optString(JSON_NAME, "");
                String uri = object.optString(JSON_URI, "");
                if (id.trim().isEmpty() || uri.trim().isEmpty()) continue;

                Uri treeUri = Uri.parse(uri);
                File displayLauncherHome = resolveTreeUriToLauncherHome(appContext, treeUri);
                File displayMinecraftHome = displayLauncherHome != null ? new File(displayLauncherHome, ".minecraft") : null;

                // This is the real File path used by Minecraft/Forge/NeoForge. It is
                // app-private and Play-safe. The user's chosen folder is synced by SAF.
                File mirrorLauncherHome = getScopedMirrorLauncherHome(appContext, id);
                File mirrorMinecraftHome = new File(mirrorLauncherHome, ".minecraft");

                String summary;
                if (displayMinecraftHome != null) {
                    summary = displayMinecraftHome.getAbsolutePath();
                } else {
                    summary = uri;
                }

                locations.add(new StorageLocation(
                        id,
                        name.trim().isEmpty() ? buildDisplayName(appContext, treeUri, displayLauncherHome) : name,
                        summary,
                        uri,
                        mirrorLauncherHome.getAbsolutePath(),
                        mirrorMinecraftHome.getAbsolutePath(),
                        false,
                        true
                ));
            } catch (Throwable throwable) {
                Logging.i(TAG, "Skipping broken storage location: " + throwable.getMessage());
            }
        }

        return locations;
    }

    @NonNull
    public static StorageLocation addTreeUri(@NonNull Context context, @NonNull Uri treeUri) {
        Context appContext = context.getApplicationContext();
        String uriString = treeUri.toString();
        String id = buildLocationId(uriString);
        File displayHome = resolveTreeUriToLauncherHome(appContext, treeUri);
        String displayName = buildDisplayName(appContext, treeUri, displayHome);

        JSONArray array = readLocationArray(appContext);
        JSONArray rewritten = new JSONArray();
        boolean replaced = false;

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject object = array.getJSONObject(i);
                if (id.equals(object.optString(JSON_ID, ""))) {
                    rewritten.put(toJson(id, displayName, uriString));
                    replaced = true;
                } else {
                    rewritten.put(object);
                }
            } catch (Throwable ignored) {
            }
        }

        if (!replaced) rewritten.put(toJson(id, displayName, uriString));
        getPrefs(appContext).edit().putString(KEY_LOCATIONS, rewritten.toString()).apply();

        for (StorageLocation location : getLocations(appContext)) {
            if (id.equals(location.getId())) return location;
        }

        File mirrorHome = getScopedMirrorLauncherHome(appContext, id);
        return new StorageLocation(
                id,
                displayName,
                displayHome != null ? new File(displayHome, ".minecraft").getAbsolutePath() : uriString,
                uriString,
                mirrorHome.getAbsolutePath(),
                new File(mirrorHome, ".minecraft").getAbsolutePath(),
                false,
                true
        );
    }

    public static boolean removeLocation(@NonNull Context context, @NonNull String id) {
        Context appContext = context.getApplicationContext();
        if (DEFAULT_LOCATION_ID.equals(id)) return false;

        JSONArray array = readLocationArray(appContext);
        JSONArray rewritten = new JSONArray();
        boolean removed = false;
        String removedUriString = "";

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject object = array.getJSONObject(i);
                if (id.equals(object.optString(JSON_ID, ""))) {
                    removed = true;
                    removedUriString = object.optString(JSON_URI, "");
                } else {
                    rewritten.put(object);
                }
            } catch (Throwable ignored) {
            }
        }

        if (!removed) return false;

        SharedPreferences.Editor editor = getPrefs(appContext).edit().putString(KEY_LOCATIONS, rewritten.toString());
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
        return id == null || id.trim().isEmpty() ? DEFAULT_LOCATION_ID : id;
    }

    @NonNull
    public static StorageLocation getSelectedLocation(@NonNull Context context) {
        String selectedId = getSelectedLocationId(context);
        List<StorageLocation> locations = getLocations(context);
        for (StorageLocation location : locations) {
            if (selectedId.equals(location.getId())) return location;
        }
        setSelectedLocationId(context, DEFAULT_LOCATION_ID);
        return locations.get(0);
    }

    @Nullable
    public static String getSelectedTreeUriString(@NonNull Context context) {
        StorageLocation location = getSelectedLocation(context);
        return location.isDefaultLocation() ? null : location.getUriString();
    }

    @Nullable
    public static Uri getSelectedTreeUri(@NonNull Context context) {
        String value = getSelectedTreeUriString(context);
        return value == null || value.trim().isEmpty() ? null : Uri.parse(value.trim());
    }

    public static boolean isSelectedScopedStorage(@NonNull Context context) {
        return !getSelectedLocation(context).isDefaultLocation() && getSelectedTreeUri(context) != null;
    }

    /**
     * Returns the actual local File root used by installers and the JVM. For SAF
     * locations this is an app-private mirror, not Android/data pretending to be
     * the user's selected folder.
     */
    @NonNull
    public static File getSelectedLauncherHome(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        StorageLocation selected = getSelectedLocation(appContext);
        String path = selected.getLauncherHomePath();
        if (path != null && !path.trim().isEmpty()) return new File(path.trim());
        return PathManager.getDefaultLauncherHome(appContext);
    }

    @NonNull
    public static File getSelectedMinecraftHome(@NonNull Context context) {
        return new File(getSelectedLauncherHome(context), ".minecraft");
    }

    @NonNull
    public static List<File> getVisibleLauncherHomes(@NonNull Context context) {
        ArrayList<File> homes = new ArrayList<>();
        homes.add(getSelectedLauncherHome(context));
        return homes;
    }

    @NonNull
    public static List<File> getVisibleMinecraftHomes(@NonNull Context context) {
        ArrayList<File> homes = new ArrayList<>();
        for (File launcherHome : getVisibleLauncherHomes(context)) homes.add(new File(launcherHome, ".minecraft"));
        return homes;
    }

    @NonNull
    public static List<File> getAllLauncherHomes(@NonNull Context context) {
        ArrayList<File> homes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (StorageLocation location : getLocations(context)) {
            String path = location.getLauncherHomePath();
            if (path != null && !path.trim().isEmpty()) addHomeIfMissing(homes, seen, new File(path.trim()));
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
            if (seen.add(minecraftHome.getAbsolutePath())) homes.add(minecraftHome);
        }
        return homes;
    }

    @NonNull
    public static File getScopedMirrorLauncherHome(@NonNull Context context, @NonNull String id) {
        String safeId = id.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(context.getApplicationContext().getFilesDir(), "scoped_storage_mirrors/" + safeId);
    }

    public static void syncSelectedMirrorToTree(
            @NonNull Context context,
            @Nullable SafMinecraftMirror.Progress progress
    ) throws Exception {
        Context appContext = context.getApplicationContext();
        StorageLocation selected = getSelectedLocation(appContext);
        if (selected.isDefaultLocation()) return;
        Uri treeUri = getSelectedTreeUri(appContext);
        if (treeUri == null) return;
        File localLauncherHome = getSelectedLauncherHome(appContext);
        SafMinecraftMirror.copyLocalLauncherHomeToTree(appContext, localLauncherHome, treeUri, progress);
    }

    public static void syncSelectedTreeToMirror(
            @NonNull Context context,
            @Nullable SafMinecraftMirror.Progress progress
    ) throws Exception {
        Context appContext = context.getApplicationContext();
        StorageLocation selected = getSelectedLocation(appContext);
        if (selected.isDefaultLocation()) return;
        Uri treeUri = getSelectedTreeUri(appContext);
        if (treeUri == null) return;
        File localLauncherHome = getSelectedLauncherHome(appContext);
        SafMinecraftMirror.copyTreeToLocalLauncherHome(appContext, treeUri, localLauncherHome, progress);
    }


    /**
     * If a local mirror path belongs to a custom SAF storage location, delete the
     * matching path from the picked scoped-storage tree too. This is required
     * because deleting the local mirror alone does not remove the already-synced
     * files from the user's selected folder.
     */
    public static boolean deleteFromScopedStorageIfNeeded(
            @NonNull Context context,
            @NonNull File localPath
    ) throws Exception {
        Context appContext = context.getApplicationContext();
        File target = localPath.getCanonicalFile();

        for (StorageLocation location : getLocations(appContext)) {
            if (location.isDefaultLocation()) continue;

            String uriString = location.getUriString();
            String launcherHomePath = location.getLauncherHomePath();
            if (uriString == null || uriString.trim().isEmpty()) continue;
            if (launcherHomePath == null || launcherHomePath.trim().isEmpty()) continue;

            File launcherHome = new File(launcherHomePath.trim()).getCanonicalFile();
            if (!target.equals(launcherHome) && !isChildOf(launcherHome, target)) {
                continue;
            }

            String relative = relativePath(launcherHome, target);
            if (relative.isEmpty()) {
                Logging.i(TAG, "Refusing to delete scoped storage root for local path: " + target.getAbsolutePath());
                return false;
            }

            return SafMinecraftMirror.deleteRelativePathFromTree(
                    appContext,
                    Uri.parse(uriString.trim()),
                    relative
            );
        }

        return false;
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
            if (documentId == null || documentId.trim().isEmpty()) return null;

            String volume = documentId;
            String relative = "";
            int split = documentId.indexOf(':');
            if (split >= 0) {
                volume = documentId.substring(0, split);
                relative = split + 1 < documentId.length() ? documentId.substring(split + 1) : "";
            }

            File base;
            if ("primary".equalsIgnoreCase(volume)) base = Environment.getExternalStorageDirectory();
            else if ("home".equalsIgnoreCase(volume)) base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            else base = new File("/storage", volume);

            return relative.trim().isEmpty() ? base : new File(base, relative);
        } catch (Throwable throwable) {
            Logging.i(TAG, "Unable to resolve tree URI " + uri + ": " + throwable.getMessage());
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

    @NonNull
    private static JSONArray readAndCleanLocationArray(@NonNull Context context) {
        JSONArray raw = readLocationArray(context);
        JSONArray cleaned = new JSONArray();
        Set<String> seenIds = new HashSet<>();
        boolean changed = false;
        String selectedId = getSelectedLocationId(context);
        boolean selectedRemoved = false;

        for (int i = 0; i < raw.length(); i++) {
            try {
                JSONObject object = raw.getJSONObject(i);
                String id = object.optString(JSON_ID, "");
                String name = object.optString(JSON_NAME, "");
                String uri = object.optString(JSON_URI, "");
                if (id.trim().isEmpty() || uri.trim().isEmpty() || !seenIds.add(id)) {
                    changed = true;
                    if (id.equals(selectedId)) selectedRemoved = true;
                    continue;
                }
                cleaned.put(toJson(id, name, uri));
            } catch (Throwable throwable) {
                changed = true;
            }
        }

        if (changed) {
            SharedPreferences.Editor editor = getPrefs(context).edit().putString(KEY_LOCATIONS, cleaned.toString());
            if (selectedRemoved) editor.putString(KEY_SELECTED_LOCATION_ID, DEFAULT_LOCATION_ID);
            editor.apply();
        }

        return cleaned;
    }

    private static void releasePersistablePermissionIfPossible(@NonNull Context context, @Nullable String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) return;
        try {
            Uri uri = Uri.parse(uriString);
            context.getApplicationContext().getContentResolver().releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Throwable throwable) {
            Logging.i(TAG, "Unable to release storage URI permission: " + throwable.getMessage());
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
            return new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    @NonNull
    private static JSONObject toJson(@NonNull String id, @NonNull String name, @NonNull String uri) {
        JSONObject object = new JSONObject();
        try {
            object.put(JSON_ID, id);
            object.put(JSON_NAME, name);
            object.put(JSON_URI, uri);
        } catch (Throwable ignored) {
        }
        return object;
    }

    @NonNull
    private static String buildLocationId(@NonNull String uriString) {
        return "tree:" + Integer.toHexString(uriString.hashCode());
    }

    @NonNull
    private static String buildDisplayName(@NonNull Context context, @NonNull Uri uri, @Nullable File resolvedHome) {
        if (resolvedHome != null) {
            String name = resolvedHome.getName();
            if (name != null && !name.trim().isEmpty()) return name;
        }

        String segment = uri.getLastPathSegment();
        if (segment == null || segment.trim().isEmpty()) return context.getString(R.string.storage_scoped_name);

        int split = segment.indexOf(':');
        if (split >= 0 && split + 1 < segment.length()) {
            String path = segment.substring(split + 1);
            if (!path.trim().isEmpty()) {
                int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                return slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
            }
        }
        return segment.replace(':', '/');
    }


    private static boolean isChildOf(@NonNull File parent, @NonNull File child) throws Exception {
        String parentPath = parent.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        return childPath.startsWith(parentPath + File.separator);
    }

    @NonNull
    private static String relativePath(@NonNull File root, @NonNull File file) throws Exception {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        if (!filePath.startsWith(rootPath)) return "";
        String out = filePath.substring(rootPath.length());
        while (out.startsWith(File.separator)) out = out.substring(1);
        return out.replace(File.separatorChar, '/');
    }

    private static void addHomeIfMissing(@NonNull List<File> homes, @NonNull Set<String> seen, @Nullable File home) {
        if (home == null) return;
        String path = home.getAbsolutePath();
        if (seen.add(path)) homes.add(home);
    }
}
