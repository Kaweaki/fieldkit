package se.blimark.fieldkit;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * The torch, and the one signal everyone recognises.
 *
 * CameraManager.setTorchMode needs no permission from API 23, so this adds no
 * grant and no camera prompt. On Android 5 the only route is opening the camera,
 * which does need one: below 23 the feature reports itself unavailable rather
 * than asking for the camera on a privacy-first device.
 */
public final class Torch {

    /** Morse timing unit at 200 ms: a dot is 1, a dash is 3, gaps are 1 or 3. */
    public static final int UNIT_MS = 200;

    /** SOS: three dots, three dashes, three dots, then a long gap. */
    private static final int[] SOS = {
            1, -1, 1, -1, 1, -3,
            3, -1, 3, -1, 3, -3,
            1, -1, 1, -1, 1, -7
    };

    private static String cachedId;
    private static boolean lit;
    private static Runnable pulse;

    private Torch() {
    }

    public static boolean supported(Context context) {
        return Build.VERSION.SDK_INT >= 23 && id(context) != null;
    }

    public static String unavailable(Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return "The torch API arrived in Android 6. Turning the light on here would "
                    + "need the camera permission, which this app does not ask for.";
        }
        if (id(context) == null) {
            return "This phone reports no camera flash, so there is no torch to drive.";
        }
        return "";
    }

    public static boolean isOn() {
        return lit;
    }

    public static boolean toggle(Context context) {
        return set(context, !lit);
    }

    public static boolean set(Context context, boolean on) {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        String cameraId = id(context);
        if (cameraId == null) {
            return false;
        }
        try {
            manager(context).setTorchMode(cameraId, on);
            lit = on;
            return true;
        } catch (CameraAccessException e) {
            lit = false;
            return false;
        } catch (RuntimeException e) {
            // Some OEM builds throw instead of reporting a busy camera.
            lit = false;
            return false;
        }
    }

    /** Start the SOS pattern; call stop() when the screen closes. */
    public static void sos(final Context context) {
        if (Build.VERSION.SDK_INT < 23 || id(context) == null) {
            return;
        }
        stop(context);
        final Handler handler = new Handler(Looper.getMainLooper());
        pulse = new Runnable() {
            private int step;

            @Override
            public void run() {
                int value = SOS[step % SOS.length];
                if (value > 0) {
                    set(context, true);
                } else {
                    set(context, false);
                }
                step++;
                handler.postDelayed(this, Math.abs(value) * UNIT_MS);
            }
        };
        handler.post(pulse);
    }

    public static void stop(Context context) {
        if (pulse != null) {
            new Handler(Looper.getMainLooper()).removeCallbacks(pulse);
            pulse = null;
        }
        set(context, false);
    }

    private static CameraManager manager(Context context) {
        return (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    private static String id(Context context) {
        if (cachedId != null) {
            return cachedId;
        }
        CameraManager cameraManager = manager(context);
        if (cameraManager == null) {
            return null;
        }
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (int i = 0; i < ids.length; i++) {
                Boolean flash = cameraManager.getCameraCharacteristics(ids[i])
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash.booleanValue()) {
                    cachedId = ids[i];
                    return cachedId;
                }
            }
        } catch (CameraAccessException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }
}
