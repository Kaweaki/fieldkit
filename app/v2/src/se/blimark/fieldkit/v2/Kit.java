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

import se.blimark.fieldkit.Geo;
import se.blimark.fieldkit.Ice;
import se.blimark.fieldkit.Perms;
import se.blimark.fieldkit.Saf;
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
    /** The cleaned JPEG waiting to be saved, and the name to offer for it. */
    static byte[] pendingPhoto;
    static String pendingPhotoName;
    static String mapsReport;

    private Kit() {
    }

    // ---- link --------------------------------------------------------------

    public static View link(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "LINK",
                "what is plugged in, what is in the air", back);

        LinearLayout usb = Ui.section(activity, "usb - otg",
                "Identify and talk to what is on the cable", "No USB cable");
        usb.addView(Ui.note(activity, "USB host is not in this build. The shipped app read "
                + "boards and serial bridges on an OTG cable; this app has no USB code at all."));
        column.addView(usb);

        LinearLayout pc = Ui.section(activity, "this pc",
                "Open a link a computer can use", "not in this build");
        pc.addView(Ui.note(activity, "The shipped app offered an ADB loopback link for a "
                + "computer at the other end of the cable. This one does not."));
        column.addView(pc);

        LinearLayout bt = Ui.section(activity, "bluetooth",
                "Paired phones, one framed line each way",
                "Classic serial, no internet needed");
        if (!Perms.hasRadio(activity)) {
            bt.addView(Ui.note(activity, Perms.explain(activity)));
            bt.addView(Ui.action(activity, "ASK FOR IT", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Perms.ensureRadio(activity);
                }
            }));
        } else {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            java.util.Set<BluetoothDevice> bonded;
            try {
                bonded = adapter == null ? new java.util.LinkedHashSet<BluetoothDevice>()
                        : adapter.getBondedDevices();
            } catch (SecurityException e) {
                bonded = new java.util.LinkedHashSet<BluetoothDevice>();
            }
            bt.addView(Ui.note(activity, bonded.isEmpty()
                    ? "Paired devices: none. Pair the other phone in Android settings, then "
                    + "come back - this screen reads the list live and never asks for a scan."
                    : "Paired devices: " + bonded.size() + ". One framed line per send."));
            final EditText message = Ui.field(activity, "message to send", "",
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            final TextView status = Ui.note(activity, "");
            bt.addView(Ui.fieldBlock(activity, "Message", message));
            boolean first = true;
            for (final BluetoothDevice device : bonded) {
                String[] described = describe(device);
                if (!first) {
                    bt.addView(Ui.divider(activity));
                }
                first = false;
                bt.addView(Ui.deviceRow(activity, described[0], described[1], Ui.AMBER,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                send(activity, device, message, status);
                            }
                        }));
            }
            bt.addView(status);
        }
        column.addView(bt);

        LinearLayout survey = Ui.section(activity, "survey - active scan",
                "Wifi and bluetooth intel, on demand", "off - not in this build");
        survey.addView(Ui.note(activity, "V1 scanned nearby networks and wrote an encrypted "
                + "log while its screen was open. This app has no scan, no log and no INTERNET "
                + "permission."));
        column.addView(survey);

        column.addView(Ui.note(activity, "Every message is one framed line: a sender id, a "
                + "sequence number and the text, with tabs and newlines folded so a message can "
                + "never split in half."));
        return column;
    }

    /**
     * A paired device's name and address, without letting a missing name or a
     * revoked permission take the whole screen down.
     */
    private static String[] describe(BluetoothDevice device) {
        String name = null;
        String address = "";
        try {
            name = device.getName();
            address = device.getAddress() == null ? "" : device.getAddress();
        } catch (SecurityException e) {
            // Read on a phone whose radio grant was revoked under the screen.
        }
        if (name == null || name.length() == 0) {
            name = "unnamed device";
        }
        return new String[]{name, address};
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
                "hand it on by your hand, or not at all", back);

        long apkBytes = new File(activity.getApplicationInfo().sourceDir).length();
        LinearLayout app = Ui.section(activity, "the whole app",
                "Pass FieldKit on as a file",
                (apkBytes / 1024) + " kB, written byte for byte");
        app.addView(Ui.note(activity, "This is the package Android installed on this phone, "
                + "read from its own path and written unchanged, so the next phone can install "
                + "the same build. It carries this build's signature - a debug one - so the "
                + "phone at the other end will ask before it takes it."));
        app.addView(Ui.button(activity, "SAVE THE APK AS A FILE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
                                .format(new Date());
                        Saf.createFile(activity, "fieldkit-" + stamp + ".apk",
                                "application/vnd.android.package-archive",
                                V2Activity.REQ_APK);
                    }
                }));
        column.addView(app);

        LinearLayout data = Ui.section(activity, "your data",
                "Settings as one plain file", "not a secret - keep it like a note");
        String backup = Store.backupText(activity);
        data.addView(Ui.body(activity, backup));
        data.addView(Ui.note(activity, backup.split("\n").length + " lines, "
                + backup.length() + " characters. Plain text: it is a copy of your settings, "
                + "not a secret."));
        data.addView(Ui.action(activity, "SAVE AS A FILE", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
                pendingExport = Store.backupText(activity);
                se.blimark.fieldkit.Saf.createFile(activity,
                        se.blimark.fieldkit.Backup.fileName(stamp), "text/plain",
                        V2Activity.REQ_EXPORT);
            }
        }));
        data.addView(Ui.button(activity, "SEND IT ANYWHERE", Ui.TEAL, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "FieldKit backup");
                send.putExtra(Intent.EXTRA_TEXT, Store.backupText(activity));
                activity.startActivity(Intent.createChooser(send, "Send the backup"));
            }
        }));
        data.addView(Ui.button(activity, "RESTORE FROM A FILE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        se.blimark.fieldkit.Saf.pickFile(activity, V2Activity.REQ_IMPORT);
                    }
                }));
        data.addView(Ui.note(activity, "A restore replaces every stored value. The sealed "
                + "journal is a separate file and stays where it is."));
        column.addView(data);

        LinearLayout metadata = Ui.section(activity, "metadata - before it leaves",
                "Strip what a photo says about you",
                pendingPhoto == null ? "no photo picked yet" : pendingPhotoName);
        metadata.addView(Ui.note(activity, pendingPhoto == null
                ? "Pick a JPEG and this writes a second copy with the EXIF, IPTC, comment and "
                + "vendor blocks cut out. The pixels and the scan data stay exactly as they "
                + "were - only whole metadata segments are dropped - and the original file is "
                + "never modified."
                : pendingPhoto.length + " bytes ready, no metadata blocks left. Save it to a "
                + "folder and the clean copy is the one that leaves."));
        metadata.addView(Ui.button(activity, pendingPhoto == null
                ? "PICK A PHOTO" : "PICK ANOTHER PHOTO", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Saf.pickFile(activity, V2Activity.REQ_PHOTO);
                    }
                }));
        if (pendingPhoto != null) {
            metadata.addView(Ui.button(activity, "SAVE THE CLEANED PHOTO", Ui.GREEN,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Saf.createFile(activity, pendingPhotoName, "image/jpeg",
                                    V2Activity.REQ_PHOTO_SAVE);
                        }
                    }));
        }
        column.addView(metadata);

        LinearLayout any = Ui.section(activity, "any file",
                "Pick a file and send it with a note", "not in this build");
        any.addView(Ui.note(activity, "V1 could attach any file from the picker to a message. "
                + "Only the backup above leaves this app, and only through the system picker."));
        column.addView(any);
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
        wallpaper(activity, Ice.lines(Store.owner(activity),
                Store.get(activity, "blood", ""), Store.get(activity, "allergies", ""),
                Store.get(activity, "meds", ""), Store.get(activity, "contact", "")));
    }

    /** Paints exactly the lines the Screens card holds, in V1's nightly-routine order. */
    public static void wallpaper(Activity activity, String[] lines) {
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
            boolean lockOnly = Build.VERSION.SDK_INT >= 24;
            if (lockOnly) {
                // The lock screen only: the home screen keeps whatever the owner chose.
                manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
            } else {
                // Before 7.0 there is no lock-only flag: this sets both surfaces.
                manager.setBitmap(bitmap);
            }
            android.widget.Toast.makeText(activity, lockOnly ? "Lock screen updated"
                            : "Wallpaper set - this Android version has no lock-only setting, "
                            + "so the home screen changed too",
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
                "the lines a stranger reads without unlocking your phone", back);

        String saved = Store.get(activity, "routine", "");
        final EditText routine = Ui.field(activity, "one thing per line", saved,
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        routine.setMinLines(5);
        if (saved.length() == 0) {
            StringBuilder prefill = new StringBuilder();
            for (String line : Ice.lines(Store.owner(activity),
                    Store.get(activity, "blood", ""), Store.get(activity, "allergies", ""),
                    Store.get(activity, "meds", ""), Store.get(activity, "contact", ""))) {
                if (prefill.length() > 0) {
                    prefill.append('\n');
                }
                prefill.append(line);
            }
            routine.setText(prefill.toString());
        }

        LinearLayout card = Ui.section(activity, "nightly routine - one thing per line",
                "What the lock screen says",
                "Name, blood type, allergies, medicines and one contact are the usual five.");
        card.addView(routine);
        card.addView(Ui.action(activity, "SET AS LOCK SCREEN", Ui.ORANGE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String text = routine.getText().toString().trim();
                        Store.put(activity, "routine", text);
                        String[] lines = Kit.lines(text);
                        if (lines.length == 0) {
                            android.widget.Toast.makeText(activity,
                                    "Write at least one line first",
                                    android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        wallpaper(activity, lines);
                    }
                }));
        column.addView(card);

        LinearLayout home = Ui.section(activity, "home screen",
                "The home screen keeps your own wallpaper", "");
        home.addView(Ui.note(activity, "V1 shipped a set of built-in home backgrounds. This "
                + "build paints the lock screen only on Android 7 and newer; before that there "
                + "is no lock-only setting, so the home screen changes too."));
        column.addView(home);

        column.addView(Ui.note(activity, Ice.MAX_LINES + " lines of at most " + Ice.MAX_LINE
                + " characters, because a lock screen cannot scroll. Long values are cut with "
                + "an ellipsis rather than wrapped."));
        return column;
    }

    /** Splits the routine box into the lines a lock screen can hold. */
    static String[] lines(String text) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.length() == 0) {
                continue;
            }
            out.add(line.length() > Ice.MAX_LINE ? line.substring(0, Ice.MAX_LINE - 1) + "\u2026"
                    : line);
            if (out.size() == Ice.MAX_LINES) {
                break;
            }
        }
        return out.toArray(new String[0]);
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
                "the books on this phone, counted rather than promised", back);

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

        final String kiwix = installed(activity, KIWIX);
        LinearLayout reader = Ui.section(activity, "kiwix",
                kiwix == null ? "Kiwix is not installed" : "Kiwix is installed",
                "Kiwix reads ZIM files. This screen only finds them and counts them.");
        reader.addView(Ui.action(activity, lines.isEmpty() ? "CHOOSE THE KIWIX FOLDER"
                : "CHOOSE A DIFFERENT FOLDER", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFolder(activity);
            }
        }));
        if (kiwix == null) {
            reader.addView(Ui.note(activity, "Nothing here can be read until Kiwix is "
                    + "installed."));
        } else {
            reader.addView(Ui.action(activity, "OPEN KIWIX", Ui.GREEN, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launch = activity.getPackageManager().getLaunchIntentForPackage(kiwix);
                    if (launch != null) {
                        activity.startActivity(launch);
                    }
                }
            }));
        }
        column.addView(reader);

        LinearLayout shelf = Ui.section(activity, "starter shelf",
                "Ten useful first pages", "not in this build");
        shelf.addView(Ui.note(activity, "V1 listed ten starter pages and starred ten starter "
                + "searches. This build cannot deep-link into Kiwix, so it cannot open a page "
                + "by name: search inside Kiwix instead."));
        column.addView(shelf);

        LinearLayout books = Ui.section(activity, "on this phone",
                lines.isEmpty() ? "No books found yet"
                        : lines.size() + (lines.size() == 1 ? " book in this folder"
                        : " books in this folder"),
                lines.isEmpty() ? "Point the app at the folder once; it keeps the permission."
                        : "Sizes and article counts are read from each file.");
        if (lines.isEmpty()) {
            books.addView(Ui.note(activity, "This Android version will not let an app read "
                    + ZIM_DIR + " by itself, so no folder means no list."));
        } else {
            for (String entry : lines) {
                String[] parts = entry.split("\\|");
                books.addView(Ui.row(activity, parts[0].replace(".zim", ""),
                        parts.length > 1 ? parts[1] : "", null));
            }
        }
        column.addView(books);

        LinearLayout favourites = Ui.section(activity, "favourites",
                "Add a favourite", "not in this build");
        favourites.addView(Ui.note(activity, "No saved articles in this build: there is no "
                + "list to star into, and nothing that could open a page by name."));
        column.addView(favourites);

        column.addView(Ui.note(activity, "Article counts come from each file's own 80-byte "
                + "header, so a truncated download shows up as a number that cannot be right "
                + "for its size."));
        return column;
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

    private static String line(String name, long size, int articles) {
        String detail = String.format(Locale.US, "%.0f MB", size / 1048576.0);
        if (articles > 0) {
            detail += "  " + articles + " articles";
        }
        return name + "|" + detail;
    }
    // ---- journal -----------------------------------------------------------

    public static View journal(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "FIELD JOURNAL",
                "notes sealed with AES-256-GCM - wrong passphrase, no note", back);

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

        LinearLayout form = Ui.section(activity, "new entry",
                "One entry, one sealed card", "The passphrase stays in your head.");
        form.addView(Ui.fieldBlock(activity, "Passphrase", pass));
        form.addView(Ui.fieldBlock(activity, "What happened, what worked", note));
        form.addView(Ui.action(activity, "SEAL AND ADD", Ui.GREEN, new View.OnClickListener() {
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
        column.addView(form);
        column.addView(status);

        LinearLayout open = Ui.section(activity, "what is stored",
                "The sealed file, opened with the same passphrase", "");
        open.addView(Ui.action(activity, "OPEN THE JOURNAL", Ui.AMBER,
                new View.OnClickListener() {
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
        open.addView(Ui.button(activity, "CLEAR THE WHOLE JOURNAL", Ui.RED,
                new View.OnClickListener() {
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
        column.addView(open);

        column.addView(Ui.note(activity, "The key is stretched from your passphrase with "
                + "PBKDF2 (60,000 rounds, 16-byte salt) and every save gets a fresh random IV. "
                + "Lose the passphrase and the notes are gone; that is what sealed means."));
        return column;
    }

    // ---- maps --------------------------------------------------------------

    private static final String FIX_LAT = "fixlat";
    private static final String FIX_LON = "fixlon";
    private static final String FIX_SOURCE = "fixsource";
    private static final String WAYPOINTS = "waypoints";
    private static String pendingGpx;

    public static View maps(final Activity activity, final Runnable back) {
        LinearLayout column = Tools.screen(activity, "MAP",
                "a crosshair, a fix you can name, and a file you can carry", back);

        double lat = Geo.parse(Store.get(activity, FIX_LAT, ""));
        double lon = Geo.parse(Store.get(activity, FIX_LON, ""));
        boolean haveFix = Geo.valid(lat, lon);
        String source = Store.get(activity, FIX_SOURCE, "nothing read yet");
        boolean radio = Perms.hasRadio(activity);

        LinearLayout where = Ui.section(activity, "position",
                haveFix ? Geo.pair(lat, lon) : "No coordinate yet",
                haveFix ? source : "read the last known fix, or type one in");
        where.addView(Ui.note(activity, haveFix
                ? "The crosshair and the GPX both use exactly this value."
                : "Every value on this screen names where it came from. Nothing here is "
                + "guessed, and nothing is looked up over a network."));
        final EditText latField = Ui.textField(activity, "latitude",
                haveFix ? Geo.format(lat) : "");
        final EditText lonField = Ui.textField(activity, "longitude",
                haveFix ? Geo.format(lon) : "");
        if (!radio) {
            where.addView(Ui.note(activity, Perms.explain(activity)));
            where.addView(Ui.button(activity, "ASK FOR IT", Ui.AMBER,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Perms.ensureRadio(activity);
                        }
                    }));
        } else {
            where.addView(Ui.action(activity, "READ THE LAST KNOWN FIX",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String outcome = readFix(activity);
                            android.widget.Toast.makeText(activity, outcome,
                                    android.widget.Toast.LENGTH_LONG).show();
                            V2Activity.showMaps(activity);
                        }
                    }));
        }
        where.addView(Ui.fieldBlock(activity, "Latitude", latField));
        where.addView(Ui.fieldBlock(activity, "Longitude", lonField));
        where.addView(Ui.button(activity, "USE THESE COORDINATES", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        double typedLat = Geo.parse(latField.getText().toString());
                        double typedLon = Geo.parse(lonField.getText().toString());
                        if (!Geo.valid(typedLat, typedLon)) {
                            android.widget.Toast.makeText(activity, "That is not a coordinate: "
                                    + "latitude -90 to 90, longitude -180 to 180.",
                                    android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        Store.put(activity, FIX_LAT, Geo.format(typedLat));
                        Store.put(activity, FIX_LON, Geo.format(typedLon));
                        Store.put(activity, FIX_SOURCE, "typed in by hand");
                        V2Activity.showMaps(activity);
                    }
                }));
        column.addView(where);

        LinearLayout cross = Ui.section(activity, "crosshair",
                haveFix ? "The point, at the centre" : "Nothing to centre on",
                "No tiles: this draws a crosshair, not a map");
        cross.addView(new CrosshairView(activity, haveFix));
        cross.addView(Ui.note(activity, haveFix
                ? Geo.pair(lat, lon) + " - " + source
                : "Set a position above and the crosshair lands on it. There is no map "
                + "renderer and no INTERNET permission, so no tile is ever fetched."));
        column.addView(cross);

        final List<String[]> points = waypoints(activity);
        LinearLayout list = Ui.section(activity, "waypoints",
                points.isEmpty() ? "No waypoints yet"
                        : points.size() + (points.size() == 1 ? " waypoint" : " waypoints"),
                "Kept in this app's store, and exported as GPX");
        if (points.isEmpty()) {
            list.addView(Ui.note(activity, "Set a position and add it here; the list is what "
                    + "the GPX export writes."));
        } else {
            for (int i = 0; i < points.size(); i++) {
                String[] row = points.get(i);
                if (i > 0) {
                    list.addView(Ui.divider(activity));
                }
                list.addView(Ui.row(activity, row[2] + "   " + row[0] + ", " + row[1],
                        i + 1 < 10 ? "0" + (i + 1) : String.valueOf(i + 1), null));
            }
        }
        final EditText name = Ui.textField(activity, "name for this point",
                "P" + (points.size() + 1));
        list.addView(Ui.fieldBlock(activity, "Name", name));
        list.addView(Ui.button(activity, "ADD THE POINT ABOVE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        double atLat = Geo.parse(Store.get(activity, FIX_LAT, ""));
                        double atLon = Geo.parse(Store.get(activity, FIX_LON, ""));
                        if (!Geo.valid(atLat, atLon)) {
                            android.widget.Toast.makeText(activity, "Set a position first.",
                                    android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        String label = name.getText().toString().trim();
                        if (label.length() == 0) {
                            label = "P" + (points.size() + 1);
                        }
                        String when = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                                .format(new Date());
                        addPoint(activity, Geo.format(atLat), Geo.format(atLon), label,
                                Store.get(activity, FIX_SOURCE, "unknown"), when);
                        V2Activity.showMaps(activity);
                    }
                }));
        if (!points.isEmpty()) {
            list.addView(Ui.button(activity, "SAVE THE WAYPOINTS AS GPX", Ui.GREEN,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pendingGpx = Geo.gpx("FieldKit waypoints", waypoints(activity));
                            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
                                    .format(new Date());
                            Saf.createFile(activity, "fieldkit-" + stamp + ".gpx",
                                    "application/gpx+xml", V2Activity.REQ_GPX);
                        }
                    }));
            list.addView(Ui.button(activity, "CLEAR THE WAYPOINTS", Ui.RED,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Store.put(activity, WAYPOINTS, "");
                            V2Activity.showMaps(activity);
                        }
                    }));
        }
        column.addView(list);

        LinearLayout file = Ui.section(activity, "map file",
                mapsReport == null ? "No map loaded" : "A map has been read", mapsReport == null
                        ? "An .mbtiles file is one database with tiles inside."
                        : "The numbers below come from the file itself.");
        file.addView(Ui.button(activity, "CHOOSE AN .MBTILES FILE", Ui.BLUE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Saf.pickFile(activity, V2Activity.REQ_MBTILES);
                    }
                }));
        file.addView(Ui.body(activity, mapsReport == null
                ? "Nothing read yet. Pick a file and this card fills in."
                : mapsReport));
        file.addView(Ui.note(activity, "No tiles are drawn: this opens the database, reads "
                + "its metadata and counts the tiles, which is how you find out whether the "
                + "file you carried is the one you meant to carry."));
        column.addView(file);
        return column;
    }

    /**
     * The newest last known fix from any enabled provider, stored under a name
     * that says where it came from. Never a default, never an estimate.
     */
    private static String readFix(Activity activity) {
        android.location.LocationManager manager =
                (android.location.LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return "This phone has no location service.";
        }
        android.location.Location best = null;
        String provider = "";
        try {
            for (String name : manager.getProviders(true)) {
                android.location.Location found = manager.getLastKnownLocation(name);
                if (found != null && (best == null || found.getTime() > best.getTime())) {
                    best = found;
                    provider = name;
                }
            }
        } catch (SecurityException e) {
            return "Location is not allowed.";
        }
        if (best == null) {
            Store.put(activity, FIX_LAT, "");
            Store.put(activity, FIX_LON, "");
            Store.put(activity, FIX_SOURCE, "no last known fix on this phone");
            return "No provider holds a last known fix yet.";
        }
        long age = Math.max(0, System.currentTimeMillis() - best.getTime()) / 1000;
        String when = age < 90 ? age + " s old" : (age / 60) + " min old";
        Store.put(activity, FIX_LAT, Geo.format(best.getLatitude()));
        Store.put(activity, FIX_LON, Geo.format(best.getLongitude()));
        Store.put(activity, FIX_SOURCE, "Android last known fix (" + provider + "), " + when
                + (best.hasAccuracy() ? ", +/- " + Math.round(best.getAccuracy()) + " m" : ""));
        return "Read a fix from " + provider + ".";
    }

    private static List<String[]> waypoints(Activity activity) {
        List<String[]> out = new ArrayList<String[]>();
        String stored = Store.get(activity, WAYPOINTS, "");
        if (stored.length() == 0) {
            return out;
        }
        for (String line : stored.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length >= 2 && Geo.valid(Geo.parse(parts[0]), Geo.parse(parts[1]))) {
                out.add(parts);
            }
        }
        return out;
    }

    private static void addPoint(Activity activity, String lat, String lon, String name,
                                 String source, String when) {
        String stored = Store.get(activity, WAYPOINTS, "");
        StringBuilder out = new StringBuilder(stored);
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(lat).append('\t').append(lon).append('\t').append(name.replace('\t', ' '))
                .append('\t').append(source.replace('\t', ' ')).append('\t').append(when);
        Store.put(activity, WAYPOINTS, out.toString());
    }

    static String takePendingGpx() {
        String text = pendingGpx;
        pendingGpx = null;
        return text;
    }

    /**
     * The crosshair: a centre mark, a ring and four ticks, drawn on the phone.
     * It is a coordinate readout, not a map, and it says so on the card.
     */
    private static final class CrosshairView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean lit;

        CrosshairView(Context context, boolean lit) {
            super(context);
            this.lit = lit;
            float density = context.getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density * 1.6f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 180));
            params.topMargin = Ui.dp(context, 12);
            setLayoutParams(params);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float width = getWidth();
            float height = getHeight();
            float cx = width / 2f;
            float cy = height / 2f;
            float radius = Math.min(width, height) / 2f - 8f;
            paint.setColor(0xFF272F3A);
            canvas.drawCircle(cx, cy, radius, paint);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
            canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI / 2 * i;
                float x = (float) (cx + Math.cos(angle) * radius);
                float y = (float) (cy + Math.sin(angle) * radius);
                canvas.drawCircle(x, y, 4f, paint);
            }
            paint.setColor(Ui.AMBER);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
            canvas.drawLine(cx - 22f, cy, cx + 22f, cy, paint);
            canvas.drawLine(cx, cy - 22f, cx, cy + 22f, paint);
            if (lit) {
                canvas.drawCircle(cx, cy, 7f, paint);
            } else {
                paint.setColor(0xFF3A4552);
                canvas.drawCircle(cx, cy, 7f, paint);
            }
        }
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
        column.addView(Ui.caps(activity, "Briar", Ui.NOTE, Ui.AMBER));
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

        column.addView(Ui.caps(activity, "What this app can carry itself", Ui.NOTE, Ui.AMBER));
        column.addView(Ui.body(activity,
                "Bluetooth serial: short text to a paired phone, on the Link tile.\n"
                        + "The torch and the tone: an SOS anyone can see or hear, on the SOS bar.\n"
                        + "A file: the backup, through Share, to a card or another app."));
        column.addView(Ui.note(activity, "There is no INTERNET permission in this app. Nothing "
                + "here can reach a server even if one existed."));
        return column;
    }
}
