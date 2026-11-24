package se.blimark.fieldkit;

/**
 * The words around a compass reading: which way is which, and what the sensor
 * says about its own accuracy.
 *
 * Plain Java because a heading is arithmetic, and because the self test can then
 * check the eight points and the edge cases (359 degrees is north, not "no
 * reading") without a magnetometer.
 */
public final class Compass {

    private static final String[] POINTS = {
            "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    };

    private Compass() {
    }

    /** "no reading" until the sensor answers; then the nearest of eight points. */
    public static String cardinal(float degrees) {
        if (Float.isNaN(degrees) || Float.isInfinite(degrees)) {
            return "no reading";
        }
        int index = Math.round(normalise(degrees) / 45f) % 8;
        return POINTS[index];
    }

    /** The whole degree, wrapped into 0-359. */
    public static String degrees(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "\u2014";
        }
        return Math.round(normalise(value)) % 360 + "\u00b0";
    }

    /** The line the screen prints: "147°  SE". */
    public static String line(float value) {
        return degrees(value) + "  " + cardinal(value);
    }

    /** What Android's SENSOR_STATUS_* means in a sentence. */
    public static String accuracy(int status) {
        switch (status) {
            case 3:
                return "Accuracy: high. The phone is seeing a clean magnetic field.";
            case 2:
                return "Accuracy: medium. Move away from metal and magnets for a better read.";
            case 1:
                return "Accuracy: low. Something nearby is bending the field.";
            case 0:
                return "Accuracy: unreliable. Calibrate by turning the phone in a figure eight.";
            default:
                return "Accuracy: waiting for the sensor.";
        }
    }

    /** Keeps a heading inside [0, 360) whatever the sensor hands over. */
    public static float normalise(float value) {
        float wrapped = value % 360f;
        return wrapped < 0 ? wrapped + 360f : wrapped;
    }
}
