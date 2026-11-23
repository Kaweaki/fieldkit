package se.blimark.fieldkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Lines for the lock-screen wallpaper.
 *
 * The wallpaper is the one surface a stranger can read without the passcode, so
 * it carries only what a responder needs and the owner chose to publish. Blank
 * fields drop out; long fields are cut rather than wrapped, because the canvas
 * draws one line at a fixed size.
 */
public final class Ice {

    public static final int MAX_LINE = 34;
    public static final int MAX_LINES = 5;

    private Ice() {
    }

    public static String[] lines(String name, String bloodType, String allergies,
                                 String medicines, String contact) {
        List<String> out = new ArrayList<String>();
        add(out, name, "ICE ");
        add(out, bloodType, "BLOOD ");
        add(out, allergies, "ALLERGY ");
        add(out, medicines, "MEDS ");
        add(out, contact, "CALL ");
        return out.toArray(new String[0]);
    }

    private static void add(List<String> out, String value, String prefix) {
        if (out.size() >= MAX_LINES) {
            return;
        }
        if (value == null) {
            return;
        }
        String clean = value.trim().replace('\n', ' ').replace('\r', ' ');
        if (clean.length() == 0) {
            return;
        }
        out.add(fit(prefix + clean, MAX_LINE));
    }

    public static String fit(String text, int width) {
        if (text == null) {
            return "";
        }
        if (text.length() <= width) {
            return text;
        }
        if (width <= 1) {
            return text.substring(0, width);
        }
        return text.substring(0, width - 1) + "\u2026";
    }

    public static String summary(String[] lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }
}
