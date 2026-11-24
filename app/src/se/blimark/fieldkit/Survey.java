package se.blimark.fieldkit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A field survey: one site, one category, one count, one note - and the rules
 * that decide whether it may be saved at all.
 *
 * Everything here is plain Java so the JVM self test can prove the validation,
 * the escaping and the ordering without a phone. The storage is a single line
 * per survey with tabs folded, so a note can never split an entry in half, and
 * the list is sorted newest first on every read rather than trusted as written.
 */
public final class Survey {

    /** The categories a field team actually writes down. */
    public static final String[] CATEGORIES = {
            "water", "food", "shelter", "medical", "radio", "other"
    };

    public static final int MAX_COUNT = 100000;
    public static final int MAX_NAME = 60;
    public static final int MAX_NOTE = 400;

    private Survey() {
    }

    /** One saved survey. Immutable, so a list of them can be handed around. */
    public static final class Entry {
        public final String id;
        public final long created;
        public final String name;
        public final String category;
        public final int count;
        public final String note;

        public Entry(String id, long created, String name, String category, int count,
                     String note) {
            this.id = id == null ? "" : id;
            this.created = created;
            this.name = name == null ? "" : name;
            this.category = category == null ? "" : category;
            this.count = count;
            this.note = note == null ? "" : note;
        }
    }

    /**
     * A half-typed form, kept on disk between keystrokes.
     *
     * The count is the raw text, not a number, so an empty field comes back empty
     * instead of turning into a zero the user never typed.
     */
    public static final class Draft {
        public final String id;
        public final long created;
        public final String name;
        public final String category;
        public final String countText;
        public final String note;

        public Draft(String id, long created, String name, String category, String countText,
                     String note) {
            this.id = id == null ? "" : id;
            this.created = created;
            this.name = name == null ? "" : name;
            this.category = category == null ? "" : category;
            this.countText = countText == null ? "" : countText;
            this.note = note == null ? "" : note;
        }
    }

    public static String encodeDraft(Draft draft) {
        return esc(draft.id) + "\t" + draft.created + "\t" + esc(draft.name) + "\t"
                + esc(draft.category) + "\t" + esc(draft.countText) + "\t" + esc(draft.note);
    }

    /** null when there is no usable draft on disk. */
    public static Draft decodeDraft(String line) {
        if (line == null || line.trim().length() == 0) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length != 6) {
            return null;
        }
        long created;
        try {
            created = Long.parseLong(parts[1]);
        } catch (NumberFormatException error) {
            created = 0L;
        }
        return new Draft(unesc(parts[0]), created, unesc(parts[2]), unesc(parts[3]),
                unesc(parts[4]), unesc(parts[5]));
    }

    /** "" when the name is usable, otherwise the line shown under the field. */
    public static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.length() < 2) {
            return "Name the site - at least two characters.";
        }
        if (name.length() > MAX_NAME) {
            return "Keep the name under " + MAX_NAME + " characters.";
        }
        return "";
    }

    /** "" when the count is a whole number in range, otherwise the reason. */
    public static String validateCount(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() == 0) {
            return "Enter a whole number, zero or more.";
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return "The count is a whole number - digits only.";
            }
        }
        try {
            long value = Long.parseLong(text);
            if (value < 0 || value > MAX_COUNT) {
                return "The count must be between 0 and " + MAX_COUNT + ".";
            }
        } catch (NumberFormatException error) {
            return "The count must be between 0 and " + MAX_COUNT + ".";
        }
        return "";
    }

    public static String validateNote(String raw) {
        String note = raw == null ? "" : raw.trim();
        if (note.length() > MAX_NOTE) {
            return "Keep the note under " + MAX_NOTE + " characters.";
        }
        return "";
    }

    /** The first problem in the form, or "" when it can be saved. */
    public static String validate(String name, String count, String note) {
        String error = validateName(name);
        if (error.length() > 0) {
            return error;
        }
        error = validateCount(count);
        if (error.length() > 0) {
            return error;
        }
        return validateNote(note);
    }

    public static int count(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    public static boolean isCategory(String value) {
        for (String category : CATEGORIES) {
            if (category.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** One line: id, created, name, category, count, note - tabs folded. */
    public static String encode(Entry entry) {
        return esc(entry.id) + "\t" + entry.created + "\t" + esc(entry.name) + "\t"
                + esc(entry.category) + "\t" + entry.count + "\t" + esc(entry.note);
    }

    /** null when the line is not a survey: a half-written line is skipped, not guessed. */
    public static Entry decode(String line) {
        if (line == null || line.trim().length() == 0) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length != 6) {
            return null;
        }
        long created;
        int count;
        try {
            created = Long.parseLong(parts[1]);
            count = Integer.parseInt(parts[4]);
        } catch (NumberFormatException error) {
            return null;
        }
        return new Entry(unesc(parts[0]), created, unesc(parts[2]), unesc(parts[3]), count,
                unesc(parts[5]));
    }

    /** Every usable entry, newest first; broken lines are dropped. */
    public static List<Entry> decodeAll(String blob) {
        List<Entry> out = new ArrayList<Entry>();
        if (blob == null || blob.length() == 0) {
            return out;
        }
        for (String line : blob.split("\n")) {
            Entry entry = decode(line);
            if (entry != null) {
                out.add(entry);
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.created != b.created) {
                    return a.created > b.created ? -1 : 1;
                }
                return b.id.compareTo(a.id);
            }
        });
        return out;
    }

    /** The blob with this entry added, replacing an entry with the same id. */
    public static String add(String blob, Entry entry) {
        List<Entry> entries = decodeAll(blob);
        List<Entry> kept = new ArrayList<Entry>();
        for (Entry existing : entries) {
            if (!existing.id.equals(entry.id)) {
                kept.add(existing);
            }
        }
        kept.add(entry);
        return encodeAll(kept);
    }

    public static String remove(String blob, String id) {
        List<Entry> kept = new ArrayList<Entry>();
        for (Entry existing : decodeAll(blob)) {
            if (!existing.id.equals(id)) {
                kept.add(existing);
            }
        }
        return encodeAll(kept);
    }

    public static String encodeAll(List<Entry> entries) {
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.created != b.created) {
                    return a.created > b.created ? -1 : 1;
                }
                return b.id.compareTo(a.id);
            }
        });
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(encode(entry));
        }
        return out.toString();
    }

    /** "2026-11-24 15:04" - the phone's own clock, in the list's local time. */
    public static String stamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static String unesc(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == '\\') {
                    out.append('\\');
                    i++;
                } else if (next == 't') {
                    out.append('\t');
                    i++;
                } else if (next == 'n') {
                    out.append('\n');
                    i++;
                } else if (next == 'r') {
                    out.append('\r');
                    i++;
                } else {
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
