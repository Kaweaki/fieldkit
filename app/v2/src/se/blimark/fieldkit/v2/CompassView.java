package se.blimark.fieldkit.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * The compass rose: a ring, a tick every fifteen degrees, and one needle that
 * follows the phone's rotation vector.
 *
 * The sensor is registered when the view is attached and unregistered when it is
 * not, so nothing runs while another screen is open - the same rule the rest of
 * the app follows. The reading is magnetic north, not true north: the difference
 * is the local declination, which needs a location fix and is not pretended here.
 */
public final class CompassView extends View implements SensorEventListener {

    /** What the screen prints under the rose. */
    public interface Heading {
        void onHeading(float degrees, int accuracy, boolean sensorPresent);
    }

    private final SensorManager sensors;
    private final Sensor rotation;
    private final float[] matrix = new float[9];
    private final float[] orientation = new float[3];
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint north = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint south = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path needle = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float degrees = Float.NaN;
    /** The heading currently on the glass, so a redraw can be skipped. */
    private float drawn = Float.NaN;
    private int notified = -1;
    private int notifiedStatus = -1;
    private long lastLog;
    private int accuracy = -1;
    private Heading listener;

    public CompassView(Context context) {
        super(context);
        sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotation = sensors == null ? null
                : sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(context, 2));
        ring.setColor(Ui.BORDER);
        tick.setStrokeWidth(Ui.dp(context, 1));
        tick.setColor(Ui.BORDER);
        north.setColor(Ui.AMBER);
        south.setColor(Ui.MUTED);
        label.setColor(Ui.TEXT);
        label.setTextSize(Ui.dp(context, 14));
        label.setTypeface(android.graphics.Typeface.MONOSPACE);
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    public void setHeadingListener(Heading heading) {
        listener = heading;
        if (rotation == null && heading != null) {
            heading.onHeading(Float.NaN, -1, false);
        }
    }

    public boolean hasSensor() {
        return rotation != null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotation != null) {
            sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI, handler);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sensors != null) {
            sensors.unregisterListener(this);
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        float value = (float) Math.toDegrees(orientation[0]);
        value = (value + 360f) % 360f;
        // A compass that twitches is useless in a hand: follow the sensor, but
        // ignore the last degree of noise.
        if (Float.isNaN(degrees)) {
            degrees = value;
        } else {
            float delta = ((value - degrees + 540f) % 360f) - 180f;
            degrees = (degrees + delta * 0.25f + 360f) % 360f;
        }
        // Redraw only when the needle would visibly move. A view that repaints on
        // every sensor event keeps the window busy forever, which starves
        // uiautomator and costs battery for a picture nobody can tell apart.
        if (Float.isNaN(drawn) || Math.abs(((degrees - drawn + 540f) % 360f) - 180f) >= 0.3f) {
            drawn = degrees;
            invalidate();
        }
        // The screen only prints whole degrees, so that is when it is told. Text
        // that changes fifteen times a second would keep the window permanently
        // busy - bad for the battery, and it starves anything reading the screen.
        int shown = Math.round(degrees) % 360;
        if (listener != null && (shown != notified || accuracy != notifiedStatus)) {
            notified = shown;
            notifiedStatus = accuracy;
            listener.onHeading(degrees, accuracy, true);
        }
        long now = System.currentTimeMillis();
        if (now - lastLog > 1000) {
            lastLog = now;
            android.util.Log.i("FieldKitCompass", "heading " + shown
                    + " accuracy " + accuracy);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int value) {
        accuracy = value;
        if (listener != null) {
            listener.onHeading(degrees, accuracy, rotation != null);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(width, height) / 2f - Ui.dp(getContext(), 18);

        canvas.drawCircle(cx, cy, radius, ring);
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle);
            float outer = radius;
            float inner = radius - Ui.dp(getContext(), angle % 90 == 0 ? 14 : 7);
            float x1 = cx + (float) Math.sin(rad) * inner;
            float y1 = cy - (float) Math.cos(rad) * inner;
            float x2 = cx + (float) Math.sin(rad) * outer;
            float y2 = cy - (float) Math.cos(rad) * outer;
            canvas.drawLine(x1, y1, x2, y2, tick);
        }

        canvas.save();
        canvas.rotate(Float.isNaN(degrees) ? 0f : -degrees, cx, cy);
        needle.reset();
        needle.moveTo(cx, cy - radius + Ui.dp(getContext(), 10));
        needle.lineTo(cx + Ui.dp(getContext(), 10), cy);
        needle.lineTo(cx, cy + radius - Ui.dp(getContext(), 10));
        needle.lineTo(cx - Ui.dp(getContext(), 10), cy);
        needle.close();
        canvas.drawPath(needle, south);
        canvas.save();
        canvas.clipRect(cx - radius, cy - radius, cx + radius, cy);
        canvas.drawPath(needle, north);
        canvas.restore();
        canvas.restore();

        canvas.drawText("N", cx - Ui.dp(getContext(), 5),
                cy - radius + Ui.dp(getContext(), 32), label);
        canvas.drawText("S", cx - Ui.dp(getContext(), 5),
                cy + radius - Ui.dp(getContext(), 16), label);
        canvas.drawText("E", cx + radius - Ui.dp(getContext(), 22), cy + Ui.dp(getContext(), 5),
                label);
        canvas.drawText("W", cx - radius + Ui.dp(getContext(), 12), cy + Ui.dp(getContext(), 5),
                label);
    }
}
