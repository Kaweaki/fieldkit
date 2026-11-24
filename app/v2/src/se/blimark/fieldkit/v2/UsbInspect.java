package se.blimark.fieldkit.v2;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbConstants;
import android.os.Build;

import se.blimark.fieldkit.UsbReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The other end of the cable.
 *
 * Android exposes a UsbDevice with vendor id, product id and interface lists;
 * everything else - bcdUSB, the string indices, the power attributes, the
 * endpoint intervals - lives in the raw descriptor blob, which is only readable
 * once the user grants permission for that one device. This class walks that
 * path: enumerate, ask, read the blob, name what is in it, then close the
 * connection so the next app can have the port.
 *
 * No permission is declared or needed for any of it beyond the per-device grant
 * Android itself shows as a dialog, and nothing is written to an attached device.
 */
final class UsbInspect {

    static final String ACTION_PERMISSION = "se.blimark.fieldkit.v2.USB_PERMISSION";

    /** What the last read produced, kept so the screen can be rebuilt after a grant. */
    static String lastReport = "";
    static String lastJson = "";
    static String lastDevice = "";
    private static String pendingJson;

    private UsbInspect() {
    }

    /** Drops the previous read so the inspector opens clean. */
    static void prepare() {
        lastReport = "";
        lastJson = "";
        lastDevice = "";
    }

    /** Held for the SAF picker, like the GPX text, so a cancelled save loses nothing. */
    static String takePendingJson() {
        String text = pendingJson;
        pendingJson = null;
        return text;
    }

    static void keepPendingJson() {
        pendingJson = lastJson;
    }

    static boolean hostSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    static UsbManager manager(Context context) {
        return (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    static List<UsbDevice> devices(Context context) {
        List<UsbDevice> out = new ArrayList<UsbDevice>();
        UsbManager usb = manager(context);
        if (usb == null) {
            return out;
        }
        try {
            out.addAll(usb.getDeviceList().values());
        } catch (RuntimeException ignored) {
            // A vendor build can refuse the list; an empty answer is the honest one.
        }
        return out;
    }

    /** A device that enumerated in AOA mode is not in getDeviceList at all. */
    static String accessories(Context context) {
        UsbManager usb = manager(context);
        if (usb == null) {
            return "no USB service";
        }
        try {
            android.hardware.usb.UsbAccessory[] found = usb.getAccessoryList();
            if (found == null || found.length == 0) {
                return "none";
            }
            StringBuilder out = new StringBuilder();
            for (android.hardware.usb.UsbAccessory accessory : found) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(accessory.getManufacturer()).append(' ')
                        .append(accessory.getModel());
            }
            return out.toString();
        } catch (RuntimeException error) {
            return "refused (" + error.getClass().getSimpleName() + ")";
        }
    }

    /**
     * UsbManager.getPorts() arrived in Android 9 but is not in the public SDK on
     * every build, so it is called through reflection and reported honestly when
     * the platform hides it.
     */
    static String ports(Context context) {
        UsbManager usb = manager(context);
        if (usb == null) {
            return "no USB service";
        }
        try {
            Object value = UsbManager.class.getMethod("getPorts").invoke(usb);
            if (!(value instanceof java.util.Map)) {
                return "the port list is not exposed here";
            }
            java.util.Map<?, ?> ports = (java.util.Map<?, ?>) value;
            if (ports.isEmpty()) {
                return "no USB-C port reported by this Android";
            }
            StringBuilder out = new StringBuilder();
            for (java.util.Map.Entry<?, ?> entry : ports.entrySet()) {
                Object status = call(entry.getValue(), "getStatus");
                if (out.length() > 0) {
                    out.append("\n");
                }
                out.append(entry.getKey()).append(": ");
                if (status == null) {
                    out.append("no status (nothing plugged in?)");
                    continue;
                }
                out.append("power ").append(powerRole(callInt(status, "getCurrentPowerRole")))
                        .append(", data ").append(dataRole(callInt(status, "getCurrentDataRole")));
            }
            return out.toString();
        } catch (Throwable error) {
            // getPorts() is guarded by a system permission on most vendor builds;
            // name the refusal instead of guessing at it.
            Throwable cause = error.getCause() == null ? error : error.getCause();
            return "the port list is closed to apps on this Android ("
                    + cause.getClass().getSimpleName() + ")";
        }
    }

    static boolean hasPermission(Context context, UsbDevice device) {
        UsbManager usb = manager(context);
        return usb != null && usb.hasPermission(device);
    }

    /**
     * Asks Android for the one device, then reads it as soon as the answer comes
     * back. The receiver unregisters itself, so repeated taps do not stack them.
     */
    static void request(final Activity activity, final UsbDevice device) {
        final UsbManager usb = manager(activity);
        if (usb == null) {
            lastReport = "This phone has no USB service.";
            V2Activity.showUsb(activity);
            return;
        }
        if (usb.hasPermission(device)) {
            read(activity, usb, device);
            return;
        }
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    activity.unregisterReceiver(this);
                } catch (RuntimeException ignored) {
                    // Already gone: the screen was rebuilt underneath us.
                }
                boolean granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false);
                UsbDevice answered = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (granted && answered != null) {
                    read(activity, usb, answered);
                } else {
                    lastReport = "The permission dialog was declined, so the descriptors "
                            + "stay closed. Nothing else in the app needs it.";
                    lastJson = "";
                    lastDevice = "";
                    V2Activity.showUsb(activity);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_PERMISSION);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // The dialog answers from the system, so this one is exported.
                activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(receiver, filter);
            }
        } catch (RuntimeException error) {
            lastReport = "Could not listen for the permission answer: " + error.getMessage();
            V2Activity.showUsb(activity);
            return;
        }
        Intent ask = new Intent(ACTION_PERMISSION);
        ask.setPackage(activity.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent answer = PendingIntent.getBroadcast(activity, 0, ask, flags);
        usb.requestPermission(device, answer);
    }

    private static void read(Activity activity, UsbManager usb, UsbDevice device) {
        lastDevice = device.getDeviceName();
        try {
            lastReport = report(activity, usb, device);
            lastJson = json(activity, device);
        } catch (RuntimeException error) {
            lastReport = "The read failed: " + error.getClass().getSimpleName()
                    + " (" + error.getMessage() + ").";
            lastJson = "";
        }
        V2Activity.showUsb(activity);
    }

    /** The full profile, as sections the screen turns into folded cards. */
    static String report(Context context, UsbManager usb, UsbDevice device) {
        StringBuilder out = new StringBuilder();
        out.append("== IDENTITY\n");
        out.append("node ").append(device.getDeviceName()).append('\n');
        out.append("device id ").append(device.getDeviceId()).append('\n');
        out.append(identity("vendor", device.getVendorId())).append('\n');
        out.append(identity("product", device.getProductId())).append('\n');
        out.append("bcdDevice ").append(device.getVersion()).append('\n');
        out.append("class ").append(hex2(device.getDeviceClass())).append(" (")
                .append(UsbReport.className(device.getDeviceClass())).append("), subclass ")
                .append(hex2(device.getDeviceSubclass())).append(", protocol ")
                .append(hex2(device.getDeviceProtocol())).append('\n');
        out.append("host mode supported on this phone: ")
                .append(hostSupported(context) ? "yes" : "no").append('\n');

        out.append("== STRINGS\n");
        out.append(strings(device));

        UsbDeviceConnection connection = null;
        try {
            connection = usb.openDevice(device);
            if (connection == null) {
                out.append("== CONNECTION\n");
                out.append("openDevice returned null - another app holds this device, "
                        + "or the port was reset.\n");
            } else {
                byte[] raw = null;
                try {
                    raw = connection.getRawDescriptors();
                } catch (RuntimeException ignored) {
                    // Fall through to the control transfer below.
                }
                if (raw == null) {
                    byte[] probe = new byte[18];
                    int count = connection.controlTransfer(0x80, 0x06, 0x0100, 0, probe, 18, 400);
                    if (count >= 18) {
                        raw = probe;
                    }
                }
                out.append("== RAW DESCRIPTORS\n");
                if (raw == null) {
                    out.append("the phone would not hand over a descriptor blob\n");
                } else {
                    for (String line : UsbReport.parse(raw)) {
                        out.append(line).append('\n');
                    }
                    out.append("blob ").append(raw.length).append(" bytes\n");
                }

                out.append("== LANGUAGE IDS\n");
                out.append(languages(connection));

                out.append("== CONFIGURATIONS\n");
                out.append(configurations(device));

                String hid = hidReport(connection, device);
                if (hid.length() > 0) {
                    out.append("== HID REPORT DESCRIPTOR\n");
                    out.append(hid);
                }
            }
        } catch (SecurityException error) {
            out.append("== CONNECTION\n");
            out.append("Android refused the connection: ").append(error.getMessage()).append('\n');
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return out.toString();
    }

    private static String strings(UsbDevice device) {
        StringBuilder out = new StringBuilder();
        out.append("manufacturer: ").append(safe(device, "manufacturer")).append('\n');
        out.append("product: ").append(safe(device, "product")).append('\n');
        out.append("serial: ").append(safe(device, "serial")).append('\n');
        return out.toString();
    }

    /** Each getter can throw SecurityException on its own, so each is asked alone. */
    private static String safe(UsbDevice device, String which) {
        try {
            String value;
            if ("manufacturer".equals(which)) {
                value = device.getManufacturerName();
            } else if ("product".equals(which)) {
                value = device.getProductName();
            } else {
                value = device.getSerialNumber();
            }
            return value == null ? "(descriptor has no string)" : value;
        } catch (SecurityException error) {
            return "(Android hides it from apps)";
        }
    }

    private static String configurations(UsbDevice device) {
        StringBuilder out = new StringBuilder();
        int configs = device.getConfigurationCount();
        out.append("configurations ").append(configs).append('\n');
        for (int c = 0; c < configs; c++) {
            android.hardware.usb.UsbConfiguration configuration = device.getConfiguration(c);
            out.append("config ").append(c + 1)
                    .append(" id ").append(configuration.getId())
                    .append(": ").append(configuration.isSelfPowered()
                            ? "self-powered" : "bus-powered")
                    .append(configuration.isRemoteWakeup() ? ", remote wakeup" : "")
                    .append(", max power ").append(configuration.getMaxPower()).append(" mA")
                    .append(", ").append(configuration.getInterfaceCount())
                    .append(" interfaces\n");
            for (int i = 0; i < configuration.getInterfaceCount(); i++) {
                UsbInterface iface = configuration.getInterface(i);
                out.append("  interface ").append(iface.getId())
                        .append(" alt ").append(iface.getAlternateSetting())
                        .append(": ").append(UsbReport.className(iface.getInterfaceClass()))
                        .append(", subclass ").append(UsbReport.subclassName(
                                iface.getInterfaceClass(), iface.getInterfaceSubclass()))
                        .append(", protocol ").append(UsbReport.protocolName(
                                iface.getInterfaceClass(), iface.getInterfaceProtocol()))
                        .append(", ").append(iface.getEndpointCount()).append(" endpoints\n");
                try {
                    if (iface.getName() != null) {
                        out.append("    name: ").append(iface.getName()).append('\n');
                    }
                } catch (SecurityException ignored) {
                    // No name for us.
                }
                for (int e = 0; e < iface.getEndpointCount(); e++) {
                    UsbEndpoint endpoint = iface.getEndpoint(e);
                    out.append("    endpoint ").append(hex2(endpoint.getAddress()))
                            .append(' ').append(endpoint.getDirection() == UsbConstants.USB_DIR_IN
                                    ? "IN" : "OUT")
                            .append(' ').append(UsbReport.endpointType(endpoint.getType()))
                            .append(", ").append(endpoint.getMaxPacketSize()).append(" bytes")
                            .append(", interval ").append(endpoint.getInterval())
                            .append(" (").append(UsbReport.endpointUnits(endpoint.getType()))
                            .append(")\n");
                }
            }
        }
        return out.toString();
    }

    /** GET_DESCRIPTOR 0x0300: the language ids a string descriptor uses. */
    private static String languages(UsbDeviceConnection connection) {
        byte[] data = new byte[32];
        int count;
        try {
            count = connection.controlTransfer(0x80, 0x06, 0x0300, 0, data, data.length, 400);
        } catch (RuntimeException error) {
            return "the transfer failed: " + error.getClass().getSimpleName() + "\n";
        }
        if (count < 4) {
            return "no language descriptor (transfer returned " + count + ")\n";
        }
        StringBuilder out = new StringBuilder();
        int entries = (data[0] & 0xff) >= 4 ? (data[0] - 2) / 2 : 0;
        for (int i = 0; i < entries && 2 + i * 2 + 1 < data.length; i++) {
            int id = (data[2 + i * 2] & 0xff) | ((data[3 + i * 2] & 0xff) << 8);
            out.append(String.format(Locale.US, "0x%04x\n", id));
        }
        return out.length() == 0 ? "no language ids listed\n" : out.toString();
    }

    /**
     * HID interfaces answer GET_DESCRIPTOR 0x22. The interface is claimed only
     * for the length of this one read and released in the finally block, so a
     * keyboard app can open it a moment later.
     */
    private static String hidReport(UsbDeviceConnection connection, UsbDevice device) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() != 0x03) {
                continue;
            }
            boolean claimed = false;
            try {
                claimed = connection.claimInterface(iface, true);
                if (!claimed) {
                    out.append("interface ").append(iface.getId())
                            .append(": could not be claimed\n");
                    continue;
                }
                byte[] head = new byte[8];
                int count = connection.controlTransfer(0x81, 0x06, 0x2200, iface.getId(),
                        head, head.length, 400);
                if (count < 2) {
                    out.append("interface ").append(iface.getId())
                            .append(": no report descriptor (transfer ").append(count).append(")\n");
                    continue;
                }
                int size = ((head[2] & 0xff) | ((head[3] & 0xff) << 8)) & 0xffff;
                byte[] report = new byte[Math.min(size, 512)];
                count = connection.controlTransfer(0x81, 0x06, 0x2200, iface.getId(),
                        report, report.length, 500);
                if (count <= 0) {
                    out.append("interface ").append(iface.getId())
                            .append(": the report descriptor could not be read\n");
                    continue;
                }
                byte[] read = new byte[count];
                System.arraycopy(report, 0, read, 0, count);
                out.append("interface ").append(iface.getId()).append(", ")
                        .append(count).append(" of ").append(size).append(" bytes\n");
                out.append(UsbReport.hex(read, 256));
                out.append("usages: ").append(UsbReport.hidUsages(read)).append('\n');
            } catch (RuntimeException error) {
                out.append("interface ").append(iface.getId())
                        .append(": ").append(error.getClass().getSimpleName()).append('\n');
            } finally {
                if (claimed) {
                    connection.releaseInterface(iface);
                }
            }
        }
        return out.toString();
    }

    /** The structured version, built from the same fields the screen shows. */
    static String json(Context context, UsbDevice device) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"read_at\": \"").append(android.text.format.DateFormat.format(
                "yyyy-MM-dd'T'HH:mm:ss", System.currentTimeMillis())).append("\",\n");
        out.append("  \"device\": {\n");
        out.append("    \"node\": \"").append(UsbReport.escape(device.getDeviceName())).append("\",\n");
        out.append("    \"device_id\": ").append(device.getDeviceId()).append(",\n");
        out.append("    \"vendor_id\": \"0x").append(hex4(device.getVendorId())).append("\",\n");
        out.append("    \"vendor_id_decimal\": ").append(device.getVendorId()).append(",\n");
        out.append("    \"product_id\": \"0x").append(hex4(device.getProductId())).append("\",\n");
        out.append("    \"product_id_decimal\": ").append(device.getProductId()).append(",\n");
        out.append("    \"bcd_device\": \"").append(device.getVersion()).append("\",\n");
        out.append("    \"class\": ").append(device.getDeviceClass()).append(",\n");
        out.append("    \"class_name\": \"").append(UsbReport.className(device.getDeviceClass()))
                .append("\",\n");
        out.append("    \"subclass\": ").append(device.getDeviceSubclass()).append(",\n");
        out.append("    \"protocol\": ").append(device.getDeviceProtocol()).append(",\n");
        out.append("    \"manufacturer\": \"").append(UsbReport.escape(safe(device, "manufacturer")))
                .append("\",\n");
        out.append("    \"product\": \"").append(UsbReport.escape(safe(device, "product")))
                .append("\",\n");
        out.append("    \"serial\": \"").append(UsbReport.escape(safe(device, "serial")))
                .append("\",\n");
        out.append("    \"vendor_name\": \"").append(UsbReport.escape(
                UsbReport.vendorName(device.getVendorId()))).append("\",\n");
        out.append("    \"product_name\": \"").append(UsbReport.escape(
                UsbReport.productName(device.getVendorId(), device.getProductId()))).append("\",\n");
        out.append("    \"ports\": \"").append(UsbReport.escape(ports(context))).append("\",\n");
        out.append("    \"interfaces\": [\n");
        List<String> entries = new ArrayList<String>();
        for (int c = 0; c < device.getConfigurationCount(); c++) {
            UsbConfiguration configuration = device.getConfiguration(c);
            for (int i = 0; i < configuration.getInterfaceCount(); i++) {
                UsbInterface iface = configuration.getInterface(i);
                StringBuilder one = new StringBuilder();
                one.append("      {\"configuration\": ").append(c + 1)
                        .append(", \"id\": ").append(iface.getId())
                        .append(", \"alternate\": ").append(iface.getAlternateSetting())
                        .append(", \"class\": ").append(iface.getInterfaceClass())
                        .append(", \"class_name\": \"").append(UsbReport.className(
                                iface.getInterfaceClass())).append("\"")
                        .append(", \"subclass\": ").append(iface.getInterfaceSubclass())
                        .append(", \"protocol\": ").append(iface.getInterfaceProtocol())
                        .append(", \"endpoints\": ").append(iface.getEndpointCount())
                        .append("}");
                entries.add(one.toString());
            }
        }
        out.append(join(entries, ",\n"));
        out.append("\n    ]\n");
        out.append("  },\n");
        out.append("  \"report\": \"").append(UsbReport.escape(lastReport)).append("\"\n");
        out.append("}\n");
        return out.toString();
    }

    private static String identity(String label, int id) {
        return label + " 0x" + hex4(id) + " (" + id + ")"
                + (UsbReport.vendorName(id).length() > 0 ? " - " + UsbReport.vendorName(id) : "");
    }

    private static String join(List<String> values, String between) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(between);
            }
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static Object call(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int callInt(Object target, String name) {
        Object value = call(target, name);
        return value instanceof Integer ? (Integer) value : -1;
    }

    private static String powerRole(int role) {
        switch (role) {
            case 1: return "source (this phone supplies power)";
            case 2: return "sink (the cable supplies power)";
            case 0: return "none";
            default: return "not reported";
        }
    }

    private static String dataRole(int role) {
        switch (role) {
            case 1: return "host";
            case 2: return "device";
            case 0: return "none";
            default: return "not reported";
        }
    }

    private static String hex2(int value) {
        return String.format(Locale.US, "%02x", value & 0xff);
    }

    private static String hex4(int value) {
        return String.format(Locale.US, "%04x", value & 0xffff);
    }
}
