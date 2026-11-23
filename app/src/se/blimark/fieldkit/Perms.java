package se.blimark.fieldkit;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * The runtime half of the Bluetooth permission split.
 *
 * The manifest declares the permissions; nothing happens until the app asks.
 * Before 2.7 it asked only for location and storage, so on Android 12+ every
 * radio screen died with a SecurityException that the UI never explained.
 */
public final class Perms {

    public static final int REQ_RADIO = 4101;

    private Perms() {
    }

    public static boolean granted(Context context, String permission) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23) {
            return context.getPackageManager()
                    .checkPermission(permission, context.getPackageName())
                    == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** What the radio screens need on this platform version. */
    public static String[] needed() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        }
        // Android 6-11 gate Bluetooth scans behind location.
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public static String[] missing(Context context) {
        List<String> out = new ArrayList<String>();
        String[] all = needed();
        for (int i = 0; i < all.length; i++) {
            if (!granted(context, all[i])) {
                out.add(all[i]);
            }
        }
        return out.toArray(new String[0]);
    }

    public static boolean hasRadio(Context context) {
        return missing(context).length == 0;
    }

    /**
     * Ask once and return whether the caller may proceed now. A false return
     * means a system dialog is up; run the scan from onRequestPermissionsResult.
     */
    public static boolean ensureRadio(Activity activity) {
        if (activity == null) {
            return false;
        }
        String[] missing = missing(activity);
        if (missing.length == 0) {
            return true;
        }
        activity.requestPermissions(missing, REQ_RADIO);
        return false;
    }

    /** Plain-language reason for a refusal, in the app's own style. */
    public static String explain(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            return "Android 12 and later ask for Nearby devices before an app may scan or "
                    + "connect. Allow it in App info > Permissions, then open this screen again.";
        }
        return "Android 11 and earlier need Location for a Bluetooth scan. Allow it in "
                + "App info > Permissions, then open this screen again.";
    }

    public static String name(String permission) {
        int dot = permission.lastIndexOf('.');
        return dot < 0 ? permission : permission.substring(dot + 1);
    }
}
