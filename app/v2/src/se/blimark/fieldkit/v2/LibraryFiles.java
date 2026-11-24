package se.blimark.fieldkit.v2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

import se.blimark.fieldkit.Library;

/**
 * Hands one file under shared storage to whichever app the user picked.
 *
 * Without INTERNET and without a share sheet this is the smallest correct way to
 * open a file that lives in Download: a content:// URI the receiving app can read
 * for exactly as long as the intent allows, and no path outside
 * /storage/emulated/0 ever served. The authority is the app's own, and the
 * provider is not exported, so nothing else can ask it for a file.
 */
public final class LibraryFiles extends ContentProvider {

    public static final String AUTHORITY = "se.blimark.fieldkit.v2.files";
    private static final String ROOT = "/storage/emulated/0";

    /** content://<authority>/file/storage/emulated/0/Download/x.pdf */
    public static Uri uriFor(String path) {
        return Uri.parse("content://" + AUTHORITY + "/file" + path);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("this provider is read-only");
        }
        if (!file.isFile() || !file.canRead()) {
            throw new FileNotFoundException(file.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        try {
            return Library.mime(resolve(uri).getName());
        } catch (FileNotFoundException error) {
            return null;
        }
    }

    /** Enough of a query for any viewer that asks for a name and a length. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException error) {
            return null;
        }
        String[] columns = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = file.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private static File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null || !path.startsWith("/file/")) {
            throw new FileNotFoundException("not a library file URI");
        }
        String absolute = path.substring("/file".length());
        if (!absolute.startsWith(ROOT + "/")) {
            throw new FileNotFoundException("outside shared storage: " + absolute);
        }
        try {
            File file = new File(absolute);
            if (!file.getCanonicalPath().startsWith(ROOT + "/")) {
                throw new FileNotFoundException("outside shared storage");
            }
            return file;
        } catch (java.io.IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }
}
