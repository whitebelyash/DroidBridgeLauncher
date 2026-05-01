package ca.dnamobile.javalauncher.files;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

import ca.dnamobile.javalauncher.R;
import ca.dnamobile.javalauncher.utils.path.PathManager;

/**
 * Minimal Storage Access Framework provider for the launcher folder.
 *
 * The manifest already registers this provider as:
 *     .files.JavaLauncherDocumentsProvider
 *
 * Without this class Android crashes before SplashActivity can open, because content providers
 * are created during process startup. Keep this class even if the file browser UI is not wired yet.
 */
public class JavaLauncherDocumentsProvider extends DocumentsProvider {
    private static final String ROOT_ID = "javalauncher_root";
    private static final String ROOT_DOCUMENT_ID = "root";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
    };

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null && TextUtils.isEmpty(PathManager.DIR_GAME_HOME)) {
            PathManager.initContextConstants(context);
        }
        return true;
    }

    @Override
    public Cursor queryRoots(@Nullable String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        File root = getRootDirectory();

        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID);
        row.add(Root.COLUMN_TITLE, getLauncherTitle());
        row.add(Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_CREATE |
                        Root.FLAG_SUPPORTS_RECENTS |
                        Root.FLAG_SUPPORTS_SEARCH);
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
        row.add(Root.COLUMN_AVAILABLE_BYTES, root.getFreeSpace());
        return result;
    }

    @Override
    public Cursor queryDocument(@NonNull String documentId, @Nullable String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, documentId, getFileForDocumentId(documentId));
        return result;
    }

    @Override
    public Cursor queryChildDocuments(@NonNull String parentDocumentId,
                                      @Nullable String[] projection,
                                      @Nullable String sortOrder) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        File parent = getFileForDocumentId(parentDocumentId);
        File[] files = parent.listFiles();
        if (files == null) return result;

        for (File file : files) {
            includeFile(result, getDocumentIdForFile(file), file);
        }
        return result;
    }

    @Override
    public Cursor querySearchDocuments(@NonNull String rootId,
                                       @NonNull String query,
                                       @Nullable String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        if (!ROOT_ID.equals(rootId)) return result;

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        searchRecursively(result, getRootDirectory(), lowerQuery, 0);
        return result;
    }

    @Override
    public String createDocument(@NonNull String parentDocumentId,
                                 @NonNull String mimeType,
                                 @NonNull String displayName) throws FileNotFoundException {
        File parent = getFileForDocumentId(parentDocumentId);
        if (!parent.isDirectory()) {
            throw new FileNotFoundException("Parent is not a directory: " + parentDocumentId);
        }

        File target = buildUniqueFile(parent, displayName, DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType));
        try {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!target.mkdirs() && !target.isDirectory()) {
                    throw new IOException("Unable to create directory");
                }
            } else {
                File parentFile = target.getParentFile();
                if (parentFile != null && !parentFile.exists()) parentFile.mkdirs();
                if (!target.createNewFile()) {
                    throw new IOException("Unable to create file");
                }
            }
            return getDocumentIdForFile(target);
        } catch (IOException e) {
            FileNotFoundException wrapped = new FileNotFoundException(e.getMessage());
            wrapped.initCause(e);
            throw wrapped;
        }
    }

    @Override
    public void deleteDocument(@NonNull String documentId) throws FileNotFoundException {
        File target = getFileForDocumentId(documentId);
        if (ROOT_DOCUMENT_ID.equals(documentId) || ".minecraft".equals(documentId)) {
            throw new FileNotFoundException("Refusing to delete launcher root");
        }
        deleteRecursively(target);
    }

    @Override
    public String renameDocument(@NonNull String documentId, @NonNull String displayName) throws FileNotFoundException {
        File target = getFileForDocumentId(documentId);
        File parent = target.getParentFile();
        if (parent == null) throw new FileNotFoundException("No parent for: " + documentId);

        File renamed = buildUniqueFile(parent, displayName, target.isDirectory());
        if (!target.renameTo(renamed)) {
            throw new FileNotFoundException("Unable to rename: " + documentId);
        }
        return getDocumentIdForFile(renamed);
    }

    @Override
    public ParcelFileDescriptor openDocument(@NonNull String documentId,
                                             @NonNull String mode,
                                             @Nullable CancellationSignal signal) throws FileNotFoundException {
        File file = getFileForDocumentId(documentId);
        if (file.isDirectory()) {
            throw new FileNotFoundException("Cannot open directory: " + documentId);
        }
        return ParcelFileDescriptor.open(file, parseMode(mode));
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(@NonNull String documentId,
                                                     @NonNull Point sizeHint,
                                                     @Nullable CancellationSignal signal) throws FileNotFoundException {
        return super.openDocumentThumbnail(documentId, sizeHint, signal);
    }

    private void includeFile(@NonNull MatrixCursor result,
                             @NonNull String documentId,
                             @NonNull File file) {
        int flags = 0;
        if (file.isDirectory()) {
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        }
        if (file.canWrite() && !ROOT_DOCUMENT_ID.equals(documentId) && !".minecraft".equals(documentId)) {
            flags |= Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME;
        }
        if (file.isFile() && file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }

        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(Document.COLUMN_DISPLAY_NAME, ROOT_DOCUMENT_ID.equals(documentId) ? getLauncherTitle() : file.getName());
        row.add(Document.COLUMN_MIME_TYPE, getMimeType(file));
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_SIZE, file.isFile() ? file.length() : 0);
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    private void searchRecursively(@NonNull MatrixCursor result,
                                   @NonNull File directory,
                                   @NonNull String lowerQuery,
                                   int depth) {
        if (depth > 8 || result.getCount() >= 50) return;

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.getName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                includeFile(result, getDocumentIdForFile(file), file);
            }
            if (file.isDirectory()) {
                searchRecursively(result, file, lowerQuery, depth + 1);
            }
            if (result.getCount() >= 50) return;
        }
    }

    @NonNull
    private File getRootDirectory() throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Provider context is null");

        if (TextUtils.isEmpty(PathManager.DIR_GAME_HOME)) {
            PathManager.initContextConstants(context);
        }

        // Expose the parent app-files directory through Android's Storage Access Framework.
        // This lets the system file picker show .minecraft as a visible child folder even
        // though the real on-disk path is inside Android/data/<package>/files.
        File root = PathManager.getAccessibleLauncherRoot(context);
        if (!root.exists() && !root.mkdirs()) {
            throw new FileNotFoundException("Unable to create launcher root: " + root.getAbsolutePath());
        }

        File minecraftHome = new File(root, ".minecraft");
        if (!minecraftHome.exists() && !minecraftHome.mkdirs()) {
            throw new FileNotFoundException("Unable to create .minecraft folder: " + minecraftHome.getAbsolutePath());
        }
        return root;
    }

    @NonNull
    private File getFileForDocumentId(@NonNull String documentId) throws FileNotFoundException {
        File root = getRootDirectory();
        if (ROOT_DOCUMENT_ID.equals(documentId)) return root;

        try {
            File target = new File(root, documentId).getCanonicalFile();
            File canonicalRoot = root.getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String targetPath = target.getPath();
            if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("Invalid document path: " + documentId);
            }
            return target;
        } catch (IOException e) {
            FileNotFoundException wrapped = new FileNotFoundException(e.getMessage());
            wrapped.initCause(e);
            throw wrapped;
        }
    }

    @NonNull
    private String getDocumentIdForFile(@NonNull File file) {
        try {
            File root = getRootDirectory().getCanonicalFile();
            File target = file.getCanonicalFile();
            if (root.equals(target)) return ROOT_DOCUMENT_ID;

            String rootPath = root.getPath();
            String targetPath = target.getPath();
            if (targetPath.startsWith(rootPath + File.separator)) {
                return targetPath.substring(rootPath.length() + 1);
            }
        } catch (IOException ignored) {
        }
        return file.getName();
    }

    @NonNull
    private String getMimeType(@NonNull File file) {
        if (file.isDirectory()) return Document.MIME_TYPE_DIR;

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (!TextUtils.isEmpty(mimeType)) return mimeType;
        }
        return "application/octet-stream";
    }

    private int parseMode(@NonNull String mode) {
        boolean write = mode.contains("w");
        boolean truncate = mode.contains("t");
        boolean append = mode.contains("a");

        if (write) {
            int accessMode = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE;
            if (truncate) accessMode |= ParcelFileDescriptor.MODE_TRUNCATE;
            if (append) accessMode |= ParcelFileDescriptor.MODE_APPEND;
            return accessMode;
        }
        return ParcelFileDescriptor.MODE_READ_ONLY;
    }

    @NonNull
    private File buildUniqueFile(@NonNull File parent, @NonNull String displayName, boolean directory) {
        File target = new File(parent, displayName);
        if (!target.exists()) return target;

        String baseName = displayName;
        String extension = "";
        if (!directory) {
            int dot = displayName.lastIndexOf('.');
            if (dot > 0) {
                baseName = displayName.substring(0, dot);
                extension = displayName.substring(dot);
            }
        }

        int index = 1;
        do {
            target = new File(parent, baseName + " (" + index + ")" + extension);
            index++;
        } while (target.exists());
        return target;
    }

    private void deleteRecursively(@NonNull File file) throws FileNotFoundException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) {
            throw new FileNotFoundException("Unable to delete: " + file.getAbsolutePath());
        }
    }

    @NonNull
    private String getLauncherTitle() {
        Context context = getContext();
        if (context == null) return "JavaLauncher";
        try {
            return context.getString(R.string.app_name);
        } catch (Throwable ignored) {
            return "JavaLauncher";
        }
    }

    @NonNull
    private String[] resolveRootProjection(@Nullable String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    @NonNull
    private String[] resolveDocumentProjection(@Nullable String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }
}
