package se.blimark.fieldkit;

import java.util.List;
import java.util.Locale;

/**
 * Coordinates and the GPX file they go into.
 *
 * No map, no tiles, no network: this is the arithmetic behind a crosshair and a
 * waypoint list, which is all the phone can honestly offer without a renderer
 * and without an INTERNET permission. Everything here is plain Java so the JVM
 * self test can prove the numbers and the XML without a device.
 */
public final class Geo {

    /** Six decimals is about 11 cm, and a screen cannot show more than that. */
    private static final String DECIMALS = "%.6f";

    private Geo() {
    }

    public static boolean valid(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return false;
        }
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
    }

    /** Six decimals, with a full stop whatever the phone's locale says. */
    public static String format(double value) {
        return String.format(Locale.US, DECIMALS, value);
    }

    /** "59.329300 N, 18.068600 E" - the same text the GPX carries. */
    public static String pair(double lat, double lon) {
        return format(Math.abs(lat)) + (lat < 0 ? " S, " : " N, ")
                + format(Math.abs(lon)) + (lon < 0 ? " W" : " E");
    }

    /** A typed coordinate: full stop or comma, whitespace ignored. NaN when unreadable. */
    public static double parse(String text) {
        if (text == null) {
            return Double.NaN;
        }
        String cleaned = text.trim().replace(',', '.');
        if (cleaned.length() == 0) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * GPX 1.1 with one waypoint per row.
     *
     * Each row is {lat, lon, name, source, time} exactly as it is stored, and the
     * lat/lon strings are written unchanged so the file cannot drift from what
     * the screen showed.
     */
    public static String gpx(String name, List<String[]> points) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<gpx version=\"1.1\" creator=\"FieldKit\" ");
        out.append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        out.append("  <metadata>\n");
        out.append("    <name>").append(escape(name)).append("</name>\n");
        out.append("  </metadata>\n");
        if (points != null) {
            for (int i = 0; i < points.size(); i++) {
                String[] row = points.get(i);
                if (row == null || row.length < 2) {
                    continue;
                }
                String label = row.length > 2 && row[2] != null && row[2].length() > 0
                        ? row[2] : "point " + (i + 1);
                out.append("  <wpt lat=\"").append(row[0]).append("\" lon=\"")
                        .append(row[1]).append("\">\n");
                out.append("    <name>").append(escape(label)).append("</name>\n");
                if (row.length > 3 && row[3] != null && row[3].length() > 0) {
                    out.append("    <desc>").append(escape(row[3]));
                    if (row.length > 4 && row[4] != null && row[4].length() > 0) {
                        out.append(", ").append(escape(row[4]));
                    }
                    out.append("</desc>\n");
                }
                out.append("  </wpt>\n");
            }
        }
        out.append("</gpx>\n");
        return out.toString();
    }

    /** XML-escape, because a waypoint name is typed by a person. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.append(c);
                    break;
            }
        }
        return out.toString();
    }
}
