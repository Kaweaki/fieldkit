package se.blimark.fieldkit.v2;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

import se.blimark.fieldkit.Heading;
import se.blimark.fieldkit.Ice;
import se.blimark.fieldkit.Power;
import se.blimark.fieldkit.Sun;
import se.blimark.fieldkit.Torch;
import se.blimark.fieldkit.Water;

/** Profile, settings, calculator, medical and help. */
public final class Tools {

    private Tools() {
    }

    public static LinearLayout screen(Activity activity, String title, String subtitle,
                                      final Runnable back) {
        return screen(activity, title, subtitle, "FIELDKIT", back);
    }

    /**
     * The frame every V1 screen shares: a back pill, the title in amber caps, one
     * grey line of orientation, then the amber rule.
     */
    public static LinearLayout screen(Activity activity, String title, String subtitle,
                                      String backLabel, final Runnable back) {
        LinearLayout column = Ui.column(activity);
        column.setBackground(Ui.background(activity));
        if (back != null) {
            final Runnable target = back;
            column.addView(Ui.backPill(activity, backLabel == null ? "FIELDKIT" : backLabel,
                    Ui.AMBER, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            target.run();
                        }
                    }));
        }
        column.addView(Ui.title(activity, title, 30f));
        if (subtitle != null && subtitle.length() > 0) {
            column.addView(Ui.text(activity, subtitle, 15f, Ui.MUTED));
        }
        column.addView(Ui.rule(activity));
        column.addView(Ui.spacer(activity, 6));
        return column;
    }

    // ---- profile -----------------------------------------------------------

    public static View profile(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "PROFILE", "who is here - the calculators use every person",
                back);

        final EditText weight = Ui.number(activity, "body weight, kg",
                Store.get(activity, "weight", "75"));
        final EditText people = Ui.number(activity, "people to supply",
                Store.get(activity, "people", "1"));
        final EditText days = Ui.number(activity, "days", Store.get(activity, "days", "14"));
        LinearLayout form = Ui.card(activity);
        form.addView(Ui.fieldBlock(activity, "Body weight, kg", weight));
        form.addView(Ui.fieldBlock(activity, "People to supply", people));
        form.addView(Ui.fieldBlock(activity, "Days to cover", days));
        column.addView(form);

        final TextView result = Ui.body(activity, "");
        final TextView store = Ui.note(activity, "");

        column.addView(Ui.action(activity, "WORK IT OUT", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double kg = value(weight, 75);
                double persons = Math.max(1, value(people, 1));
                double span = Math.max(1, value(days, 14));
                Store.put(activity, "weight", trim(kg));
                Store.put(activity, "people", trim(persons));
                Store.put(activity, "days", trim(span));

                // 3 L a day is the drinking minimum most guidance settles on;
                // 7.5 L per person covers drinking, cooking and washing hands.
                double drinking = 3.0 * persons * span;
                double full = 7.5 * persons * span;
                StringBuilder out = new StringBuilder();
                out.append(String.format("%.0f people for %.0f days%n", persons, span));
                out.append(String.format("drinking only: %.0f L (3 L each a day)%n", drinking));
                out.append(String.format("drinking, cooking, washing: %.0f L%n", full));
                out.append(String.format("carry weight: %.0f kg at 1 kg a litre%n", full));
                double body = kg * 0.035;
                out.append(String.format("your own daily need: about %.1f L", body));
                result.setText(out.toString());

                int drops = Water.bleachDrops(5, full, true);
                store.setText(String.format("To treat all of it with 5%% bleach: %.0f mL, "
                        + "about %d drops, wait %d minutes after mixing.%n"
                        + "Or boil: %d minute(s) at a rolling boil.",
                        Water.bleachMl(5, full, true), drops, Water.settleMinutes(),
                        Water.boilMinutes(Store.number(activity, "altitude", 0))));
            }
        }));
        column.addView(result);
        column.addView(store);

        column.addView(Ui.note(activity, "Boil time assumes ground below 2000 m. Wash the "
                + "container before filling, let treated water stand covered, and never drink "
                + "from a source you have not treated."));
        return column;
    }

    // ---- settings ----------------------------------------------------------

    public static View settings(final Activity activity, final Runnable back,
                                final Runnable export, final Runnable restore) {
        LinearLayout column = screen(activity, "SETTINGS",
                "What the app knows about you. It never leaves the phone unless you export it.",
                back);

        final EditText owner = Ui.textField(activity, "name", Store.get(activity, "owner",
                Store.NAME));
        final EditText blood = Ui.textField(activity, "blood type", Store.get(activity, "blood", ""));
        final EditText allergies = Ui.textField(activity, "allergies",
                Store.get(activity, "allergies", ""));
        final EditText meds = Ui.textField(activity, "medicines",
                Store.get(activity, "meds", ""));
        final EditText contact = Ui.textField(activity, "emergency contact",
                Store.get(activity, "contact", ""));
        final EditText lat = Ui.number(activity, "latitude, e.g. 57.7089",
                Store.get(activity, "lat", ""));
        final EditText lon = Ui.number(activity, "longitude, e.g. 11.9746",
                Store.get(activity, "lon", ""));
        final EditText offset = Ui.number(activity, "UTC offset in hours, e.g. 2",
                Store.get(activity, "utc", ""));

        LinearLayout identity = Ui.card(activity);
        identity.addView(Ui.label(activity, "identity"));
        identity.addView(Ui.fieldBlock(activity, "Name", owner));
        identity.addView(Ui.fieldBlock(activity, "Blood type", blood));
        identity.addView(Ui.fieldBlock(activity, "Allergies", allergies));
        identity.addView(Ui.fieldBlock(activity, "Medicines", meds));
        identity.addView(Ui.fieldBlock(activity, "Emergency contact", contact));
        column.addView(identity);
        column.addView(Ui.label(activity, "home position"));
        column.addView(Ui.note(activity, "Typed in once so daylight times work with the "
                + "location permission refused. Leave blank if you would rather not."));
        column.addView(lat);
        column.addView(lon);
        column.addView(offset);

        final TextView saved = Ui.note(activity, "");
        column.addView(Ui.action(activity, "SAVE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.put(activity, "owner", owner.getText().toString());
                Store.put(activity, "blood", blood.getText().toString());
                Store.put(activity, "allergies", allergies.getText().toString());
                Store.put(activity, "meds", meds.getText().toString());
                Store.put(activity, "contact", contact.getText().toString());
                Store.put(activity, "lat", lat.getText().toString());
                Store.put(activity, "lon", lon.getText().toString());
                Store.put(activity, "utc", offset.getText().toString());
                saved.setText("Saved to this phone.");
            }
        }));
        column.addView(saved);

        column.addView(Ui.caps(activity, "Data", 13f, Ui.AMBER));
        column.addView(Ui.button(activity, "EXPORT A BACKUP FILE", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export.run();
            }
        }));
        column.addView(Ui.button(activity, "RESTORE FROM A BACKUP FILE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        restore.run();
                    }
                }));

        Map<String, String> values = Store.all(activity);
        column.addView(Ui.note(activity, values.size() + " stored values. The journal is a "
                + "separate sealed file and is not part of the backup."));
        return column;
    }

    // ---- calculator --------------------------------------------------------

    public static View calculator(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "CALCULATOR",
                "Every number below is computed here, with the units you actually carry.", back);

        column.addView(Ui.label(activity, "disinfect water"));
        final EditText litres = Ui.number(activity, "litres", "10");
        final EditText percent = Ui.number(activity, "bleach strength, %", "5");
        final EditText cloudy = Ui.number(activity, "cloudy? 1 yes, 0 no", "0");
        column.addView(Ui.fieldBlock(activity, "Litres to treat", litres));
        column.addView(Ui.fieldBlock(activity, "Bleach strength, %", percent));
        column.addView(Ui.fieldBlock(activity, "Cloudy water? 1 yes, 0 no", cloudy));

        final TextView clean = Ui.body(activity, "");
        column.addView(Ui.action(activity, "BLEACH DOSE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double l = value(litres, 0);
                double p = value(percent, 5);
                boolean dirty = value(cloudy, 0) >= 1;
                if (l <= 0 || p <= 0) {
                    clean.setText("Enter litres above zero and a bleach strength.");
                    return;
                }
                StringBuilder out = new StringBuilder();
                out.append(Water.doseLine(p, l, dirty)).append('\n');
                out.append(String.format("iodine 2%%: %d drops%n", Water.iodineDrops(l, dirty)));
                out.append(String.format("boil: %d minute(s)%n", Water.boilMinutes(
                        Store.number(activity, "altitude", 0))));
                if (p > 8.5) {
                    out.append("Above 8.5% is not household bleach. Do not guess: use the "
                            + "iodine or boiling column instead.");
                }
                clean.setText(out.toString());
            }
        }));
        column.addView(clean);

        column.addView(Ui.caps(activity, "Rehydration drink", 13f, Ui.AMBER));
        final TextView ors = Ui.body(activity, "");
        column.addView(Ui.action(activity, "ORS FOR 1 LITRE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ors.setText("1 litre of treated water\n6 level teaspoons of sugar\n"
                        + "half a teaspoon of salt\nStir until dissolved. Sip, do not gulp. "
                        + "If it tastes like tears, it is right. Too salty is dangerous for "
                        + "children: use 4 teaspoons of sugar and a quarter teaspoon of salt "
                        + "under five years old.");
            }
        }));
        column.addView(ors);

        column.addView(Ui.label(activity, "power"));
        final EditText mah = Ui.number(activity, "battery, mAh", "10000");
        final EditText draw = Ui.number(activity, "draw, mA", "500");
        final EditText panel = Ui.number(activity, "solar panel, W", "20");
        final EditText sunHours = Ui.number(activity, "peak sun hours a day", "4");
        column.addView(Ui.fieldBlock(activity, "Battery, mAh", mah));
        column.addView(Ui.fieldBlock(activity, "Draw, mA", draw));
        column.addView(Ui.fieldBlock(activity, "Solar panel, W", panel));
        column.addView(Ui.fieldBlock(activity, "Peak sun hours a day", sunHours));
        final TextView powerOut = Ui.body(activity, "");
        column.addView(Ui.action(activity, "POWER BUDGET", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double capacity = value(mah, 0);
                double current = value(draw, 0);
                double watts = value(panel, 0);
                double sun = value(sunHours, 0);
                StringBuilder out = new StringBuilder();
                if (capacity > 0 && current > 0) {
                    out.append(String.format("battery: about %.1f h at %.0f mA%s",
                            Power.hours(capacity, current), current,
                            Power.hours(capacity, current) < 6 ? " - plan another source" : ""));
                    out.append('\n');
                }
                if (watts > 0 && sun > 0) {
                    out.append(String.format("solar: about %.0f Wh a day from %.0f W in %.1f h%s",
                            Power.solarWhPerDay(watts, sun), watts, sun,
                            Power.solarWhPerDay(watts, sun) < 20 ? " - keep the phone off" : ""));
                }
                powerOut.setText(out.length() == 0 ? "Enter a capacity and a draw." : out.toString());
            }
        }));
        column.addView(powerOut);
        problem(column, "16.5 Wh is about one full phone charge, so a 20 W panel in four hours "
                + "of sun is roughly two charges a day.");
        return column;
    }

    // ---- medical -----------------------------------------------------------

    private static final String[][] PROTOCOLS = {
            {"Severe bleeding", "Press hard on the wound with cloth and keep pressing for ten "
                    + "minutes without lifting to look. Add layers on top if it soaks through. "
                    + "If a limb keeps bleeding, a tourniquet 5-7 cm above the wound, tight "
                    + "until the bleeding stops, and write the time on the skin. Do not loosen "
                    + "it. Shock kills faster than blood loss looks: lie the person down, keep "
                    + "them warm, do not give food or drink."},
            {"Not breathing", "Call for help, then 30 chest compressions at 100-120 a minute, "
                    + "5-6 cm deep, on the centre of the chest, then 2 rescue breaths if you "
                    + "are trained and willing. Continue without stopping until help arrives or "
                    + "you cannot continue. If you are alone with a phone, put it on speaker "
                    + "before starting."},
            {"Choking, adult", "Five sharp blows between the shoulder blades with the heel of "
                    + "the hand, leaning them forward. If that fails, five abdominal thrusts "
                    + "just above the navel. Alternate until it clears. Unconscious: start "
                    + "compressions. Never put fingers down a throat you cannot see into."},
            {"Burns", "Cool with running water for 20 minutes, not ice, not butter, not "
                    + "toothpaste. Remove rings and watches before swelling. Cover with cling "
                    + "film or a clean, non-fluffy cloth. Do not burst blisters. Anything "
                    + "larger than the person's palm, or on face, hands, feet or joints, needs "
                    + "a hospital."},
            {"Hypothermia", "Move to shelter, remove wet clothes, insulate from the ground and "
                    + "warm the core - chest, neck, head - with your own body and dry layers. "
                    + "Warm drinks only if fully conscious. Do not rub the limbs and do not put "
                    + "them near a fire: the skin cannot feel damage at that point."},
            {"Heat stroke", "Hot skin, confusion, no sweat left: this kills within hours. Move "
                    + "to shade, remove clothing, wet the skin, fan it, put cold at neck, armpits "
                    + "and groin. Cool until they shiver. Give water only if fully alert."},
            {"Broken bone", "Do not straighten it. Support it in the position found with "
                    + "padding and a splint that includes the joint above and below. Check "
                    + "fingers or toes for colour, warmth and feeling before and after. Cold "
                    + "pack over a cloth for 20 minutes."},
            {"Dehydration", "Sunken eyes, dark urine, a pulse that races when standing. Oral "
                    + "rehydration: 1 L treated water, 6 level teaspoons of sugar, half a "
                    + "teaspoon of salt. Small sips every few minutes. A person who cannot keep "
                    + "anything down needs fluids another way - that is a hospital, not a "
                    + "field decision."},
            {"Fever in the field", "Fever with stiff neck, rash that does not fade under "
                    + "pressure, or confusion is an emergency, not a field problem. Otherwise: "
                    + "fluids, rest, and paracetamol if you have it and no reason to avoid it. "
                    + "Write down the temperature and the times - the pattern is what a "
                    + "clinician will ask for."}
    };

    public static View medical(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "EMERGENCY", "find the line that matches, tap it, read it",
                "ALL TOOLS", back);

        final TextView body = Ui.body(activity, "");
        body.setPadding(0, Ui.dp(activity, 12), 0, Ui.dp(activity, 8));

        for (final String[] protocol : PROTOCOLS) {
            column.addView(Ui.button(activity, protocol[0], Ui.RED, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    body.setText(protocol[1]);
                }
            }));
        }
        column.addView(body);

        int protocols = PROTOCOLS.length;
        column.addView(Ui.caps(activity, "ICE card", 13f, Ui.AMBER));
        String[] lines = Ice.lines(Store.get(activity, "owner", ""),
                Store.get(activity, "blood", ""), Store.get(activity, "allergies", ""),
                Store.get(activity, "meds", ""), Store.get(activity, "contact", ""));
        column.addView(Ui.body(activity, lines.length == 0
                ? "Nothing filled in yet. Settings holds the fields."
                : Ice.summary(lines)));
        column.addView(Ui.action(activity, "PUT THIS ON THE LOCK SCREEN",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Kit.wallpaper(activity);
                    }
                }));
        problem(column, protocols + " protocols here, and the full texts are the medical ZIMs "
                + "in Library. This is first aid, not diagnosis.");
        return column;
    }

    // ---- help --------------------------------------------------------------

    public static View help(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "HELP",
                "What this phone can actually do, checked just now rather than promised.", back);

        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long freeBytes = (long) stat.getAvailableBlocks() * stat.getBlockSize();
        String compass;
        Heading probe = Heading.start(activity, new Heading.Listener() {
            @Override
            public void onHeading(float degrees, String point, float accuracy) {
            }
        });
        if (probe == null) {
            compass = "no rotation vector, compass unavailable";
        } else {
            probe.stop();
            compass = "present";
        }

        column.addView(Ui.caps(activity, "This device", 13f, Ui.AMBER));
        column.addView(Ui.stat(activity, "android", Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")"));
        column.addView(Ui.stat(activity, "model", Build.MANUFACTURER + " " + Build.MODEL));
        column.addView(Ui.stat(activity, "torch", Torch.supported(activity)
                ? "works, no permission" : "unavailable"));
        column.addView(Ui.stat(activity, "compass", compass));
        column.addView(Ui.stat(activity, "free storage", (freeBytes / (1024 * 1024)) + " MB"));
        if (!Torch.supported(activity)) {
            column.addView(Ui.note(activity, Torch.unavailable(activity)));
        }

        column.addView(Ui.caps(activity, "What each tile really does", 13f, Ui.AMBER));
        column.addView(Ui.body(activity,
                "Profile computes water for your people and days.\n"
                        + "Settings stores your details and exports them as a plain file.\n"
                        + "Medical is nine protocols and your ICE card.\n"
                        + "Calculator does water, ORS and power arithmetic.\n"
                        + "Link sends a real Bluetooth message to a paired phone.\n"
                        + "Share writes the backup through the system file picker.\n"
                        + "Screens paints the ICE card onto the wallpaper.\n"
                        + "Library lists the ZIM files on this phone and opens them in Kiwix.\n"
                        + "Journal seals notes with AES-256-GCM under your passphrase.\n"
                        + "Maps inspects an .mbtiles file you pick and reports what is inside.\n"
                        + "Comms opens Briar if it is installed, and says so if it is not."));

        column.addView(Ui.caps(activity, "What this build does not do", 13f, Ui.RED));
        column.addView(Ui.body(activity,
                "It cannot render map tiles - Maps checks the file, it does not draw it.\n"
                        + "It does not read ZIM articles: Kiwix owns those.\n"
                        + "No tile sends anything to a network. There is no INTERNET permission "
                        + "in this app at all.\n"
                        + "Nothing here is a substitute for training or for a hospital."));
        return column;
    }

    // ---- shared bits -------------------------------------------------------

    static void problem(LinearLayout column, String text) {
        column.addView(Ui.note(column.getContext(), text));
    }

    static double value(EditText field, double fallback) {
        String raw = field.getText().toString().trim().replace(',', '.');
        if (raw.length() == 0) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    static EditText wholeNumber(Context context, String hint, String value) {
        return Ui.field(context, hint, value, InputType.TYPE_CLASS_NUMBER);
    }

    static String hm(int minutes) {
        return Sun.hhmm(minutes);
    }
}
