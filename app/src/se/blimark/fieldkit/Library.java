package se.blimark.fieldkit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What is actually on the phone: a scan of the folders field staff download
 * into, the format and size of each file, and the memory of what used to be
 * there so a deleted file is reported as gone instead of quietly vanishing.
 *
 * Plain Java on purpose. The scan walks {@link File}, the rules are string and
 * number work, and the JVM self test builds a small tree on disk to prove the
 * ordering, the skipping and the "last seen" bookkeeping without a phone.
 */
public final class Library {

    /** The folders scanned under shared storage, relative to its root. */
    public static final String[] FOLDERS = {"Download", "Documents", "FieldKit"};

    /** Ceiling on one scan: a runaway folder must not stall the screen. */
    public static final int MAX_FILES = 400;

    /** How deep below each folder the scan looks. */
    public static final int MAX_DEPTH = 3;

    /**
     * Pictures and video are not library material, and Download is full of them.
     * Everything else is listed; an extension this class does not know is still
     * listed, as OTHER, so an odd file shows up rather than disappearing.
     */
    private static final String[] NOT_MATERIAL = {
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".mp4", ".mov",
            ".mkv", ".avi", ".mp3", ".m4a", ".ogg", ".wav", ".nomedia"
    };

    private Library() {
    }

    /** One readable file. Two entries are the same file when their paths match. */
    public static final class Entry {
        public final String path;
        public final String name;
        public final long size;
        public final long modified;
        /**
         * The content:// URI the file was found under, when the platform index
         * supplied one. Scoped storage means a raw path can be listed and still
         * not be openable, so the URI travels with the entry; a scan of a real
         * directory leaves it empty and the app serves the file itself.
         */
        public final String uri;

        public Entry(String path, String name, long size, long modified) {
            this(path, name, size, modified, "");
        }

        public Entry(String path, String name, long size, long modified, String uri) {
            this.path = path == null ? "" : path;
            this.name = name == null ? "" : name;
            this.size = size;
            this.modified = modified;
            this.uri = uri == null ? "" : uri;
        }

        public String kind() {
            return Library.kind(name);
        }

        public String mime() {
            return Library.mime(name);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry && ((Entry) other).path.equals(path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }
    }

    /** PDF, EPUB, TEXT, CSV or OTHER - what the card prints above the name. */
    public static String kind(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".epub")) {
            return "EPUB";
        }
        if (lower.endsWith(".csv") || lower.endsWith(".tsv")) {
            return "CSV";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log")
                || lower.endsWith(".text")) {
            return "TEXT";
        }
        return "OTHER";
    }

    /** The MIME type used for the VIEW intent. Unknown extensions get a wildcard. */
    public static String mime(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".epub")) {
            return "application/epub+zip";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".tsv")) {
            return "text/tab-separated-values";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".text")) {
            return "text/plain";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".xml")) {
            return "text/xml";
        }
        if (lower.endsWith(".gpx")) {
            return "application/gpx+xml";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        }
        return "*/*";
    }

    /** True for the extensions that make Download noisy rather than useful. */
    public static boolean isMaterial(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.startsWith(".")) {
            return false;
        }
        for (String skip : NOT_MATERIAL) {
            if (lower.endsWith(skip)) {
                return false;
            }
        }
        return true;
    }

    /** "0 bytes", "812 bytes", "13.2 KB", "1.4 MB" - never a bare byte count. */
    public static String size(long bytes) {
        if (bytes <= 0) {
            return "0 bytes";
        }
        if (bytes < 1024) {
            return bytes + (bytes == 1 ? " byte" : " bytes");
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    /** "2026-11-24 15:04", the same stamp the survey list uses. */
    public static String stamp(long millis) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new java.util.Date(millis));
    }

    /** Every readable file under one folder, at most {@link #MAX_FILES}. */
    public static List<Entry> scanDir(File dir, int depth) {
        List<Entry> out = new ArrayList<Entry>();
        if (dir == null || depth > MAX_DEPTH) {
            return out;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return out;
        }
        for (File file : files) {
            if (out.size() >= MAX_FILES) {
                return out;
            }
            if (file.isDirectory()) {
                out.addAll(scanDir(file, depth + 1));
            } else if (file.isFile() && isMaterial(file.getName()) && file.canRead()) {
                out.add(new Entry(file.getAbsolutePath(), file.getName(), file.length(),
                        file.lastModified()));
            }
        }
        return out;
    }

    /** The scan of every folder that exists, newest first, one entry per path. */
    public static List<Entry> scan(List<String> folders) {
        List<Entry> all = new ArrayList<Entry>();
        Set<String> seen = new HashSet<String>();
        for (String folder : folders) {
            for (Entry entry : scanDir(new File(folder), 0)) {
                if (seen.add(entry.path)) {
                    all.add(entry);
                }
            }
        }
        return newestFirst(all);
    }

    /** Newest first; on the same timestamp the name decides, so the order is stable. */
    public static List<Entry> newestFirst(List<Entry> entries) {
        List<Entry> out = new ArrayList<Entry>(entries);
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.modified != b.modified) {
                    return a.modified > b.modified ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    /**
     * The memory of every file ever seen, with the ones the last scan found
     * refreshed and the rest kept. That is what lets a deleted file be reported
     * as gone instead of silently disappearing from the list.
     */
    public static String remember(String previous, List<Entry> current, long now) {
        Map<String, String> lines = new HashMap<String, String>();
        for (Entry old : decode(previous)) {
            lines.put(old.path, encodeLine(old, lastSeen(previous, old.path)));
        }
        for (Entry entry : current) {
            lines.put(entry.path, encodeLine(entry, now));
        }
        List<String> values = new ArrayList<String>(lines.values());
        Collections.sort(values);
        // A phone that keeps growing its Download folder must not grow this file
        // forever: keep the most recently seen entries and let the rest go.
        if (values.size() > 600) {
            values = values.subList(values.size() - 600, values.size());
        }
        StringBuilder out = new StringBuilder();
        for (String line : values) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    private static long lastSeen(String blob, String path) {
        for (String line : blob == null ? new String[0] : blob.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length >= 5 && unesc(parts[0]).equals(path)) {
                try {
                    return Long.parseLong(parts[4]);
                } catch (NumberFormatException error) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private static String encodeLine(Entry entry, long seen) {
        return esc(entry.path) + "\t" + entry.size + "\t" + entry.modified + "\t"
                + esc(entry.name) + "\t" + seen;
    }

    /** Every remembered file, newest first. Broken lines are skipped. */
    public static List<Entry> decode(String blob) {
        List<Entry> out = new ArrayList<Entry>();
        if (blob == null || blob.length() == 0) {
            return out;
        }
        for (String line : blob.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 5) {
                continue;
            }
            try {
                out.add(new Entry(unesc(parts[0]), unesc(parts[3]), Long.parseLong(parts[1]),
                        Long.parseLong(parts[2])));
            } catch (NumberFormatException ignored) {
                // a half-written line is skipped, never guessed
            }
        }
        return newestFirst(out);
    }

    /** Remembered files that the current scan did not find: deleted or moved. */
    public static List<Entry> gone(String remembered, List<Entry> current) {
        Set<String> now = new HashSet<String>();
        for (Entry entry : current) {
            now.add(entry.path);
        }
        List<Entry> out = new ArrayList<Entry>();
        for (Entry entry : decode(remembered)) {
            if (!now.contains(entry.path)) {
                out.add(entry);
            }
        }
        return out;
    }

    static String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\n", "\\n");
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
