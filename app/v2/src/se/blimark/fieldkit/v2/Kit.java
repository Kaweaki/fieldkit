package se.blimark.fieldkit.v2;

import android.app.Activity;
import android.app.WallpaperManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import se.blimark.fieldkit.Ice;
import se.blimark.fieldkit.Perms;
import se.blimark.fieldkit.TextLink;
import se.blimark.fieldkit.Torch;

/** Link, Share, Screens, Library, Journal, Maps and Comms. */
public final class Kit {

    /** Kiwix ships as a standalone and a store build; either will do. */
    private static final String[] KIWIX = {
            "org.kiwix.kiwixmobile.standalone", "org.kiwix.kiwixmobile"
    };

    private static final String[] BRIAR = {
            "org.briarproject.briar.android", "org.briarproject.briar"
    };

    private static String pendingExport;
    static String mapsReport;

    private Kit() {
    }

    // ---- link --------------------------------------------------------------

    public static View link(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "LINK",
                "Short text between two paired phones, over Bluetooth serial.", back);

        column.addView(Ui.caps(activity, "Radio permission", 13f, Ui.AMBER));
        column.addView(Ui.body(activity, Perms.explain(activity)));
        column.addView(Ui.button(activity, "ASK FOR IT", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Perms.ensureRadio(activity);
            }
        }));

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            column.addView(Ui.note(activity, "This phone reports no Bluetooth adapter."));
            return column;
        }
        if (!adapter.isEnabled()) {
            column.addView(Ui.note(activity, "Bluetooth is off. Pairing and sending both need "
                    + "it on; nothing in this app turns it on for you."));
            return column;
        }

        final List<BluetoothDevice> bonded = new ArrayList<BluetoothDevice>();
        try {
            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            for (BluetoothDevice device : devices) {
                bonded.add(device);
            }
        } catch (SecurityException e) {
            column.addView(Ui.note(activity, Perms.explain(activity)));
            return column;
        }

        column.addView(Ui.caps(activity, "Paired phones", 13f, Ui.AMBER));
        if (bonded.isEmpty()) {
            column.addView(Ui.note(activity, "Nothing is paired yet. Pair the other phone in "
                    + "the Android settings first; this screen only sends to something already "
                    + "trusted."));
        }

        final EditText message = Ui.textField(activity, "message, 400 characters",
                "SOS need help");
        final TextView status = Ui.note(activity, "");

        for (final BluetoothDevice device : bonded) {
            String label;
            try {
                label = device.getName() + "  " + device.getAddress();
            } catch (SecurityException e) {
                label = device.getAddress();
            }
            column.addView(Ui.button(activity, "SEND TO " + label, Ui.TEAL,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            send(activity, device, message, status);
                        }
                    }));
        }
        column.addView(message);
        column.addView(status);
        column.addView(Ui.note(activity, "Each message is one framed line: a sender id, a "
                + "sequence number and the text, with tabs and newlines folded so a message can "
                + "never split in half."));
        return column;
    }

    private static void send(final Activity activity, final BluetoothDevice device,
                             final EditText message, final TextView status) {
        final String text = message.getText().toString();
        if (TextLink.clean(text, TextLink.MAX_TEXT).length() == 0) {
            status.setText("Type something to send.");
            return;
        }
        status.setText("Sending...");
        final String mac;
        try {
            mac = device.getAddress();
        } catch (SecurityException e) {
            status.setText(Perms.explain(activity));
            return;
        }
        final String frame = TextLink.frame("fk2", (int) (System.currentTimeMillis() % 10000), text);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String error = se.blimark.fieldkit.SppText.send(activity, mac, frame, 8000);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(error.length() == 0
                                ? "Sent. " + TextLink.clean(text, 60)
                                : error);
                    }
                });
            }
        }).start();
    }

    // ---- share -------------------------------------------------------------

    public static View share(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "SHARE",
                "Hand the app's own data to another phone, on purpose and by your hand.", back);

        String backup = Store.backupText(activity);
        column.addView(Ui.caps(activity, "Backup contents", 13f, Ui.AMBER));
        column.addView(Ui.body(activity, backup));
        column.addView(Ui.note(activity, backup.split("\n").length + " lines, "
                + backup.length() + " characters. Plain text: it is a copy of your settings, "
                + "not a secret."));

        column.addView(Ui.button(activity, "SAVE AS A FILE", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
                pendingExport = Store.backupText(activity);
                se.blimark.fieldkit.Saf.createFile(activity,
                        se.blimark.fieldkit.Backup.fileName(stamp), "text/plain",
                        V2Activity.REQ_EXPORT);
            }
        }));
        column.addView(Ui.button(activity, "SEND IT ANYWHERE", Ui.TEAL, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "FieldKit backup");
                send.putExtra(Intent.EXTRA_TEXT, Store.backupText(activity));
                activity.startActivity(Intent.createChooser(send, "Send the backup"));
            }
        }));
        column.addView(Ui.button(activity, "RESTORE FROM A FILE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        se.blimark.fieldkit.Saf.pickFile(activity, V2Activity.REQ_IMPORT);
                    }
                }));
        column.addView(Ui.note(activity, "A restore replaces every stored value. The sealed "
                + "journal is a separate file and stays where it is."));
        return column;
    }

    static String takePendingExport() {
        String text = pendingExport;
        pendingExport = null;
        return text;
    }

    // ---- screens (wallpaper) ----------------------------------------------

    /** Paints the ICE card onto the lock screen, which is the one surface a
     *  stranger can read without the passcode. */
    public static void wallpaper(Activity activity) {
        String[] lines = Ice.lines(Store.owner(activity),
                Store.get(activity, "blood", ""), Store.get(activity, "allergies", ""),
                Store.get(activity, "meds", ""), Store.get(activity, "contact", ""));
        if (lines.length == 0) {
            android.widget.Toast.makeText(activity, "Fill in the ICE fields first",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = Math.max(720, metrics.widthPixels);
        int height = Math.max(1280, metrics.heightPixels);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Ui.BG);

        Paint grid = new Paint();
        grid.setColor(0x0DFFFFFF);
        int step = width / 12;
        for (int x = 0; x < width; x += step) {
            canvas.drawLine(x, 0, x, height, grid);
        }
        for (int y = 0; y < height; y += step) {
            canvas.drawLine(0, y, width, y, grid);
        }

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Ui.AMBER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(width * 0.075f);
        canvas.drawText("ICE", width * 0.08f, height * 0.16f, title);

        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setColor(Color.WHITE);
        body.setTextSize(width * 0.045f);
        float y = height * 0.22f;
        for (String line : lines) {
            canvas.drawText(line, width * 0.08f, y, body);
            y += body.getTextSize() * 1.6f;
        }

        Paint foot = new Paint(Paint.ANTI_ALIAS_FLAG);
        foot.setColor(Ui.DIM);
        foot.setTextSize(width * 0.03f);
        canvas.drawText("shown without unlocking on purpose", width * 0.08f, y + body.getTextSize(),
                foot);

        try {
            WallpaperManager manager = WallpaperManager.getInstance(activity);
            if (!manager.isSetWallpaperAllowed()) {
                android.widget.Toast.makeText(activity, "Android does not allow changing "
                        + "the wallpaper on this phone", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= 24) {
                // The lock screen only: the home screen keeps whatever the owner chose.
                manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
            } else {
                manager.setBitmap(bitmap);
            }
            android.widget.Toast.makeText(activity, "Lock screen updated",
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            android.widget.Toast.makeText(activity, "The wallpaper could not be set: "
                    + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            android.widget.Toast.makeText(activity, "Android refused the wallpaper change",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    public static View screens(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "SCREENS",
                "The ICE card as a lock screen, built from what Settings holds.", back);
        String[] lines = Ice.lines(Store.owner(activity),
                Store.get(activity, "blood", ""), Store.get(activity, "allergies", ""),
                Store.get(activity, "meds", ""), Store.get(activity, "contact", ""));
        column.addView(Ui.body(activity, lines.length == 0
                ? "Nothing to paint yet: the name, blood type, allergies, medicines and contact "
                + "are empty. Settings fills them."
                : Ice.summary(lines)));
        column.addView(Ui.button(activity, "PAINT IT ON THE LOCK SCREEN", Ui.ORANGE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        wallpaper(activity);
                    }
                }));
        column.addView(Ui.note(activity, Ice.MAX_LINES + " lines of at most " + Ice.MAX_LINE
                + " characters, because a lock screen cannot scroll. Long values are cut with "
                + "an ellipsis rather than wrapped."));
        return column;
    }

    // ---- library -----------------------------------------------------------

    private static final String ZIM_DIR = "/storage/emulated/0/Kiwix";

    public static final int REQ_TREE = 5004;
    private static final String PREF_TREE = "zimtree";

    /** Lets the user hand the app one folder, which is the only way in under
     *  scoped storage and the reason V1's Library tile showed nothing. */
    public static void pickFolder(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        activity.startActivityForResult(intent, REQ_TREE);
    }

    public static View library(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "LIBRARY",
                "ZIM files on this phone. Kiwix reads them; this screen counts them.", back);

        List<String> lines = fromKiwixLibrary(activity);
        String tree = Store.get(activity, PREF_TREE, "");
        if (lines.isEmpty() && tree.length() > 0) {
            lines = listTree(activity, Uri.parse(tree));
        } else if (lines.isEmpty()) {
            File[] files = new File(ZIM_DIR).listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().toLowerCase(Locale.US).endsWith(".zim")) {
                        String detail = String.format(Locale.US, "%.0f MB",
                                file.length() / 1048576.0);
                        int articles = articles(file);
                        if (articles > 0) {
                            detail += "  " + articles + " articles";
                        }
                        lines.add(file.getName() + "|" + detail);
                    }
                }
            }
        }

        if (lines.isEmpty()) {
            column.addView(Ui.note(activity, "No folder has been chosen, and this Android "
                    + "version will not let an app read " + ZIM_DIR + " by itself. Point the app "
                    + "at the folder once; it keeps the permission."));
        } else {
            for (String entry : lines) {
                String[] parts = entry.split("\\|");
                LinearLayout row = Ui.card(activity, Ui.TEAL);
                row.addView(Ui.caps(activity, parts[0].replace(".zim", ""), 12f, Ui.TEXT));
                row.addView(Ui.note(activity, parts.length > 1 ? parts[1] : ""));
                column.addView(row);
            }
            column.addView(Ui.body(activity, lines.size() + " books in this folder"));
        }

        column.addView(Ui.button(activity, lines.isEmpty() ? "CHOOSE THE KIWIX FOLDER"
                : "CHOOSE A DIFFERENT FOLDER", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFolder(activity);
            }
        }));

        final String kiwix = installed(activity, KIWIX);
        if (kiwix == null) {
            column.addView(Ui.note(activity, "Kiwix is not installed, so nothing here can be "
                    + "read yet."));
        } else {
            column.addView(Ui.button(activity, "OPEN KIWIX", Ui.GREEN, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launch = activity.getPackageManager().getLaunchIntentForPackage(kiwix);
                    if (launch != null) {
                        activity.startActivity(launch);
                    }
                }
            }));
        }
        column.addView(Ui.note(activity, "Article counts come from each file's own 80-byte "
                + "header, so a truncated download shows up as a number that cannot be right "
                + "for its size."));
        return column;
    }

    private static String line(String name, long size, int articles) {
        String detail = String.format(Locale.US, "%.0f MB", size / 1048576.0);
        if (articles > 0) {
            detail += "  " + articles + " articles";
        }
        return name + "|" + detail;
    }

    private static List<String> listTree(Activity activity, Uri treeUri) {
        List<String> out = new ArrayList<String>();
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri));
            Cursor cursor = activity.getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
            if (cursor == null) {
                return out;
            }
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (name == null || !name.toLowerCase(Locale.US).endsWith(".zim")) {
                        continue;
                    }
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            cursor.getString(2));
                    out.add(line(name, cursor.getLong(1), articles(activity, doc)));
                }
            } finally {
                cursor.close();
            }
        } catch (RuntimeException e) {
            out.add("unreadable|the folder permission was withdrawn");
        }
        return out;
    }

    /** Reads the article count through a document URI, 80 bytes at a time. */
    static int articles(Activity activity, Uri doc) {
        InputStream in = null;
        try {
            in = activity.getContentResolver().openInputStream(doc);
            byte[] header = new byte[80];
            int read = in.read(header);
            if (read < 32 || header[0] != 'Z' || header[1] != 'I' || header[2] != 'M') {
                return 0;
            }
            return (header[24] & 0xFF) | ((header[25] & 0xFF) << 8)
                    | ((header[26] & 0xFF) << 16) | ((header[27] & 0xFF) << 24);
        } catch (Exception e) {
            return 0;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }


    /**
     * Kiwix writes the books it has indexed into library.xml, with relative
     * paths. That file is the one place on this phone that already knows which
     * archives are real, so the tile reads it first and only falls back to the
     * picker when it cannot.
     */
    private static final String[] KIWIX_LIBRARY = {
            "/storage/emulated/0/Android/data/org.kiwix.kiwixmobile.standalone/files/ZIMFiles/library.xml",
            "/storage/emulated/0/Android/data/org.kiwix.kiwixmobile/files/ZIMFiles/library.xml",
    };

    private static List<String> fromKiwixLibrary(Activity activity) {
        List<String> out = new ArrayList<String>();
        for (String path : KIWIX_LIBRARY) {
            File file = new File(path);
            if (!file.canRead()) {
                continue;
            }
            String xml = readText(file);
            if (xml == null) {
                continue;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<book[^>]*?path=\"([^\"]*)\"[^>]*?title=\"([^\"]*)\"")
                    .matcher(xml);
            while (m.find()) {
                File zim = new File(file.getParentFile(), m.group(1));
                String detail = String.format(Locale.US, "%.0f MB", zim.length() / 1048576.0);
                int articles = articles(zim);
                if (articles > 0) {
                    detail += "  " + articles + " articles";
                }
                out.add(m.group(2) + "|" + detail + "  " + zim.getName());
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    private static String readText(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buffer = new byte[(int) Math.min(file.length(), 1 << 20)];
            int read = 0;
            while (read < buffer.length) {
                int step = in.read(buffer, read, buffer.length - read);
                if (step <= 0) {
                    break;
                }
                read += step;
            }
            return new String(buffer, 0, read, "UTF-8");
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }

    /** Reads articleCount out of the ZIM header without loading the archive. */
    static int articles(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] header = new byte[80];
            int read = in.read(header);
            if (read < 32 || header[0] != 'Z' || header[1] != 'I' || header[2] != 'M') {
                return 0;
            }
            return (header[24] & 0xFF) | ((header[25] & 0xFF) << 8)
                    | ((header[26] & 0xFF) << 16) | ((header[27] & 0xFF) << 24);
        } catch (IOException e) {
            return 0;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }

    static String installed(Context context, String[] candidates) {
        PackageManager manager = context.getPackageManager();
        for (String candidate : candidates) {
            try {
                manager.getPackageInfo(candidate, 0);
                return candidate;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try the next package name.
            }
        }
        return null;
    }

    // ---- journal -----------------------------------------------------------

    public static View journal(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "JOURNAL",
                "Notes sealed with AES-256-GCM. Wrong passphrase, no note.", back);

        final EditText pass = Ui.field(activity, "passphrase", "",
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText note = Ui.field(activity, "what happened, what worked",
                "", android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(4);
        final TextView status = Ui.note(activity, Store.hasJournal(activity)
                ? "A sealed journal is on this phone." : "No journal yet.");

        column.addView(pass);
        column.addView(note);
        column.addView(status);

        column.addView(Ui.button(activity, "SEAL AND SAVE", Ui.GREEN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String secret = pass.getText().toString();
                if (secret.length() < 4) {
                    status.setText("Use at least four characters. There is no recovery: this "
                            + "key exists only in your head.");
                    return;
                }
                String text = note.getText().toString();
                try {
                    Store.writeJournal(activity, Store.seal(secret, text));
                    status.setText("Sealed. " + text.length() + " characters written.");
                } catch (Exception e) {
                    status.setText("Could not seal: " + e.getMessage());
                }
            }
        }));
        column.addView(Ui.button(activity, "OPEN THE JOURNAL", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String secret = pass.getText().toString();
                try {
                    String text = Store.open(secret, Store.readJournal(activity));
                    note.setText(text);
                    status.setText("Opened. " + text.length() + " characters.");
                } catch (javax.crypto.AEADBadTagException e) {
                    status.setText("That passphrase does not open this journal.");
                } catch (Exception e) {
                    status.setText("Could not open: " + e.getMessage());
                }
            }
        }));
        column.addView(Ui.button(activity, "DELETE THE JOURNAL", Ui.RED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = Store.journalFile(activity);
                if (file.delete()) {
                    note.setText("");
                    status.setText("Deleted.");
                } else {
                    status.setText("Nothing to delete.");
                }
            }
        }));
        column.addView(Ui.note(activity, "The key is stretched from your passphrase with "
                + "PBKDF2 (60,000 rounds, 16-byte salt) and every save gets a fresh random IV. "
                + "Lose the passphrase and the notes are gone; that is what sealed means."));
        return column;
    }

    // ---- maps --------------------------------------------------------------

    public static View maps(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "MAPS",
                "Pick an .mbtiles file and this screen tells you what is really inside it.",
                back);
        column.addView(Ui.button(activity, "CHOOSE A MAP FILE", Ui.BLUE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                se.blimark.fieldkit.Saf.pickFile(activity, V2Activity.REQ_MBTILES);
            }
        }));
        column.addView(Ui.body(activity, mapsReport == null ? "" : mapsReport));
        column.addView(Ui.note(activity, "This build does not draw tiles. It opens the database, "
                + "reads the metadata and counts the tiles, which is how you find out whether the "
                + "file you carried is the one you meant to carry."));
        return column;
    }

    /** Opens a copied .mbtiles and reports its metadata and tile count. */
    public static String inspect(Activity activity, File file) {
        StringBuilder out = new StringBuilder();
        out.append(file.getName()).append('\n');
        out.append(String.format(Locale.US, "%.1f MB%n", file.length() / 1048576.0));
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            Cursor meta = database.rawQuery("select name, value from metadata", null);
            while (meta.moveToNext()) {
                String key = meta.getString(0);
                if ("name".equals(key) || "format".equals(key) || "minzoom".equals(key)
                        || "maxzoom".equals(key) || "bounds".equals(key) || "type".equals(key)) {
                    out.append(key).append(": ").append(meta.getString(1)).append('\n');
                }
            }
            meta.close();
            Cursor count = database.rawQuery("select count(*) from tiles", null);
            if (count.moveToFirst()) {
                out.append("tiles: ").append(count.getInt(0)).append('\n');
            }
            count.close();
            out.append("readable: yes");
        } catch (RuntimeException e) {
            out.append("readable: no - ").append(e.getMessage());
        } finally {
            if (database != null) {
                database.close();
            }
        }
        return out.toString();
    }

    // ---- comms -------------------------------------------------------------

    public static View comms(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "COMMS",
                "What can carry a message when there is no network.", back);

        final String briar = installed(activity, BRIAR);
        column.addView(Ui.caps(activity, "Briar", 13f, Ui.AMBER));
        if (briar == null) {
            column.addView(Ui.note(activity, "Briar is not installed on this phone. It is the "
                    + "mesh messenger V1 pointed at; without it this tile has nothing to open, "
                    + "and saying so is better than a button that does nothing."));
        } else {
            column.addView(Ui.body(activity, "Installed: " + briar));
            column.addView(Ui.button(activity, "OPEN BRIAR", Ui.PURPLE, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launch = activity.getPackageManager().getLaunchIntentForPackage(briar);
                    if (launch != null) {
                        activity.startActivity(launch);
                    }
                }
            }));
        }

        column.addView(Ui.caps(activity, "What this app can carry itself", 13f, Ui.AMBER));
        column.addView(Ui.body(activity,
                "Bluetooth serial: short text to a paired phone, on the Link tile.\n"
                        + "The torch and the tone: an SOS anyone can see or hear, on the SOS bar.\n"
                        + "A file: the backup, through Share, to a card or another app."));
        column.addView(Ui.note(activity, "There is no INTERNET permission in this app. Nothing "
                + "here can reach a server even if one existed."));
        return column;
    }
}
