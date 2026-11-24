package se.blimark.fieldkit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-JVM checks for everything that is arithmetic rather than Android.
 *
 * No JUnit, no device, no android.jar: the classes under test import nothing
 * from the platform, so `java -cp build/classes:build/test
 * se.blimark.fieldkit.SelfTest` runs anywhere a JDK does. That is the layer of
 * docs/TEST-PLAN.md that can be proven without a phone.
 *
 *   javac --release 8 -d build/classes $(find app/src -name '*.java')
 *   javac --release 8 -cp build/classes -d build/test $(find app/test -name '*.java')
 *   java -cp build/classes:build/test se.blimark.fieldkit.SelfTest
 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        sun();
        water();
        power();
        ice();
        backup();
        textLink();
        jpeg();
        geo();
        usb();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- sun

    private static void sun() {
        near("London midsummer sunrise", 223, Sun.riseUtc(2026, 6, 21, 51.5074, -0.1278), 3);
        near("London midsummer sunset", 1221, Sun.setUtc(2026, 6, 21, 51.5074, -0.1278), 3);
        near("London midwinter sunrise", 483, Sun.riseUtc(2026, 12, 21, 51.5074, -0.1278), 3);
        near("London midwinter sunset", 953, Sun.setUtc(2026, 12, 21, 51.5074, -0.1278), 3);
        near("Nairobi equinox sunrise", 217, Sun.riseUtc(2026, 3, 21, -1.2921, 36.8219), 4);
        near("Nairobi equinox sunset", 944, Sun.setUtc(2026, 3, 21, -1.2921, 36.8219), 4);
        equal("Tromso midnight sun", Sun.MIDNIGHT_SUN, Sun.riseUtc(2026, 6, 21, 69.6496, 18.9560));
        equal("Tromso polar night", Sun.POLAR_NIGHT, Sun.riseUtc(2026, 12, 21, 69.6496, 18.9560));
        equal("hhmm formats", "03:43", Sun.hhmm(223));
        equal("hhmm edge", "midnight sun", Sun.hhmm(Sun.MIDNIGHT_SUN));
        near("midsummer daylight", 16.63, Sun.daylightHours(223, 1221), 0.05);
    }

    // ---------------------------------------------------------------- water

    private static void water() {
        near("5% bleach, 1 L clear (mL)", 0.08403, Water.bleachMl(5, 1, false), 0.001);
        equal("5% bleach, 1 L clear (drops)", 2, Water.bleachDrops(5, 1, false));
        near("5% bleach, 10 L cloudy (mL)", 1.68067, Water.bleachMl(5, 10, true), 0.001);
        equal("5% bleach, 10 L cloudy (drops)", 34, Water.bleachDrops(5, 10, true));
        near("8.25% bleach, 1 L clear (mL)", 0.05093, Water.bleachMl(8.25, 1, false), 0.001);
        equal("8.25% bleach, 1 L clear (drops)", 2, Water.bleachDrops(8.25, 1, false));
        equal("6% bleach, 4 L clear (drops)", 6, Water.bleachDrops(6, 4, false));
        equal("iodine, 1 L clear", 5, Water.iodineDrops(1, false));
        equal("iodine, 1 L cloudy", 10, Water.iodineDrops(1, true));
        equal("boil at sea level", 1, Water.boilMinutes(0));
        equal("boil at 2500 m", 3, Water.boilMinutes(2500));
        equal("zero litres is not a dose", 0, Water.bleachDrops(5, 0, false));
        ok("dose line reads like a sentence", Water.doseLine(5, 10, true).contains("34 drops"));
    }

    // ---------------------------------------------------------------- power

    private static void power() {
        near("10000 mAh at 500 mA", 17.0, Power.hours(10000, 500), 0.01);
        near("5000 mAh at 1200 mA", 3.5417, Power.hours(5000, 1200), 0.01);
        near("20 W panel, 4 peak hours", 56.0, Power.solarWhPerDay(20, 4), 0.01);
        near("74 Wh bank into a 15 Wh phone", 4.1933, Power.charges(74, 15), 0.01);
        equal("no draw is not infinite", 0.0, Power.hours(5000, 0));
    }

    // ---------------------------------------------------------------- ice

    private static void ice() {
        String[] lines = Ice.lines("Ada", "O+", "", "", "+44 7000 000000");
        equal("blank fields drop out", 3, lines.length);
        ok("name keeps its prefix", lines[0].startsWith("ICE Ada"));
        ok("contact keeps its prefix", lines[2].startsWith("CALL "));
        equal("no fields, no card", 0, Ice.lines("", "", "", "", "").length);
        ok("long line is cut", Ice.fit("abcdefghij", 5).length() == 5);
        ok("summary joins with newlines", Ice.summary(lines).indexOf('\n') > 0);
        equal("lines never contain a newline", -1, Ice.lines("A\nB", "", "", "", "")[0].indexOf('\n'));
    }

    // ---------------------------------------------------------------- backup

    private static void backup() {
        Map<String, String> source = new LinkedHashMap<String, String>();
        source.put("people_list", "Ada|62|0|1|well\nBea|55|2|0|sick");
        source.put("widget_what", "party\tsummary");
        source.put("owner", "Ada \\ \"the\"");
        String text = Backup.encode(source);

        ok("header first", text.startsWith(Backup.HEADER + "\n"));
        equal("no raw tabs in a value", 3, countTabs(text));

        Map<String, String> back = Backup.decode(text);
        equal("round trip size", source.size(), back.size());
        equal("round trip newline", source.get("people_list"), back.get("people_list"));
        equal("round trip tab", source.get("widget_what"), back.get("widget_what"));
        equal("round trip backslash", source.get("owner"), back.get("owner"));

        ok("crlf accepted", Backup.decode(text.replace("\n", "\r\n")).size() == 3);
        throwsOn("garbage is rejected", "hello\nworld");
        throwsOn("wrong version is rejected", "FIELDKIT-BACKUP 2\nkey\tvalue");
        ok("empty backup round trips", Backup.decode(Backup.encode(new LinkedHashMap<String, String>())).isEmpty());
        equal("file name is sanitised", "fieldkit-2026-11-23.txt", Backup.fileName("2026-11-23"));
    }

    // ---------------------------------------------------------------- text

    private static void textLink() {
        String line = TextLink.frame("ab12", 7, "water at the north gate\tshift handed over");
        TextLink.Frame frame = TextLink.parse(line);
        ok("frame parses", frame != null);
        equal("id survives", "ab12", frame.id);
        equal("sequence survives", 7, frame.sequence);
        equal("tab folded to a space", "water at the north gate shift handed over", frame.text);

        equal("wrong prefix rejected", null, TextLink.parse("FK1\tab12\t7\thi"));
        equal("short line rejected", null, TextLink.parse("FK2\tab12\thi"));
        equal("bad sequence rejected", null, TextLink.parse("FK2\tab12\tx\thi"));
        equal("junk rejected", null, TextLink.parse("hello world"));
        equal("null rejected", null, TextLink.parse(null));
        ok("long text is cut", TextLink.parse(TextLink.frame("a", 1, repeat("x", 900))).text.length() == TextLink.MAX_TEXT);
        ok("control characters folded", TextLink.parse(TextLink.frame("a", 1, "a\nb\rc")).text.indexOf('\n') < 0);
    }

    // ---------------------------------------------------------------- jpeg

    private static void jpeg() {
        byte[] app1 = segment(0xE1, utf8("Exif\0\0GPS 59.33,18.06"));
        byte[] app0 = segment(0xE0, utf8("JFIF"));
        byte[] comment = segment(0xFE, utf8("shot in Berlin"));
        byte[] sos = segment(0xDA, new byte[0]);
        byte[] scan = new byte[]{(byte) 0xFF, 0x00, 0x12, 0x34, (byte) 0xFF, (byte) 0xD9};
        byte[] in = concat(new byte[][]{{(byte) 0xFF, (byte) 0xD8}, app1, app0, comment, sos,
                scan});
        byte[] out = Jpeg.strip(in);
        ok("jpeg still starts with SOI", out.length > 2 && (out[0] & 0xFF) == 0xFF
                && (out[1] & 0xFF) == 0xD8);
        equal("jpeg drops exactly EXIF and the comment", in.length - out.length,
                app1.length + comment.length);
        ok("jpeg keeps the JFIF block", find(out, app0) >= 0);
        ok("jpeg has no APP1 left", find(out, new byte[]{(byte) 0xFF, (byte) 0xE1}) < 0);
        ok("jpeg keeps every scan byte", find(out, scan) >= 0);
        ok("jpeg does not touch its input", in.length == app1.length + app0.length
                + comment.length + sos.length + scan.length + 2);
        try {
            Jpeg.strip(utf8("hello"));
            failed++;
            System.out.println("FAIL jpeg refuses a text file: no exception");
        } catch (IllegalArgumentException expected) {
            passed++;
            System.out.println("PASS jpeg refuses a text file");
        }
    }

    private static byte[] segment(int marker, byte[] payload) {
        byte[] out = new byte[payload.length + 4];
        out[0] = (byte) 0xFF;
        out[1] = (byte) marker;
        out[2] = (byte) ((payload.length + 2) >> 8);
        out[3] = (byte) (payload.length + 2);
        System.arraycopy(payload, 0, out, 4, payload.length);
        return out;
    }

    private static byte[] concat(byte[][] parts) {
        int total = 0;
        for (int i = 0; i < parts.length; i++) {
            total += parts[i].length;
        }
        byte[] out = new byte[total];
        int at = 0;
        for (int i = 0; i < parts.length; i++) {
            System.arraycopy(parts[i], 0, out, at, parts[i].length);
            at += parts[i].length;
        }
        return out;
    }

    private static int find(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] utf8(String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value.getBytes();
        }
    }

    // ---------------------------------------------------------------- geo

    private static void geo() {
        ok("geo accepts a real coordinate", Geo.valid(59.3293, 18.0686));
        ok("geo accepts the corners", Geo.valid(-90, 180));
        ok("geo rejects latitude 91", !Geo.valid(91, 0));
        ok("geo rejects longitude 181", !Geo.valid(0, 181));
        ok("geo rejects NaN", !Geo.valid(Double.NaN, 0));
        equal("geo formats six decimals", "59.329300", Geo.format(59.3293));
        equal("geo pair north east", "59.329300 N, 18.068600 E", Geo.pair(59.3293, 18.0686));
        equal("geo pair south west", "33.868800 S, 151.209300 W", Geo.pair(-33.8688, -151.2093));
        equal("geo parses a comma", 59.3293, Geo.parse("59,3293"));
        ok("geo refuses words", Double.isNaN(Geo.parse("hello")));
        ok("geo refuses an empty field", Double.isNaN(Geo.parse("  ")));

        java.util.List<String[]> points = new java.util.ArrayList<String[]>();
        points.add(new String[]{"59.329300", "18.068600", "camp <north> & dry",
                "typed in by hand", "2026-11-24T12:00:00Z"});
        String gpx = Geo.gpx("FieldKit waypoints", points);
        ok("gpx opens with the declaration",
                gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"));
        ok("gpx is version 1.1", gpx.contains("version=\"1.1\""));
        equal("gpx has one waypoint", 1, countOf(gpx, "<wpt "));
        ok("gpx keeps the screen's decimals",
                gpx.contains("lat=\"59.329300\" lon=\"18.068600\""));
        ok("gpx escapes the name", gpx.contains("camp &lt;north&gt; &amp; dry"));
        ok("gpx remembers the source", gpx.contains("typed in by hand"));
        ok("gpx closes", gpx.trim().endsWith("</gpx>"));
    }

    private static void usb() {
        // A hand-built descriptor blob: device, one configuration, one interface
        // with a bulk endpoint and an interrupt endpoint. CP2102 ids, 100 mA,
        // bus powered with remote wakeup.
        byte[] raw = bytes(
                18, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 64,
                0xc4, 0x10, 0x60, 0xea, 0x00, 0x01, 1, 2, 3, 1,
                9, 0x02, 32, 0, 1, 1, 0, 0xa0, 50,
                9, 0x04, 0, 0, 2, 0xff, 0, 0, 0,
                7, 0x05, 0x81, 0x02, 0x00, 0x02, 0,
                7, 0x05, 0x02, 0x03, 64, 0, 10);

        equal("usb reads a 2.0 spec version", "2.0", UsbReport.usbVersion(0x0200));
        equal("usb reads a 3.1 spec version", "3.1", UsbReport.usbVersion(0x0310));
        equal("usb names mass storage", "mass storage", UsbReport.className(0x08));
        equal("usb names HID", "human interface (HID)", UsbReport.className(0x03));
        equal("usb names vendor specific", "vendor specific", UsbReport.className(0xff));
        equal("usb knows Silicon Labs", "Silicon Labs (CP210x)", UsbReport.vendorName(0x10c4));
        equal("usb knows the CP2102", "CP2102 serial", UsbReport.productName(0x10c4, 0xea60));
        equal("usb leaves an unknown vendor blank", "", UsbReport.vendorName(0x1234));
        equal("usb converts 2 mA units", 100, UsbReport.milliamp(50, 0x0200));
        equal("usb converts 2 mA units at the maximum", 500, UsbReport.milliamp(250, 0x0200));
        equal("usb converts 8 mA units for USB 3", 400, UsbReport.milliamp(50, 0x0300));
        equal("usb says bus powered", "bus-powered, remote wakeup",
                UsbReport.configFlags(0xa0));
        equal("usb says self powered", "self-powered", UsbReport.configFlags(0xc0));

        String[] lines = UsbReport.parse(raw);
        String text = join(lines);
        ok("usb parses bcdUSB", text.contains("bcdUSB 0200 (USB 2.0)"));
        ok("usb parses the control packet size", text.contains("bMaxPacketSize0 64 bytes"));
        ok("usb parses the vendor id both ways", text.contains("idVendor 10c4 (4292)"));
        ok("usb parses the product id both ways", text.contains("idProduct ea60 (60000)"));
        ok("usb parses the string indices",
                text.contains("manufacturer 1, product 2, serial 3"));
        ok("usb parses the configuration", text.contains("configuration 1, 9 bytes"));
        ok("usb converts bMaxPower", text.contains("100 mA requested"));
        ok("usb parses the interface class", text.contains("interface 0 alt 0: vendor specific"));
        ok("usb parses the bulk endpoint",
                text.contains("endpoint 81 IN bulk, 512 bytes"));
        ok("usb parses the interrupt endpoint",
                text.contains("endpoint 02 OUT interrupt, 64 bytes, interval 10"));
        equal("usb reads both endpoints", 2, countOf(text, "endpoint "));

        byte[] cut = bytes(18, 0x01, 0x00, 0x02);
        ok("usb survives a truncated blob", UsbReport.parse(cut).length > 0);
        ok("usb survives a null blob", UsbReport.parse(null).length > 0);
        ok("usb survives a zero length blob", UsbReport.parse(new byte[0]).length > 0);

        String dump = UsbReport.hex(bytes(0x0a, 0x00, 0x41), 16);
        ok("usb dumps hex with an offset", dump.startsWith("0000  0a 00 41 "));
        ok("usb dumps the ascii column", dump.contains(".A"));
        equal("usb escapes a quote", "a\\\"b", UsbReport.escape("a\"b"));
        equal("usb escapes a newline", "a\\nb", UsbReport.escape("a\nb"));
        equal("usb escapes a backslash", "a\\\\b", UsbReport.escape("a\\b"));

        byte[] hid = bytes(0x05, 0x01, 0x09, 0x06, 0xa1, 0x01, 0xc0);
        equal("usb names the HID usage page", "0x01 (generic desktop)",
                UsbReport.hidUsages(hid));
        equal("usb names two HID pages", "0x07 (keyboard), 0x0c (consumer control)",
                UsbReport.hidUsages(bytes(0x05, 0x07, 0x05, 0x0c)));
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static String join(String[] lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    // ---------------------------------------------------------------- harness

    private static String repeat(String value, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) {
            out.append(value);
        }
        return out.toString();
    }

    private static int countTabs(String text) {
        int count = 0;
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            for (int j = 0; j < lines[i].length(); j++) {
                if (lines[i].charAt(j) == '\t') {
                    count++;
                }
            }
        }
        return count;
    }

    private static void near(String label, double expected, double actual, double tolerance) {
        if (Math.abs(expected - actual) <= tolerance) {
            passed++;
            System.out.println("PASS " + label + " (" + actual + ")");
        } else {
            failed++;
            System.out.println("FAIL " + label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void equal(String label, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (same) {
            passed++;
            System.out.println("PASS " + label);
        } else {
            failed++;
            System.out.println("FAIL " + label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void ok(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS " + label);
        } else {
            failed++;
            System.out.println("FAIL " + label);
        }
    }

    private static void throwsOn(String label, String text) {
        try {
            Backup.decode(text);
            failed++;
            System.out.println("FAIL " + label + ": no exception");
        } catch (IllegalArgumentException expected) {
            passed++;
            System.out.println("PASS " + label);
        }
    }
}
