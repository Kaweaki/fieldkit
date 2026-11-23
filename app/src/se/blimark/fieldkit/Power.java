package se.blimark.fieldkit;

/** Battery and solar budget, so "how long will this last" has a number. */
public final class Power {

    /** Usable share of a battery or power bank, allowance for conversion loss. */
    public static final double USABLE = 0.85;

    /** Panel output in real light against its rated watts. */
    public static final double SOLAR_EFFICIENCY = 0.70;

    private Power() {
    }

    public static double hours(double capacityMah, double drawMa) {
        if (capacityMah <= 0 || drawMa <= 0) {
            return 0;
        }
        return capacityMah * USABLE / drawMa;
    }

    public static double charges(double bankWh, double phoneWh) {
        if (bankWh <= 0 || phoneWh <= 0) {
            return 0;
        }
        return bankWh * USABLE / phoneWh;
    }

    public static double solarWhPerDay(double panelWatts, double peakSunHours) {
        if (panelWatts <= 0 || peakSunHours <= 0) {
            return 0;
        }
        return panelWatts * peakSunHours * SOLAR_EFFICIENCY;
    }

    public static double chargeMinutes(double bankWh, double phoneWh, double chargerWatts) {
        if (bankWh <= 0 || chargerWatts <= 0) {
            return 0;
        }
        return bankWh / chargerWatts * 60.0;
    }

    public static String hoursLine(double capacityMah, double drawMa) {
        return String.format("%.0f mAh at %.0f mA: about %.1f h", capacityMah, drawMa,
                hours(capacityMah, drawMa));
    }

    public static String solarLine(double panelWatts, double peakSunHours) {
        return String.format("%.0f W panel in %.1f h of sun: about %.0f Wh a day",
                panelWatts, peakSunHours, solarWhPerDay(panelWatts, peakSunHours));
    }
}
