package se.blimark.fieldkit;

/**
 * Sunrise and sunset with no network and no location permission: the phone's own
 * latitude and longitude are enough.
 *
 * NOAA's general solar position, accurate to about a minute outside the polar
 * circles. Everything is in minutes after midnight UTC so the caller can add its
 * own offset; there is no timezone database in an offline app.
 */
public final class Sun {

    /** The sun stays below the horizon all day. */
    public static final int POLAR_NIGHT = -1;

    /** The sun stays above the horizon all day. */
    public static final int MIDNIGHT_SUN = -2;

    private Sun() {
    }

    public static int riseUtc(int year, int month, int day, double lat, double lon) {
        return edge(year, month, day, lat, lon, true);
    }

    public static int setUtc(int year, int month, int day, double lat, double lon) {
        return edge(year, month, day, lat, lon, false);
    }

    private static int edge(int year, int month, int day, double lat, double lon,
                            boolean rising) {
        double gamma = 2.0 * Math.PI / 365.0 * (dayOfYear(year, month, day) - 1);
        double eqTime = 229.18 * (0.000075
                + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918
                - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma)
                + 0.00148 * Math.sin(3 * gamma);
        double phi = Math.toRadians(lat);
        double cosHa = Math.cos(Math.toRadians(90.833))
                / (Math.cos(phi) * Math.cos(decl))
                - Math.tan(phi) * Math.tan(decl);
        if (cosHa > 1.0) {
            return POLAR_NIGHT;
        }
        if (cosHa < -1.0) {
            return MIDNIGHT_SUN;
        }
        double ha = Math.toDegrees(Math.acos(cosHa));
        double minutes = rising
                ? 720 - 4 * (lon + ha) - eqTime
                : 720 - 4 * (lon - ha) - eqTime;
        int rounded = (int) Math.round(minutes);
        rounded = rounded % 1440;
        return rounded < 0 ? rounded + 1440 : rounded;
    }

    public static boolean isEdge(int minutes) {
        return minutes == POLAR_NIGHT || minutes == MIDNIGHT_SUN;
    }

    /** 223 -> "03:43". */
    public static String hhmm(int minutes) {
        if (isEdge(minutes)) {
            return minutes == POLAR_NIGHT ? "polar night" : "midnight sun";
        }
        int normalised = ((minutes % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", normalised / 60, normalised % 60);
    }

    /** Shift UTC minutes by a fixed offset, ignoring daylight saving. */
    public static int withOffset(int minutes, int offsetMinutes) {
        if (isEdge(minutes)) {
            return minutes;
        }
        int shifted = (minutes + offsetMinutes) % 1440;
        return shifted < 0 ? shifted + 1440 : shifted;
    }

    public static double daylightHours(int rise, int set) {
        if (isEdge(rise) || isEdge(set)) {
            return rise == MIDNIGHT_SUN ? 24.0 : 0.0;
        }
        int span = set - rise;
        if (span < 0) {
            span += 1440;
        }
        return span / 60.0;
    }

    private static int dayOfYear(int year, int month, int day) {
        int[] cumulative = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int index = month - 1;
        if (index < 0) {
            index = 0;
        }
        if (index > 11) {
            index = 11;
        }
        int result = cumulative[index] + day;
        if (month > 2 && leap(year)) {
            result++;
        }
        return result;
    }

    private static boolean leap(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }
}
