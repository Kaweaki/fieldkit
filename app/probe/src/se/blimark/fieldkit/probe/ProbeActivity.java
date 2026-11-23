package se.blimark.fieldkit.probe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import se.blimark.fieldkit.Backup;
import se.blimark.fieldkit.Heading;
import se.blimark.fieldkit.Ice;
import se.blimark.fieldkit.Insets;
import se.blimark.fieldkit.Perms;
import se.blimark.fieldkit.Power;
import se.blimark.fieldkit.Sun;
import se.blimark.fieldkit.TextLink;
import se.blimark.fieldkit.Torch;
import se.blimark.fieldkit.Water;

/**
 * Runs every 2.7 class that can run on a phone and writes the result to logcat
 * as KEY=value, so the outcome can be read with adb instead of by eye.
 */
public final class ProbeActivity extends Activity {

    private static final String TAG = "FieldKitProbe";

    private TextView report;
    private ScrollView scroll;
    private Heading heading;
    private boolean headingLogged;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        setContentView(scroll);

        // Exercised on a real window; the padding is logged once insets arrive.
        Insets.apply(scroll);

        report = new TextView(this);
        report.setTextSize(11f);
        report.setPadding(28, 28, 28, 28);
        column.addView(report);

        addButton(column, "TORCH 2s", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = Torch.set(ProbeActivity.this, true);
                log("torch_on", on);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        log("torch_off", Torch.set(ProbeActivity.this, false));
                        show();
                    }
                }, 2000);
            }
        });

        addButton(column, "SOS 4s", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Torch.sos(ProbeActivity.this);
                log("sos", "started");
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Torch.stop(ProbeActivity.this);
                        log("sos", "stopped");
                    }
                }, 4000);
            }
        });

        addButton(column, "ASK NEARBY DEVICES", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ready = Perms.ensureRadio(ProbeActivity.this);
                log("radio_ready_before_prompt", ready);
                show();
            }
        });

        addButton(column, "COMPASS 5s", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                headingLogged = false;
                heading = Heading.start(ProbeActivity.this, new Heading.Listener() {
                    @Override
                    public void onHeading(float degrees, String point, float accuracy) {
                        if (!headingLogged) {
                            headingLogged = true;
                            log("heading", point + " " + Math.round(degrees));
                        }
                    }
                });
                log("compass_started", heading != null);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (heading != null) {
                            heading.stop();
                            heading = null;
                        }
                    }
                }, 5000);
            }
        });

        addButton(column, "RECHECK", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show();
            }
        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                log("insets_top_padding", scroll.getPaddingTop());
                log("insets_bottom_padding", scroll.getPaddingBottom());
            }
        }, 800);

        show();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == Perms.REQ_RADIO) {
            for (int i = 0; i < permissions.length && i < results.length; i++) {
                log("perm_" + Perms.name(permissions[i]),
                        results[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied");
            }
            log("radio_ready_after_prompt", Perms.hasRadio(this));
            show();
        }
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private void show() {
        StringBuilder text = new StringBuilder();
        Map<String, String> values = new LinkedHashMap<String, String>();

        values.put("android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        values.put("model", Build.MODEL);
        values.put("torch_supported", String.valueOf(Torch.supported(this)));
        values.put("torch_reason", Torch.unavailable(this));
        values.put("bt_scan", granted(Manifest.permission.BLUETOOTH_SCAN));
        values.put("bt_connect", granted(Manifest.permission.BLUETOOTH_CONNECT));
        values.put("radio_ready", String.valueOf(Perms.hasRadio(this)));
        values.put("sun_london_0621", Sun.hhmm(Sun.riseUtc(2026, 6, 21, 51.5074, -0.1278))
                + " / " + Sun.hhmm(Sun.setUtc(2026, 6, 21, 51.5074, -0.1278)));
        values.put("water_5pct_10l", Water.doseLine(5, 10, true));
        values.put("power", Power.hoursLine(10000, 500));
        values.put("ice", Ice.summary(Ice.lines("Ada", "O+", "penicillin", "", "+44 7000 000000"))
                .replace("\n", " | "));

        Map<String, String> source = new LinkedHashMap<String, String>();
        source.put("people_list", "Ada|62|0|1|well\nBea|55|2|0|sick");
        source.put("owner", "a\\b");
        Map<String, String> back = Backup.decode(Backup.encode(source));
        values.put("backup_roundtrip", String.valueOf(source.equals(back)));

        TextLink.Frame frame = TextLink.parse(TextLink.frame("id1", 3, "hello there"));
        values.put("textlink", frame == null ? "parse failed"
                : frame.id + "/" + frame.sequence + "/" + frame.text);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            text.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
            log(entry.getKey(), entry.getValue());
        }
        report.setText(text.toString());
    }

    private String granted(String permission) {
        return Perms.granted(this, permission) ? "granted" : "denied";
    }

    private void log(String key, Object value) {
        Log.i(TAG, key + "=" + value);
    }
}
