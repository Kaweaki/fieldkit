package se.blimark.fieldkit;

/**
 * The wire format for FieldKit talking to FieldKit.
 *
 * A line is three tab-separated fields: sender id, sequence number and text.
 * Tabs and newlines are folded to spaces, so a message can never split a frame.
 * The sender id is the app's own random id, not the Bluetooth MAC, which may be
 * randomised.
 */
public final class TextLink {

    public static final int MAX_TEXT = 400;
    public static final String PREFIX = "FK2";

    private TextLink() {
    }

    public static final class Frame {
        public final String id;
        public final int sequence;
        public final String text;

        Frame(String id, int sequence, String text) {
            this.id = id;
            this.sequence = sequence;
            this.text = text;
        }

        @Override
        public String toString() {
            return id + "/" + sequence + ": " + text;
        }
    }

    public static String frame(String id, int sequence, String text) {
        return PREFIX + '\t' + clean(id, 24) + '\t' + sequence + '\t' + clean(text, MAX_TEXT);
    }

    /** Returns null for anything that is not one of our frames. */
    public static Frame parse(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.replace("\r", "").replace("\n", "");
        String[] parts = trimmed.split("\t", -1);
        if (parts.length != 4) {
            return null;
        }
        if (!PREFIX.equals(parts[0])) {
            return null;
        }
        int sequence;
        try {
            sequence = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (parts[1].length() == 0 || parts[1].length() > 24) {
            return null;
        }
        return new Frame(parts[1], sequence, parts[3]);
    }

    public static String clean(String value, int limit) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (flat.length() > limit) {
            flat = flat.substring(0, limit);
        }
        return flat;
    }
}
