package se.blimark.fieldkit.v2;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import se.blimark.fieldkit.Ice;
import se.blimark.fieldkit.Torch;

/**
 * The distress signal: the torch in Morse, the same pattern in sound, the screen
 * flashing, and a message ready to hand to any other app.
 *
 * All three run off one clock, so the light and the tone stay together. Nothing
 * is sent anywhere by this screen - it is a beacon for a human who can see or
 * hear you, which works with no network, no SIM and no permission.
 */
public final class Sos {

    private static final int UNIT_MS = Torch.UNIT_MS;

    /** dot dot dot  dash dash dash  dot dot dot  then a long pause. */
    private static final int[] PATTERN = {
            1, -1, 1, -1, 1, -3,
            3, -1, 3, -1, 3, -3,
            1, -1, 1, -1, 1, -7
    };

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View flash;
    private final TextView state;
    private ToneGenerator tone;
    private boolean running;

    private Sos(Activity activity, View flash, TextView state) {
        this.activity = activity;
        this.flash = flash;
        this.state = state;
    }

    public static View screen(final Activity activity, final Runnable back) {
        LinearLayout column = Ui.column(activity);
        column.setBackground(Ui.background(activity));

        column.addView(Ui.caps(activity, "SOS", 26f, Ui.RED));
        column.addView(Ui.note(activity, "Three short, three long, three short. It is "
                + "recognised by anyone, and it needs no signal to send."));

        final View flash = new View(activity);
        flash.setBackgroundColor(0xFF1A0E10);
        LinearLayout.LayoutParams flashParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(activity, 92));
        flashParams.topMargin = Ui.dp(activity, 14);
        flash.setLayoutParams(flashParams);
        column.addView(flash);

        final TextView state = Ui.caps(activity, "IDLE", 13f, Ui.DIM);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stateParams.topMargin = Ui.dp(activity, 10);
        state.setLayoutParams(stateParams);
        column.addView(state);

        final Sos sos = new Sos(activity, flash, state);

        column.addView(Ui.button(activity, "START THE SIGNAL", Ui.RED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sos.start();
            }
        }));
        column.addView(Ui.button(activity, "STOP", Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sos.stop();
            }
        }));

        final String message = preset(activity);
        column.addView(Ui.caps(activity, "Preset message", 13f, Ui.AMBER));
        TextView preview = Ui.body(activity, message);
        preview.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));
        column.addView(preview);

        column.addView(Ui.button(activity, "COPY MESSAGE", Ui.AMBER,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ClipboardManager clipboard = (ClipboardManager)
                                activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("SOS", message));
                        state.setText("MESSAGE COPIED");
                    }
                }));
        column.addView(Ui.button(activity, "SHARE MESSAGE", Ui.AMBER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, message);
                activity.startActivity(Intent.createChooser(send, "Send the distress message"));
            }
        }));

        column.addView(Ui.note(activity, "The torch pattern needs a camera flash. The tone needs "
                + "the speaker turned up. The message needs someone else's app to send it - this "
                + "one never goes near a network."));

        TextView note = Ui.note(activity, "");
        if (!Torch.supported(activity)) {
            note.setText(Torch.unavailable(activity));
            column.addView(note);
        }

        column.addView(Ui.button(activity, "\u2039  BACK", Ui.DIM, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sos.stop();
                back.run();
            }
        }));
        return column;
    }

    /** What the owner would want a stranger, or a rescue team, to read. */
    public static String preset(Context context) {
        String name = Store.get(context, "owner", Store.NAME);
        String blood = Store.get(context, "blood", "");
        String allergies = Store.get(context, "allergies", "");
        String meds = Store.get(context, "meds", "");
        String contact = Store.get(context, "contact", "");
        StringBuilder out = new StringBuilder();
        out.append("I need help.");
        String[] lines = Ice.lines(name, blood, allergies, meds, contact);
        for (String line : lines) {
            out.append('\n').append(line);
        }
        double lat = Store.number(context, "lat", Double.NaN);
        double lon = Store.number(context, "lon", Double.NaN);
        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            out.append(String.format("%nPOSITION %.5f, %.5f", lat, lon));
        }
        return out.toString();
    }

    private void start() {
        stop();
        running = true;
        state.setText("SIGNALLING");
        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (RuntimeException e) {
            tone = null;
        }
        if (Torch.supported(activity)) {
            Torch.sos(activity);
        }
        handler.post(new Runnable() {
            private int step;

            @Override
            public void run() {
                if (!running) {
                    return;
                }
                int value = PATTERN[step % PATTERN.length];
                boolean on = value > 0;
                if (tone != null && on) {
                    tone.startTone(value > 1 ? ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                            : ToneGenerator.TONE_PROP_BEEP, Math.abs(value) * UNIT_MS);
                }
                flash.setBackgroundColor(on ? 0xFFD05050 : 0xFF1A0E10);
                step++;
                handler.postDelayed(this, Math.abs(value) * UNIT_MS);
            }
        });
    }

    private void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        Torch.stop(activity);
        if (tone != null) {
            try {
                tone.stopTone();
                tone.release();
            } catch (RuntimeException ignored) {
                // The tone generator can already be gone with the activity.
            }
            tone = null;
        }
        flash.setBackgroundColor(0xFF1A0E10);
        state.setText("IDLE");
    }
}
