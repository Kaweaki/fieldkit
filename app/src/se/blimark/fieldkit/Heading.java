package se.blimark.fieldkit;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * A compass without a fix and without a permission.
 *
 * The rotation vector already fuses accelerometer and magnetometer, so this is
 * one listener and no calibration UI. FieldKit's peers screen can only give a
 * bearing after both sides have GPS; this gives one standing still.
 */
public final class Heading implements SensorEventListener {

    public interface Listener {
        /** degrees is 0 = north, clockwise; accuracy is the sensor's error estimate. */
        void onHeading(float degrees, String point, float accuracyDegrees);
    }

    private static final String[] POINTS = {
            "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    };

    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;

    private Heading(SensorManager manager, Sensor sensor, Listener listener) {
        this.manager = manager;
        this.sensor = sensor;
        this.listener = listener;
    }

    /** Returns null when the phone has no rotation vector, which is most tablets. */
    public static Heading start(Context context, Listener listener) {
        if (context == null || listener == null) {
            return null;
        }
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) {
            return null;
        }
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null) {
            return null;
        }
        Heading heading = new Heading(manager, sensor, listener);
        manager.registerListener(heading, sensor, SensorManager.SENSOR_DELAY_UI);
        return heading;
    }

    public void stop() {
        manager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values.length < 3) {
            return;
        }
        float[] rotation = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(rotation, event.values);
        SensorManager.getOrientation(rotation, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        degrees = (degrees + 360f) % 360f;
        listener.onHeading(degrees, compassPoint(degrees), 5f);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to do: the app keeps its own "estimate" wording.
    }

    /** 0 -> "N", 45 -> "NE", 350 -> "N". */
    public static String compassPoint(float degrees) {
        float normalised = ((degrees % 360f) + 360f) % 360f;
        int index = Math.round(normalised / 45f) % 8;
        return POINTS[index];
    }

    public static String line(float degrees) {
        return String.format("%s %03.0f\u00b0", compassPoint(degrees), degrees);
    }
}
