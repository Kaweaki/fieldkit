package se.blimark.fieldkit;

/**
 * Water disinfection arithmetic, in the units a person actually has.
 *
 * Doses follow WHO/CDC field guidance: 2 mg/L available chlorine for clear
 * water, 4 mg/L for cloudy, 30 minutes contact. One millilitre of sodium
 * hypochlorite solution delivers (percent x 4.76) mg of available chlorine,
 * which is why two drops of 5% bleach per litre is the familiar number.
 *
 * Iodine is the 2% tincture: 5 drops per litre, 10 if cloudy, 30 minutes.
 * Boiling is one minute at a rolling boil, three above 2000 m.
 */
public final class Water {

    /** Available chlorine, mg per mL of solution. */
    public static final double CHLORINE_MG_PER_ML_PER_PERCENT = 4.76;

    /** One drop of household bleach from a dropper is about 0.05 mL. */
    public static final double ML_PER_DROP = 0.05;

    private Water() {
    }

    public static double chlorineMgPerMl(double percent) {
        return percent * CHLORINE_MG_PER_ML_PER_PERCENT;
    }

    public static double targetMgPerL(boolean cloudy) {
        return cloudy ? 4.0 : 2.0;
    }

    public static double bleachMl(double percent, double litres, boolean cloudy) {
        if (percent <= 0 || litres <= 0) {
            return 0;
        }
        return targetMgPerL(cloudy) * litres / chlorineMgPerMl(percent);
    }

    public static int bleachDrops(double percent, double litres, boolean cloudy) {
        return drops(bleachMl(percent, litres, cloudy));
    }

    public static int iodineDrops(double litres, boolean cloudy) {
        if (litres <= 0) {
            return 0;
        }
        // Already a drop count: 5 drops per litre of 2% tincture, 10 if cloudy.
        // Converting through millilitres here would divide by the drop volume twice.
        return (int) Math.ceil(litres * (cloudy ? 10.0 : 5.0) - 1e-9);
    }

    public static int boilMinutes(double altitudeMetres) {
        return altitudeMetres > 2000 ? 3 : 1;
    }

    public static int settleMinutes() {
        return 30;
    }

    /** Round up, with a tolerance so an exact multiple does not gain a drop. */
    public static int drops(double millilitres) {
        if (millilitres <= 0) {
            return 0;
        }
        return (int) Math.ceil(millilitres / ML_PER_DROP - 1e-9);
    }

    public static String doseLine(double percent, double litres, boolean cloudy) {
        int drops = bleachDrops(percent, litres, cloudy);
        return String.format("%.0f L %s: %.2f mL of %.2f%% bleach, about %d drops, "
                        + "wait %d min",
                litres, cloudy ? "cloudy" : "clear", bleachMl(percent, litres, cloudy),
                percent, drops, settleMinutes());
    }
}
