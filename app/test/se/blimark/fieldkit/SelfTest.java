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
