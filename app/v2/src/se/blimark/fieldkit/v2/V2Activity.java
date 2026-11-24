package se.blimark.fieldkit.v2;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import se.blimark.fieldkit.Backup;
import se.blimark.fieldkit.Insets;
import se.blimark.fieldkit.Perms;
import se.blimark.fieldkit.Saf;
import se.blimark.fieldkit.Torch;

/**
 * One activity, one tile grid, no fragments and no libraries: the same shape as
 * the shipped app, rebuilt so every tile has something real behind it.
 */
public final class V2Activity extends Activity {

    static final int REQ_EXPORT = 5002;
    static final int REQ_IMPORT = 5001;
    static final int REQ_MBTILES = 5003;
    static final int REQ_TREE = Kit.REQ_TREE;

    private FrameLayout root;
    private View home;
    private View current;

    /** Which tile screen is open, so a permission answer can rebuild it in place. */
    private String openTile = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackground(Ui.background(this));
        setContentView(root);
        home = build();
        show(home);
    }

    private void show(View screen) {
        // A screen that was shown before still belongs to the old ScrollView.
        android.view.ViewParent parent = screen.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(screen);
        }
        root.removeAllViews();
        current = screen;

        final int side = Ui.dp(this, 18);
        final int top = Ui.dp(this, 14);
        final int bottom = Ui.dp(this, 24);
        final LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(side, top, side, bottom);
        pad.addView(screen);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.addView(pad);
        // The insets are added to the page padding rather than replacing it,
        // so an edge-to-edge window still keeps its margins.
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View view,
                                                                android.view.WindowInsets insets) {
                pad.setPadding(side + insets.getSystemWindowInsetLeft(),
                        top + insets.getSystemWindowInsetTop(),
                        side + insets.getSystemWindowInsetRight(),
                        bottom + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        scroll.requestApplyInsets();
        root.addView(scroll);
    }

    private void open(View screen) {
        show(screen);
    }

    private void goHome() {
        // Rebuilt each time so the header, the permission card and the footer
        // reflect whatever the last screen changed.
        openTile = "";
        home = build();
        show(home);
    }

    @Override
    public void onBackPressed() {
        if (current != null && current != home) {
            goHome();
            return;
        }
        super.onBackPressed();
    }

    // ---- home --------------------------------------------------------------

    private View build() {
        LinearLayout column = Ui.column(this);

        // V1 puts the build label above the title, in grey caps, and nothing else.
        column.addView(Ui.caps(this, "FIELDKIT 2.9", Ui.NOTE, Ui.MUTED));
        column.addView(Ui.title(this, "FIELDKIT", Ui.TITLE_HOME));
        column.addView(Ui.text(this, "offline index + tools - tap a tile", Ui.BODY, Ui.MUTED));
        column.addView(Ui.rule(this));

        column.addView(tileGrid());

        // V1 has no SOS bar: it goes under the grid so the top of the screen
        // stays exactly where the shipped app puts it.
        column.addView(sosBar());
        column.addView(permissionCard());
        column.addView(footer());
        return column;
    }

    /** The one thing on this screen that should be findable with cold hands. */
    private View sosBar() {
        LinearLayout bar = Ui.card(this, Ui.RED);
        bar.setBackground(Ui.roundRect(0xFF1B1313, Ui.RED, 24, this));
        bar.addView(Ui.caps(this, "SOS", Ui.NOTE, Ui.RED));
        bar.addView(Ui.text(this, "torch and tone in Morse, plus a message ready to send",
                Ui.BODY, Ui.TEXT));
        bar.addView(Ui.action(this, "OPEN THE DISTRESS PANEL", Ui.RED,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(Sos.screen(V2Activity.this, new Runnable() {
                            @Override
                            public void run() {
                                goHome();
                            }
                        }));
                    }
                }));
        return bar;
    }

    /**
     * The same warning V1 shows, but computed: it appears only when a permission
     * the screens need is actually missing.
     */
    private View permissionCard() {
        final String missing = Perms.explain(this);
        if (Perms.hasRadio(this)) {
            return new View(this);
        }
        LinearLayout card = Ui.card(this, Ui.AMBER);
        card.setBackground(Ui.roundRect(0xFF16130A, Ui.AMBER, 24, this));
        card.addView(Ui.caps(this, "RADIO LOCKED", Ui.NOTE, Ui.AMBER));
        card.addView(Ui.text(this, missing, Ui.BODY, Ui.TEXT));
        card.addView(Ui.action(this, "ASK FOR IT NOW", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Perms.ensureRadio(V2Activity.this);
            }
        }));
        return card;
    }

    private View tileGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = Ui.dp(this, 6);
        grid.setLayoutParams(gridParams);

        String[][] tiles = {
                {"Profile", "body, water", "person", "BLUE"},
                {"Settings", "app + data", "gear", "GREY"},
                {"Medical", "9 protocols", "cross", "RED"},
                {"Calculator", "water, power", "calc", "AMBER"},
                {"Link", "radio, text", "link", "VIOLET"},
                {"Share", "hand it on", "share", "TEAL"},
                {"Screens", "lock screen", "screen", "ORANGE"},
                {"Library", "Kiwix books", "book", "GREEN"},
                {"Journal", "sealed notes", "pen", "TAN"},
                {"Maps", "own map files", "map", "SKY"},
                {"Comms", "Briar, beacons", "talk", "PURPLE"},
                {"Help", "what works", "question", "IVORY"}
        };

        for (int i = 0; i < tiles.length; i++) {
            final String[] tile = tiles[i];
            View view = tile(tile[0], tile[1], icon(tile[2]), colour(tile[3]));
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFor(tile[0]);
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = Ui.dp(this, 96);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            int column = i % 3;
            int row = i / 3;
            params.setMargins(column == 0 ? 0 : Ui.dp(this, 8), row == 0 ? 0 : Ui.dp(this, 8),
                    0, 0);
            view.setLayoutParams(params);
            grid.addView(view);
        }
        return grid;
    }

    private void openFor(String tile) {
        openTile = tile;
        final Runnable home = new Runnable() {
            @Override
            public void run() {
                goHome();
            }
        };
        if ("Profile".equals(tile)) {
            open(Tools.profile(this, home));
        } else if ("Settings".equals(tile)) {
            open(Tools.settings(this, home, new Runnable() {
                @Override
                public void run() {
                    open(Kit.share(V2Activity.this, new Runnable() {
                        @Override
                        public void run() {
                            goHome();
                        }
                    }));
                }
            }, new Runnable() {
                @Override
                public void run() {
                    Saf.pickFile(V2Activity.this, REQ_IMPORT);
                }
            }));
        } else if ("Medical".equals(tile)) {
            open(Tools.medical(this, home));
        } else if ("Calculator".equals(tile)) {
            open(Tools.calculator(this, home));
        } else if ("Link".equals(tile)) {
            open(Kit.link(this, home));
        } else if ("Share".equals(tile)) {
            open(Kit.share(this, home));
        } else if ("Screens".equals(tile)) {
            open(Kit.screens(this, home));
        } else if ("Library".equals(tile)) {
            open(Kit.library(this, home));
        } else if ("Journal".equals(tile)) {
            open(Kit.journal(this, home));
        } else if ("Maps".equals(tile)) {
            open(Kit.maps(this, home));
        } else if ("Comms".equals(tile)) {
            open(Kit.comms(this, home));
        } else {
            open(Tools.help(this, home));
        }
    }

    private View tile(String title, String subtitle, View icon, int colour) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 10));
        box.setBackground(Ui.roundRect(colour == Ui.RED ? Ui.RED_PANEL : Ui.PANEL, colour,
                24, this));
        box.addView(icon);
        TextView heading = Ui.text(this, title, Ui.TILE_TITLE, Ui.TEXT);
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(heading);
        TextView sub = Ui.text(this, subtitle, Ui.TILE_SUB, Ui.MUTED);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(sub);
        return box;
    }

    private View icon(String name) {
        int kind = Ui.ICON_PERSON;
        if ("gear".equals(name)) {
            kind = Ui.ICON_GEAR;
        } else if ("cross".equals(name)) {
            kind = Ui.ICON_CROSS;
        } else if ("calc".equals(name)) {
            kind = Ui.ICON_CALC;
        } else if ("link".equals(name)) {
            kind = Ui.ICON_LINK;
        } else if ("share".equals(name)) {
            kind = Ui.ICON_SHARE;
        } else if ("screen".equals(name)) {
            kind = Ui.ICON_SCREEN;
        } else if ("book".equals(name)) {
            kind = Ui.ICON_BOOK;
        } else if ("pen".equals(name)) {
            kind = Ui.ICON_PEN;
        } else if ("map".equals(name)) {
            kind = Ui.ICON_MAP;
        } else if ("talk".equals(name)) {
            kind = Ui.ICON_TALK;
        } else if ("question".equals(name)) {
            kind = Ui.ICON_QUESTION;
        }
        return Ui.icon(this, kind, Ui.DIM);
    }

    private int colour(String name) {
        if ("BLUE".equals(name)) {
            return Ui.BLUE;
        }
        if ("GREY".equals(name)) {
            return Ui.GREY;
        }
        if ("RED".equals(name)) {
            return Ui.RED;
        }
        if ("AMBER".equals(name)) {
            return Ui.AMBER;
        }
        if ("PURPLE".equals(name)) {
            return Ui.PURPLE;
        }
        if ("VIOLET".equals(name)) {
            return Ui.VIOLET;
        }
        if ("SKY".equals(name)) {
            return Ui.SKY;
        }
        if ("TEAL".equals(name)) {
            return Ui.TEAL;
        }
        if ("ORANGE".equals(name)) {
            return Ui.ORANGE;
        }
        if ("GREEN".equals(name)) {
            return Ui.GREEN;
        }
        if ("TAN".equals(name)) {
            return Ui.TAN;
        }
        return Ui.IVORY;
    }

    private View footer() {
        LinearLayout block = Ui.column(this);
        block.setPadding(0, Ui.dp(this, 14), 0, 0);
        int radio = Perms.hasRadio(this) ? 1 : 0;
        String torch = Torch.supported(this) ? "torch ready" : "no torch on this phone";
        block.addView(Ui.note(this, "Everything here works with the radio off and the cable "
                + "out. No accounts, no cloud, no background services - each tile does its work "
                + "only while you are looking at it. This build asks for the radio only for the "
                + "Link tile, and it has no INTERNET permission at all."));
        block.addView(Ui.note(this, radio == 1 ? "Radio permission: granted. " + torch + "."
                : "Radio permission: not granted, so Link will ask. " + torch + "."));
        return block;
    }

    // ---- results -----------------------------------------------------------

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        Uri uri = data == null ? null : data.getData();
        if (request == REQ_EXPORT) {
            if (result != RESULT_OK || uri == null) {
                return;
            }
            String text = Kit.takePendingExport();
            try {
                Saf.writeText(this, uri, text == null ? "" : text);
                toast("Backup written.");
            } catch (IOException e) {
                toast("Could not write: " + e.getMessage());
            }
            return;
        }
        if (request == REQ_IMPORT) {
            if (result != RESULT_OK || uri == null) {
                return;
            }
            try {
                String text = read(uri);
                int count = Store.restoreText(this, text);
                home = build();
                toast("Restored " + count + " values.");
                goHome();
            } catch (IllegalArgumentException e) {
                toast("That file is not a FieldKit backup.");
            } catch (IOException e) {
                toast("Could not read: " + e.getMessage());
            }
            return;
        }
        if (request == REQ_TREE) {
            if (result != RESULT_OK || uri == null) {
                return;
            }
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some providers keep the grant without persisting it.
            }
            Store.put(this, "zimtree", uri.toString());
            open(Kit.library(this, new Runnable() {
                @Override
                public void run() {
                    goHome();
                }
            }));
            return;
        }
        if (request == REQ_MBTILES) {
            if (result != RESULT_OK || uri == null) {
                return;
            }
            try {
                String picked = Saf.displayName(this, uri);
                File copy = Saf.copyInto(this, uri, picked == null ? "map.mbtiles" : picked);
                Kit.mapsReport = Kit.inspect(this, copy);
            } catch (IOException e) {
                Kit.mapsReport = "Could not copy the file: " + e.getMessage();
            }
            home = build();
            open(Kit.maps(this, new Runnable() {
                @Override
                public void run() {
                    goHome();
                }
            }));
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == Perms.REQ_RADIO) {
            // Answer in place: on Android 12+ the request usually comes from the
            // Link screen, and dropping the owner back to the grid loses their
            // place and hides the answer they just gave.
            if ("Link".equals(openTile)) {
                open(Kit.link(this, new Runnable() {
                    @Override
                    public void run() {
                        goHome();
                    }
                }));
            } else {
                goHome();
            }
            toast(Perms.hasRadio(this) ? "Radio granted." : "Radio still refused.");
        }
    }

    private String read(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("the picker returned no stream");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
}
