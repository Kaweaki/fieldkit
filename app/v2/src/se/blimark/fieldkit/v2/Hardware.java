package se.blimark.fieldkit.v2;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.PowerManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * What the phone can say about its own power and its own cable, with no new
 * permission.
 *
 * The battery figures come from the sticky ACTION_BATTERY_CHANGED intent, the
 * saver state from PowerManager, host-mode devices from UsbManager (which needs
 * no permission to list) and a tethered link from the interface names the kernel
 * publishes in /sys/class/net. Nothing here asks the network anything, and the
 * app still has no INTERNET permission.
 */
final class Hardware {

    private Hardware() {
    }

    /** "100%", "74%", or an empty string when the phone will not say. */
    static String batteryPercent(Context context) {
        Intent state = battery(context);
        if (state == null) {
            return "";
        }
        int level = state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = state.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            return "";
        }
        return Math.round(level * 100f / scale) + "%";
    }

    /** "full", "charging", "not charging", "discharging", or "unknown". */
    static String batteryStatus(Context context) {
        Intent state = battery(context);
        if (state == null) {
            return "unknown";
        }
        switch (state.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            case BatteryManager.BATTERY_STATUS_FULL:
                return "full";
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "charging";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "not charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "discharging";
            default:
                return "unknown";
        }
    }

    /** "USB", "AC", "wireless", "not plugged in" - where the power comes from. */
    static String powerSource(Context context) {
        Intent state = battery(context);
        if (state == null) {
            return "unknown";
        }
        switch (state.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "AC";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "wireless";
            default:
                return "not plugged in";
        }
    }

    /** "24.7 C" - the battery's own thermometer, not the weather. */
    static String batteryTemperature(Context context) {
        Intent state = battery(context);
        if (state == null) {
            return "";
        }
        int tenths = state.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (tenths == Integer.MIN_VALUE) {
            return "";
        }
        return String.format(java.util.Locale.US, "%.1f C", tenths / 10.0);
    }

    static boolean powerSaveOn(Context context) {
        PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return manager != null && manager.isPowerSaveMode();
    }

    /** "0 devices", or "2 devices: Samsung ... , ..." on a host-mode cable. */
    static String hostDevices(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            return "no USB manager on this phone";
        }
        try {
            java.util.HashMap<String, UsbDevice> found = manager.getDeviceList();
            if (found == null || found.isEmpty()) {
                return "0 devices on the host port";
            }
            List<String> names = new ArrayList<String>();
            for (UsbDevice device : found.values()) {
                names.add(device.getProductName() == null
                        ? "device " + device.getDeviceId() : device.getProductName());
            }
            return found.size() + (found.size() == 1 ? " device: " : " devices: ")
                    + join(names);
        } catch (RuntimeException e) {
            return "the USB service refused the list";
        }
    }

    /**
     * The interfaces USB tethering creates. When the phone is tethered to a
     * computer, the kernel publishes rndis0/usb0/eth0 here; when it is not, none
     * of them exist. Reading the directory needs no permission.
     */
    static String tetherInterfaces() {
        File net = new File("/sys/class/net");
        String[] names = net.list();
        if (names == null) {
            return "the kernel would not list its interfaces";
        }
        List<String> tethered = new ArrayList<String>();
        for (String name : names) {
            if (name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("eth")) {
                tethered.add(name);
            }
        }
        if (tethered.isEmpty()) {
            return "none - no usb0, rndis0 or eth0 interface";
        }
        return join(tethered) + " up";
    }

    private static Intent battery(Context context) {
        try {
            return context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(values.get(i));
        }
        return out.toString();
    }
}
