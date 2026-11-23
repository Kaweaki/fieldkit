package se.blimark.fieldkit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The storage permission replacement.
 *
 * The app reads MBTiles by path, which only ever worked below Android 10; the
 * manifest carries the storage permissions capped at API 28 for those devices.
 * On anything newer the file has to come through a document picker.
 */
public final class Saf {

    public static final int REQ_OPEN_MBTILES = 4201;
    public static final int REQ_CREATE_BACKUP = 4202;

    private Saf() {
    }

    public static void pickFile(Activity activity, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, requestCode);
    }

    public static void createFile(Activity activity, String name, String mime, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime == null ? "text/plain" : mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        activity.startActivityForResult(intent, requestCode);
    }

    /** Copy a picked document into app storage and return the file. */
    public static File copyInto(Context context, Uri uri, String fallbackName) throws IOException {
        String name = displayName(context, uri);
        if (name == null || name.length() == 0) {
            name = fallbackName;
        }
        File target = new File(context.getFilesDir(), safeName(name));
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException("the picker returned no stream");
            }
            out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            close(in);
            close(out);
        }
        return target;
    }

    /** Write a string to a document the user chose with createFile. */
    public static void writeText(Context context, Uri uri, String text) throws IOException {
        OutputStream out = null;
        try {
            out = context.getContentResolver().openOutputStream(uri, "wt");
            if (out == null) {
                throw new IOException("the document could not be opened for writing");
            }
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } finally {
            close(out);
        }
    }

    public static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver()
                    .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // A provider that refuses the query still gives us a usable path.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /** Strip any path the picker reported, so the copy cannot escape app storage. */
    public static String safeName(String name) {
        if (name == null) {
            return "import.dat";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "_");
        return name.length() == 0 ? "import.dat" : name;
    }

    private static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Nothing useful to do on close failure.
            }
        }
    }
}
