package ca.dnamobile.javalauncher.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import ca.dnamobile.javalauncher.feature.log.Logging;

/**
 * Minimal SAF mirror used for Google Play compliant scoped storage.
 *
 * Minecraft/Forge/NeoForge installers and the JVM need java.io.File paths, so the
 * launcher runs from an app-private mirror and syncs that mirror to the user-picked
 * SAF tree with ContentResolver/DocumentsContract.
 */
public final class SafMinecraftMirror {
    private static final String TAG = "SafMinecraftMirror";
    private static final int BUFFER_SIZE = 64 * 1024;

    private SafMinecraftMirror() {
    }

    public interface Progress {
        void onProgress(int progress, @NonNull String message);
    }

    public static void copyLocalLauncherHomeToTree(
            @NonNull Context context,
            @NonNull File launcherHome,
            @NonNull Uri treeUri,
            @Nullable Progress progress
    ) throws Exception {
        if (!launcherHome.isDirectory()) return;

        Uri rootDocument = getRootDocumentUri(treeUri);
        if (rootDocument == null) {
            throw new IllegalStateException("Unable to resolve scoped storage root.");
        }

        if (progress != null) progress.onProgress(98, "Syncing scoped storage...");
        copyLocalDirectoryToDocument(context, launcherHome, rootDocument, progress, launcherHome);
    }

    public static void copyTreeToLocalLauncherHome(
            @NonNull Context context,
            @NonNull Uri treeUri,
            @NonNull File launcherHome,
            @Nullable Progress progress
    ) throws Exception {
        Uri rootDocument = getRootDocumentUri(treeUri);
        if (rootDocument == null) return;

        if (!launcherHome.exists() && !launcherHome.mkdirs()) {
            throw new IllegalStateException("Unable to create scoped storage mirror: " + launcherHome.getAbsolutePath());
        }

        if (progress != null) progress.onProgress(2, "Reading scoped storage mirror...");
        copyDocumentDirectoryToLocal(context, rootDocument, launcherHome, progress, "");
    }

    @Nullable
    private static Uri getRootDocumentUri(@NonNull Uri treeUri) {
        try {
            String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId);
        } catch (Throwable throwable) {
            Logging.i(TAG, "Unable to build root document URI: " + throwable.getMessage());
            return null;
        }
    }


    /**
     * Deletes a file or directory from the picked SAF tree using a path relative
     * to the launcher home mirror.
     *
     * Example relative paths:
     * - .minecraft/instances/my-instance
     * - .minecraft/versions/1.21.1-forge
     */
    public static boolean deleteRelativePathFromTree(
            @NonNull Context context,
            @NonNull Uri treeUri,
            @NonNull String relativePath
    ) throws Exception {
        Uri rootDocument = getRootDocumentUri(treeUri);
        if (rootDocument == null) {
            throw new IllegalStateException("Unable to resolve scoped storage root.");
        }

        Uri target = findDescendant(context, rootDocument, relativePath);
        if (target == null) {
            Logging.i(TAG, "Scoped delete target not found: " + relativePath);
            return false;
        }

        deleteDocumentRecursively(context, target);
        Logging.i(TAG, "Deleted scoped storage path: " + relativePath);
        return true;
    }

    @Nullable
    private static Uri findDescendant(
            @NonNull Context context,
            @NonNull Uri rootDirectory,
            @NonNull String relativePath
    ) throws Exception {
        String cleaned = relativePath.replace('\\', '/').trim();
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        if (cleaned.isEmpty()) return rootDirectory;

        Uri current = rootDirectory;
        String[] parts = cleaned.split("/");
        for (String rawPart : parts) {
            String part = rawPart == null ? "" : rawPart.trim();
            if (part.isEmpty() || ".".equals(part)) continue;
            Uri next = findChildAny(context, current, part);
            if (next == null) return null;
            current = next;
        }
        return current;
    }

    @Nullable
    private static Uri findChildAny(
            @NonNull Context context,
            @NonNull Uri parentDirectory,
            @NonNull String name
    ) {
        try {
            for (DocumentEntry entry : listChildren(context, parentDirectory)) {
                if (name.equals(entry.displayName)) return entry.uri;
            }
        } catch (Throwable throwable) {
            Logging.i(TAG, "findChildAny failed: " + throwable.getMessage());
        }
        return null;
    }

    private static void deleteDocumentRecursively(
            @NonNull Context context,
            @NonNull Uri documentUri
    ) throws Exception {
        boolean directory = isDirectory(context, documentUri);
        if (directory) {
            for (DocumentEntry child : listChildren(context, documentUri)) {
                deleteDocumentRecursively(context, child.uri);
            }
        }

        boolean deleted = DocumentsContract.deleteDocument(context.getContentResolver(), documentUri);
        if (!deleted) {
            throw new IllegalStateException("Unable to delete SAF document: " + documentUri);
        }
    }

    private static boolean isDirectory(@NonNull Context context, @NonNull Uri documentUri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                documentUri,
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) return false;
            String mimeType = cursor.getString(0);
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
        } catch (Throwable throwable) {
            Logging.i(TAG, "Unable to inspect SAF document type: " + throwable.getMessage());
            return false;
        }
    }

    private static void copyLocalDirectoryToDocument(
            @NonNull Context context,
            @NonNull File localDirectory,
            @NonNull Uri documentDirectory,
            @Nullable Progress progress,
            @NonNull File rootForDisplay
    ) throws Exception {
        File[] children = localDirectory.listFiles();
        if (children == null) return;

        java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (child.getName().equals("launcher_log")) continue;
            if (child.getName().equals(".java_launcher_saf_tmp")) continue;

            if (child.isDirectory()) {
                Uri childDirectory = ensureDirectory(context, documentDirectory, child.getName());
                copyLocalDirectoryToDocument(context, child, childDirectory, progress, rootForDisplay);
            } else if (child.isFile()) {
                String relative = relativePath(rootForDisplay, child);
                if (progress != null) progress.onProgress(99, "Syncing scoped storage: " + relative);
                copyLocalFileToDocument(context, child, documentDirectory, child.getName());
            }
        }
    }

    private static void copyDocumentDirectoryToLocal(
            @NonNull Context context,
            @NonNull Uri documentDirectory,
            @NonNull File localDirectory,
            @Nullable Progress progress,
            @NonNull String relativePrefix
    ) throws Exception {
        if (!localDirectory.exists() && !localDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create local mirror directory: " + localDirectory.getAbsolutePath());
        }

        for (DocumentEntry entry : listChildren(context, documentDirectory)) {
            if (entry.displayName == null || entry.displayName.trim().isEmpty()) continue;
            File localChild = new File(localDirectory, entry.displayName);
            String relative = relativePrefix.isEmpty() ? entry.displayName : relativePrefix + "/" + entry.displayName;

            if (entry.directory) {
                copyDocumentDirectoryToLocal(context, entry.uri, localChild, progress, relative);
            } else {
                if (progress != null) progress.onProgress(3, "Reading scoped storage: " + relative);
                copyDocumentFileToLocal(context, entry.uri, localChild);
            }
        }
    }

    @NonNull
    private static Uri ensureDirectory(
            @NonNull Context context,
            @NonNull Uri parentDirectory,
            @NonNull String name
    ) throws Exception {
        Uri existing = findChild(context, parentDirectory, name, true);
        if (existing != null) return existing;

        Uri created = DocumentsContract.createDocument(
                context.getContentResolver(),
                parentDirectory,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name
        );
        if (created == null) throw new IllegalStateException("Unable to create SAF directory: " + name);
        return created;
    }

    private static void copyLocalFileToDocument(
            @NonNull Context context,
            @NonNull File source,
            @NonNull Uri parentDirectory,
            @NonNull String name
    ) throws Exception {
        Uri fileUri = findChild(context, parentDirectory, name, false);
        if (fileUri == null) {
            fileUri = DocumentsContract.createDocument(
                    context.getContentResolver(),
                    parentDirectory,
                    "application/octet-stream",
                    name
            );
        }
        if (fileUri == null) throw new IllegalStateException("Unable to create SAF file: " + name);

        try (InputStream input = new FileInputStream(source);
             OutputStream output = context.getContentResolver().openOutputStream(fileUri, "wt")) {
            if (output == null) throw new IllegalStateException("Unable to open SAF output: " + name);
            copy(input, output);
        }
    }

    private static void copyDocumentFileToLocal(
            @NonNull Context context,
            @NonNull Uri source,
            @NonNull File target
    ) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create local mirror parent: " + parent.getAbsolutePath());
        }

        try (InputStream input = context.getContentResolver().openInputStream(source);
             OutputStream output = new FileOutputStream(target, false)) {
            if (input == null) throw new IllegalStateException("Unable to open SAF input: " + source);
            copy(input, output);
        }
    }

    @Nullable
    private static Uri findChild(
            @NonNull Context context,
            @NonNull Uri parentDirectory,
            @NonNull String name,
            boolean directory
    ) {
        try {
            for (DocumentEntry entry : listChildren(context, parentDirectory)) {
                if (name.equals(entry.displayName) && entry.directory == directory) return entry.uri;
            }
        } catch (Throwable throwable) {
            Logging.i(TAG, "findChild failed: " + throwable.getMessage());
        }
        return null;
    }

    @NonNull
    private static java.util.ArrayList<DocumentEntry> listChildren(
            @NonNull Context context,
            @NonNull Uri parentDirectory
    ) throws Exception {
        java.util.ArrayList<DocumentEntry> result = new java.util.ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        String parentDocumentId = DocumentsContract.getDocumentId(parentDirectory);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentDirectory, parentDocumentId);

        try (Cursor cursor = resolver.query(
                childrenUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                },
                null,
                null,
                null
        )) {
            if (cursor == null) return result;
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String displayName = cursor.getString(1);
                String mimeType = cursor.getString(2);
                if (documentId == null || displayName == null) continue;

                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(parentDirectory, documentId);
                boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
                result.add(new DocumentEntry(documentUri, displayName, directory));
            }
        }

        return result;
    }

    private static void copy(@NonNull InputStream input, @NonNull OutputStream output) throws Exception {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    @NonNull
    private static String relativePath(@NonNull File root, @NonNull File file) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(rootPath)) {
                String out = filePath.substring(rootPath.length());
                while (out.startsWith(File.separator)) out = out.substring(1);
                return out;
            }
        } catch (Throwable ignored) {
        }
        return file.getName();
    }

    private static final class DocumentEntry {
        final Uri uri;
        final String displayName;
        final boolean directory;

        DocumentEntry(@NonNull Uri uri, @NonNull String displayName, boolean directory) {
            this.uri = uri;
            this.displayName = displayName;
            this.directory = directory;
        }
    }
}
