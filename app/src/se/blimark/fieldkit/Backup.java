package se.blimark.fieldkit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One file with everything the app knows, in a format that survives a copy.
 *
 * Deliberately not JSON: org.json only exists on Android, this has to run in the
 * plain-JVM test, and a hand-written parser with a version header is easier to
 * keep correct than a hand-written JSON parser. Tabs and newlines are escaped,
 * so a profile name containing either cannot corrupt the file.
 */
public final class Backup {

    public static final String HEADER = "FIELDKIT-BACKUP 1";

    private Backup() {
    }

    public static String encode(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        out.append(HEADER).append('\n');
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                out.append(escape(entry.getKey()))
                        .append('\t')
                        .append(escape(entry.getValue()))
                        .append('\n');
            }
        }
        return out.toString();
    }

    /** Throws IllegalArgumentException when the text is not a FieldKit backup. */
    public static Map<String, String> decode(String text) {
        if (text == null) {
            throw new IllegalArgumentException("no text");
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) {
            throw new IllegalArgumentException("not a FieldKit backup");
        }
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab < 0) {
                throw new IllegalArgumentException("line " + (i + 1) + " has no key");
            }
            String key = unescape(line.substring(0, tab));
            String value = unescape(line.substring(tab + 1));
            if (key.length() == 0) {
                throw new IllegalArgumentException("line " + (i + 1) + " has an empty key");
            }
            out.put(key, value);
        }
        return out;
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
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

    static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i == value.length() - 1) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            if (next == 't') {
                out.append('\t');
            } else if (next == 'n') {
                out.append('\n');
            } else if (next == 'r') {
                out.append('\r');
            } else if (next == '\\') {
                out.append('\\');
            } else {
                out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    /** File name for the share sheet, dated by the caller. */
    public static String fileName(String stamp) {
        String safe = stamp == null ? "" : stamp.replaceAll("[^0-9A-Za-z-]", "");
        if (safe.length() == 0) {
            safe = "backup";
        }
        return "fieldkit-" + safe + ".txt";
    }
}
