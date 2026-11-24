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
import se.blimark.fieldkit.Perms;
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
        column.addView(Ui.title(activity, title, Ui.TITLE_DETAIL));
        if (subtitle != null && subtitle.length() > 0) {
            column.addView(Ui.text(activity, subtitle, Ui.BODY, Ui.MUTED));
        }
        column.addView(Ui.rule(activity));
        column.addView(Ui.spacer(activity, 6));
        return column;
    }

    // ---- profile -----------------------------------------------------------

    private static final String[] CONDITIONS = {
            "pregnant", "breastfeeding", "diabetes", "asthma", "epilepsy",
            "heart condition", "kidney disease", "immunosuppressed", "blood thinner",
            "mobility limit"
    };

    private static final String[] ALLERGIES = {
            "penicillin", "sulfa drugs", "nuts", "latex", "insect stings", "food"
    };

    private static final String[] WORKLOAD = {"light", "moderate", "heavy"};

    /**
     * V1's profile: who is here, what they carry, what is wrong with them. The
     * values feed the water and ORS sums and the ICE card, exactly as in the
     * shipped build, so this screen is a form and not a calculator.
     */
    public static View profile(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "PROFILE",
                "who is here - the calculators use every person", back);

        final EditText name = Ui.textField(activity, "name",
                Store.get(activity, "owner", Store.NAME));
        final EditText weight = Ui.number(activity, "body weight, kg",
                Store.get(activity, "weight", "75"));
        final EditText age = Ui.number(activity, "age in years",
                Store.get(activity, "age", ""));

        LinearLayout person = Ui.card(activity);
        person.addView(Ui.fieldBlock(activity, "Name", name));
        person.addView(Ui.fieldBlock(activity, "Body weight, kg", weight));
        person.addView(Ui.fieldBlock(activity, "Age in years - optional", age));
        column.addView(person);

        final java.util.Set<String> conditions =
                csv(Store.get(activity, "conditions", ""));
        LinearLayout conditionCard = Ui.card(activity);
        conditionCard.addView(Ui.sentenceLabel(activity, "Conditions - tap all that apply"));
        conditionCard.addView(chipGrid(activity, CONDITIONS, conditions, "conditions"));
        column.addView(conditionCard);

        final java.util.Set<String> allergies = csv(Store.get(activity, "allergies_pick", ""));
        final EditText otherAllergies = Ui.textField(activity, "other allergies",
                Store.get(activity, "allergies", ""));
        LinearLayout allergyCard = Ui.card(activity);
        allergyCard.addView(Ui.sentenceLabel(activity, "Allergies - tap all that apply"));
        allergyCard.addView(chipGrid(activity, ALLERGIES, allergies, "allergies_pick"));
        allergyCard.addView(Ui.fieldBlock(activity, "Other allergies", otherAllergies));
        column.addView(allergyCard);

        final EditText meds = Ui.textField(activity, "medicines",
                Store.get(activity, "meds", ""));
        final java.util.Set<String> load = csv(Store.get(activity, "workload", "moderate"));
        LinearLayout medCard = Ui.card(activity);
        medCard.addView(Ui.sentenceLabel(activity,
                "Regular medicines - name and where it is kept"));
        medCard.addView(meds);
        medCard.addView(Ui.sentenceLabel(activity, "Usual workload"));
        medCard.addView(chipGrid(activity, WORKLOAD, load, "workload"));
        column.addView(medCard);

        column.addView(Ui.action(activity, "SAVE THIS PROFILE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.put(activity, "owner", name.getText().toString().trim());
                Store.put(activity, "weight", trim(value(weight, 75)));
                Store.put(activity, "age", trim(value(age, 0)));
                Store.put(activity, "meds", meds.getText().toString().trim());
                String other = otherAllergies.getText().toString().trim();
                Store.put(activity, "allergies", other.length() == 0 ? join(allergies)
                        : (allergies.isEmpty() ? other : join(allergies) + ", " + other));
                if (load.isEmpty()) {
                    Store.put(activity, "workload", "moderate");
                }
                android.widget.Toast.makeText(activity, "Saved to this phone.",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        }));

        column.addView(Ui.note(activity, "The weight sizes the rehydration salts; the "
                + "workload sets the daily water figure; name, allergies and medicines build "
                + "the ICE card that Screens paints on the lock screen. Nothing here leaves "
                + "the phone."));
        return column;
    }

    // ---- settings ----------------------------------------------------------

    /**
     * V1's settings: a folded list of sections in its own order. Sections this
     * build cannot back say so in one line rather than pretending to a switch.
     */
    public static View settings(final Activity activity, final Runnable back,
                                final Runnable export, final Runnable restore) {
        LinearLayout column = screen(activity, "SETTINGS",
                "everything local, nothing leaves the phone", back);
        column.addView(Ui.note(activity,
                "tap a section to open it - folds instead of one long list"));

        final EditText owner = Ui.textField(activity, "name",
                Store.get(activity, "owner", Store.NAME));
        final EditText blood = Ui.textField(activity, "blood type",
                Store.get(activity, "blood", ""));
        final EditText contact = Ui.textField(activity, "emergency contact",
                Store.get(activity, "contact", ""));
        LinearLayout ownerBody = Ui.column(activity);
        ownerBody.addView(Ui.fieldBlock(activity, "Name", owner));
        ownerBody.addView(Ui.fieldBlock(activity, "Blood type", blood));
        ownerBody.addView(Ui.fieldBlock(activity, "Emergency contact", contact));
        ownerBody.addView(Ui.action(activity, "SAVE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.put(activity, "owner", owner.getText().toString().trim());
                Store.put(activity, "blood", blood.getText().toString().trim());
                Store.put(activity, "contact", contact.getText().toString().trim());
                android.widget.Toast.makeText(activity, "Saved to this phone.",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        }));
        accordion(activity, column, "OWNER", ownerBody);

        accordion(activity, column, "PASSCODE", body(activity,
                "There is no app lock in this build. Each journal entry is sealed on "
                        + "its own with the passphrase you type on the Journal tile, so a "
                        + "wrong passphrase opens nothing. This is a vault per entry, not a "
                        + "lock on the app."));

        LinearLayout permissionBody = Ui.column(activity);
        permissionBody.addView(Ui.note(activity, Perms.hasRadio(activity)
                ? "Radio permission: granted."
                : Perms.explain(activity)));
        if (!Perms.hasRadio(activity)) {
            permissionBody.addView(Ui.action(activity, "ASK FOR IT NOW",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Perms.ensureRadio(activity);
                        }
                    }));
        }
        accordion(activity, column, "PERMISSIONS", permissionBody);

        accordion(activity, column, "APPEARANCE", body(activity,
                "One look, the one you are reading: amber on near-black. The shipped "
                        + "build carried a theme switch; this one does not."));

        accordion(activity, column, "ADVANCED MODE", body(activity,
                "Not in this build. V1 used it to unhide the survey and the raw radio "
                        + "log; neither exists here."));

        accordion(activity, column, "ACTIVE SURVEY", body(activity,
                "Not in this build. V1 scanned wifi and Bluetooth and wrote an encrypted "
                        + "log while its screen was open. Nothing here scans in the "
                        + "background or at all."));

        accordion(activity, column, "FIELDKIT PEERS", body(activity,
                "Not in this build. V1 offered an opt-in UDP beacon on the local subnet; "
                        + "this app has no INTERNET permission and no socket."));

        accordion(activity, column, "SAVED ENTITIES", body(activity,
                "Nothing saved in this build. Notes live in the Journal, not in a list "
                        + "of people and places."));

        accordion(activity, column, "LOW BATTERY MODE", body(activity,
                "Nothing to switch: this app has no background work, no service and no "
                        + "wakelock to turn off."));

        LinearLayout sharingBody = Ui.column(activity);
        sharingBody.addView(Ui.note(activity, "The backup is your settings as plain "
                + "text, written through the system file picker. It is a copy, not a "
                + "secret, so keep it somewhere you would keep a note."));
        sharingBody.addView(Ui.button(activity, "EXPORT A BACKUP FILE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        export.run();
                    }
                }));
        sharingBody.addView(Ui.button(activity, "RESTORE FROM A BACKUP FILE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        restore.run();
                    }
                }));
        accordion(activity, column, "SHARING", sharingBody);

        accordion(activity, column, "HOME-SCREEN WIDGET", body(activity,
                "No widget in this build. V1 shipped one; this app has no widget "
                        + "provider at all."));

        accordion(activity, column, "STORED ON THE PHONE", body(activity,
                "Settings, the sealed journal and any backup you have written sit in this "
                        + "app's private folder. Nothing is stored anywhere else, and "
                        + "uninstalling removes all of it."));

        accordion(activity, column, "THIS INSTALL", body(activity,
                "FieldKit 2.9 (21), package " + activity.getPackageName() + ".\n"
                        + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                        + "), target SDK 36.\n"
                        + "No INTERNET permission. No storage permission. One runtime "
                        + "permission, the Bluetooth pair, only for the Link tile."));
        return column;
    }

    // ---- calculator --------------------------------------------------------

    /**
     * V1's calculator is a numbered list of named sums, not a wall of fields. The
     * four here are the ones this build can actually compute; each opens in place.
     */
    public static View calculator(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "CALCULATOR",
                "the sums that keep coming back", back);

        final EditText litres = Ui.number(activity, "litres", "10");
        final EditText percent = Ui.number(activity, "bleach strength, %", "5");
        final EditText cloudy = Ui.number(activity, "cloudy? 1 yes, 0 no", "0");
        final TextView clean = Ui.body(activity, "");
        LinearLayout waterBody = Ui.column(activity);
        waterBody.addView(Ui.fieldBlock(activity, "Litres to treat", litres));
        waterBody.addView(Ui.fieldBlock(activity, "Bleach strength, %", percent));
        waterBody.addView(Ui.fieldBlock(activity, "Cloudy water? 1 yes, 0 no", cloudy));
        waterBody.addView(Ui.action(activity, "BLEACH DOSE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double l = value(litres, 0);
                double p = value(percent, 5);
                boolean dirty = value(cloudy, 0) >= 1;
                if (l <= 0 || p <= 0) {
                    clean.setText("Enter litres and the bleach strength.");
                    return;
                }
                clean.setText(String.format(
                        "%.2f mL of %.0f%% bleach, about %d drops, wait %d minutes.",
                        Water.bleachMl(p, l, dirty), p,
                        Water.bleachDrops(p, l, dirty), Water.settleMinutes()));
            }
        }));
        waterBody.addView(clean);
        waterBody.addView(Ui.note(activity, "Field dose: 2 mg/L available chlorine for clear "
                + "water, 4 mg/L for cloudy, 30 minutes contact before drinking."));
        tool(activity, column, "01", "Chlorinate water", "bleach drops per litre", waterBody);

        final EditText orsWeight = Ui.number(activity, "body weight, kg",
                Store.get(activity, "weight", "75"));
        final TextView ors = Ui.body(activity, "");
        LinearLayout orsBody = Ui.column(activity);
        orsBody.addView(Ui.fieldBlock(activity, "Body weight, kg", orsWeight));
        orsBody.addView(Ui.action(activity, "ORS FOR 1 LITRE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double kg = value(orsWeight, 75);
                ors.setText(String.format("1 L water, %.1f g salt (about %.0f level "
                        + "teaspoons), %.0f g sugar (about %.0f teaspoons). Sip every few "
                        + "minutes.", 0.75 * kg / 20, 0.75 * kg / 20 / 5, 6 * kg / 20,
                        6 * kg / 20 / 4));
            }
        }));
        orsBody.addView(ors);
        tool(activity, column, "02", "Rehydration salts",
                "WHO ORS, scaled to body weight", orsBody);

        final EditText mah = Ui.number(activity, "battery, mAh", "10000");
        final EditText draw = Ui.number(activity, "draw, mA", "500");
        final EditText panel = Ui.number(activity, "solar panel, W", "20");
        final EditText sunHours = Ui.number(activity, "peak sun hours a day", "4");
        final TextView powerOut = Ui.body(activity, "");
        LinearLayout powerBody = Ui.column(activity);
        powerBody.addView(Ui.fieldBlock(activity, "Battery, mAh", mah));
        powerBody.addView(Ui.fieldBlock(activity, "Draw, mA", draw));
        powerBody.addView(Ui.fieldBlock(activity, "Solar panel, W", panel));
        powerBody.addView(Ui.fieldBlock(activity, "Peak sun hours a day", sunHours));
        powerBody.addView(Ui.action(activity, "POWER BUDGET", new View.OnClickListener() {
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
        powerBody.addView(powerOut);
        powerBody.addView(Ui.note(activity, "Both sums are estimates: 85% of a battery's "
                + "rated mAh is treated as usable through a converter, and a panel is "
                + "credited with 70% of its rating in real sun."));
        tool(activity, column, "03", "Solar sizing",
                "panel watts and battery mAh", powerBody);

        final EditText people = Ui.number(activity, "people",
                Store.get(activity, "people", "1"));
        final EditText days = Ui.number(activity, "days", Store.get(activity, "days", "14"));
        final TextView daily = Ui.body(activity, "");
        LinearLayout dailyBody = Ui.column(activity);
        dailyBody.addView(Ui.fieldBlock(activity, "People to supply", people));
        dailyBody.addView(Ui.fieldBlock(activity, "Days to cover", days));
        dailyBody.addView(Ui.action(activity, "WORK OUT THE WATER", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double persons = Math.max(1, value(people, 1));
                double span = Math.max(1, value(days, 14));
                Store.put(activity, "people", trim(persons));
                Store.put(activity, "days", trim(span));
                double perPerson = 3.0;
                String load = Store.get(activity, "workload", "moderate");
                if ("moderate".equals(load)) {
                    perPerson = 4.0;
                } else if ("heavy".equals(load)) {
                    perPerson = 5.0;
                }
                double drinking = perPerson * persons * span;
                StringBuilder out = new StringBuilder();
                out.append(String.format("%.0f L to drink over %.0f days for %.0f people "
                        + "at %.0f L each a day (%s workload).", drinking, span, persons,
                        perPerson, load));
                out.append('\n');
                out.append(String.format("About %.0f kg to carry, and %.0f mL of 5%% bleach "
                        + "to treat it all if it is cloudy.", drinking,
                        Water.bleachMl(5, drinking, true)));
                daily.setText(out.toString());
            }
        }));
        dailyBody.addView(daily);
        tool(activity, column, "04", "Daily water need",
                "litres per day, by profile", dailyBody);

        column.addView(Ui.note(activity, "The shipped build carried ten tools. These four "
                + "are the ones whose arithmetic this app can do without a map, a radio or "
                + "a library, and each one works out its number on the spot."));
        return column;
    }

    // ---- medical -----------------------------------------------------------

    /** What a person says, and what it means: V1's two-column emergency list. */
    private static final String[][] SYMPTOMS = {
            {"Bleeding that will not stop", "BLEEDING"},
            {"Not breathing, no pulse", "CPR"},
            {"Blue lips, cannot speak", "CHOKING"},
            {"Burn or scalded skin", "BURNS"},
            {"Cold, confused, shivering stopped", "HYPOTHERMIA"},
            {"Hot skin, confusion, no sweat", "HEAT STROKE"},
            {"Limb bent the wrong way", "FRACTURE"},
            {"Sunken eyes, dark urine", "DEHYDRATION"},
            {"Fever with stiff neck or rash", "FEVER"}
    };

    public static View medical(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "EMERGENCY",
                "find the line that matches, tap it, read it", "ALL TOOLS", back);

        LinearLayout list = Ui.card(activity);
        LinearLayout header = Ui.row(activity, "IF YOU SEE", "OPEN", null);
        list.addView(header);
        list.addView(Ui.divider(activity));
        for (int i = 0; i < PROTOCOLS.length; i++) {
            final String body = PROTOCOLS[i][1];
            final String[] symptom = SYMPTOMS[Math.min(i, SYMPTOMS.length - 1)];
            final LinearLayout row = Ui.row(activity, symptom[0], "\u203a " + symptom[1], null);
            final TextView text = Ui.body(activity, "");
            final LinearLayout holder = Ui.column(activity);
            holder.addView(text);
            holder.setVisibility(View.GONE);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean open = holder.getVisibility() == View.VISIBLE;
                    text.setText(body);
                    holder.setVisibility(open ? View.GONE : View.VISIBLE);
                }
            });
            list.addView(row);
            list.addView(holder);
            if (i < PROTOCOLS.length - 1) {
                list.addView(Ui.divider(activity));
            }
        }
        column.addView(list);

        String conditions = Store.get(activity, "conditions", "");
        if (conditions.length() > 0) {
            column.addView(Ui.section(activity, "on file", "Conditions", conditions));
        }

        LinearLayout ice = Ui.section(activity, "ice card",
                "What a stranger needs in ten seconds", "");
        String[] lines = Ice.lines(Store.get(activity, "owner", ""),
                Store.get(activity, "blood", ""), Store.get(activity, "allergies", ""),
                Store.get(activity, "meds", ""), Store.get(activity, "contact", ""));
        ice.addView(Ui.body(activity, lines.length == 0
                ? "Nothing filled in yet. Profile and Settings hold the fields."
                : Ice.summary(lines)));
        ice.addView(Ui.action(activity, "PUT THIS ON THE LOCK SCREEN",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Kit.wallpaper(activity);
                    }
                }));
        column.addView(ice);

        problem(column, PROTOCOLS.length + " protocols here, and the long forms are in the "
                + "medical ZIMs in Library. This is first aid, not diagnosis.");
        return column;
    }

    // ---- help --------------------------------------------------------------

    public static View help(final Activity activity, final Runnable back) {
        LinearLayout column = screen(activity, "HELP",
                "what this kit does, in plain words", back);

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

        LinearLayout device = Ui.section(activity, "this device",
                "Checked just now, not promised", "");
        device.addView(Ui.stat(activity, "android", Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")"));
        device.addView(Ui.stat(activity, "model", Build.MANUFACTURER + " " + Build.MODEL));
        device.addView(Ui.stat(activity, "torch", Torch.supported(activity)
                ? "works, no permission" : "unavailable"));
        device.addView(Ui.stat(activity, "compass", compass));
        device.addView(Ui.stat(activity, "free storage", (freeBytes / (1024 * 1024)) + " MB"));
        if (!Torch.supported(activity)) {
            device.addView(Ui.note(activity, Torch.unavailable(activity)));
        }
        column.addView(device);

        LinearLayout does = Ui.section(activity, "what this is",
                "An index and a set of sums, not a library", "");
        does.addView(Ui.body(activity,
                "Profile records who is here and computes nothing by itself.\n"
                        + "Settings stores your details and exports a plain backup.\n"
                        + "Emergency is nine protocols and your ICE card.\n"
                        + "Calculator works out water, ORS, power and daily need.\n"
                        + "Link sends a Bluetooth message to a paired phone.\n"
                        + "Share writes the backup through the system picker.\n"
                        + "Screens paints the ICE card on the lock screen.\n"
                        + "Library counts the ZIM files on this phone and opens Kiwix.\n"
                        + "Journal seals a note with AES-256-GCM under your passphrase.\n"
                        + "Maps inspects an .mbtiles file you pick.\n"
                        + "Comms opens Briar if it is installed."));
        column.addView(does);

        LinearLayout not = Ui.section(activity, "what this build does not do",
                "Said plainly, so nothing is discovered the hard way", "");
        not.addView(Ui.body(activity,
                "It cannot draw map tiles - Maps reads the file, it does not render it.\n"
                        + "It does not read ZIM articles: Kiwix owns those.\n"
                        + "No USB, no PC link and no radio survey; Link is Bluetooth only.\n"
                        + "No widget, no app lock and no background scan.\n"
                        + "No tile sends anything to a network: there is no INTERNET "
                        + "permission in this app at all.\n"
                        + "Nothing here replaces training or a hospital."));
        column.addView(not);
        return column;
    }

    // ---- shared structure --------------------------------------------------

    /** A settings section that opens in place, with V1's plus and minus. */
    static void accordion(final Activity activity, LinearLayout column, String title,
                          View body) {
        final LinearLayout card = Ui.card(activity);
        final LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(activity, 18), 0, Ui.dp(activity, 18));
        TextView name = Ui.caps(activity, title, Ui.NOTE, Ui.TEXT);
        name.setLetterSpacing(0.08f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(name);
        head.addView(Ui.caps(activity, "+", Ui.BODY, Ui.AMBER));
        card.addView(head);
        body.setVisibility(View.GONE);
        card.addView(body);
        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean open = body.getVisibility() == View.VISIBLE;
                body.setVisibility(open ? View.GONE : View.VISIBLE);
                ((TextView) head.getChildAt(1)).setText(open ? "+" : "\u2013");
            }
        });
        column.addView(card);
    }

    /** A numbered calculator row that opens its fields in place. */
    static void tool(final Activity activity, LinearLayout column, String number,
                     String name, String detail, View body) {
        final LinearLayout card = Ui.card(activity);
        card.addView(Ui.toolRow(activity, number, name, detail, null));
        body.setVisibility(View.GONE);
        card.addView(body);
        card.getChildAt(0).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                body.setVisibility(body.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            }
        });
        column.addView(card);
    }

    static View body(Activity activity, String text) {
        LinearLayout holder = Ui.column(activity);
        holder.addView(Ui.note(activity, text));
        return holder;
    }

    /** Two chips per row, V1's toggle shape, saved the moment they are tapped. */
    static View chipGrid(final Activity activity, final String[] labels,
                         final java.util.Set<String> chosen, final String key) {
        android.widget.GridLayout grid = new android.widget.GridLayout(activity);
        grid.setColumnCount(2);
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final TextView chip = Ui.chip(activity, label, chosen.contains(label), null);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (chosen.contains(label)) {
                        chosen.remove(label);
                    } else {
                        chosen.add(label);
                    }
                    boolean on = chosen.contains(label);
                    chip.setTextColor(on ? Ui.BG : Ui.TEXT);
                    chip.setBackground(Ui.roundRect(on ? Ui.AMBER : Ui.CHIP,
                            on ? Ui.AMBER : Ui.BORDER, 24, activity));
                    Store.put(activity, key, join(chosen));
                }
            });
            android.widget.GridLayout.LayoutParams params =
                    new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = Ui.dp(activity, 48);
            params.columnSpec = android.widget.GridLayout.spec(
                    android.widget.GridLayout.UNDEFINED, 1f);
            params.setMargins(i % 2 == 0 ? 0 : Ui.dp(activity, 4), Ui.dp(activity, 4), 0, 0);
            chip.setLayoutParams(params);
            grid.addView(chip);
        }
        return grid;
    }

    static java.util.Set<String> csv(String raw) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split(",")) {
            String clean = part.trim();
            if (clean.length() > 0) {
                out.add(clean);
            }
        }
        return out;
    }

    static String join(java.util.Set<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
    }

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
