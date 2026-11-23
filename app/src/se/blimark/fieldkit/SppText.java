package se.blimark.fieldkit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.OutputStream;
import java.util.UUID;

/**
 * Sending one TextLink frame over classic Bluetooth serial.
 *
 * The app already speaks SPP (Serial and PC link screens); this reuses that
 * transport for short text between two FieldKit phones, which needs no network
 * and no server. Every call returns an empty string on success or a sentence the
 * error card can show.
 */
public final class SppText {

    public static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private SppText() {
    }

    public static String send(Context context, String mac, String line, int timeoutMs) {
        if (mac == null || mac.length() == 0) {
            return "No device selected.";
        }
        if (android.os.Build.VERSION.SDK_INT >= 31
                && !Perms.granted(context, android.Manifest.permission.BLUETOOTH_CONNECT)) {
            return Perms.explain(context);
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return "This phone has no Bluetooth adapter.";
        }
        if (!adapter.isEnabled()) {
            return "Bluetooth is off. Turn it on and try again.";
        }
        BluetoothSocket socket = null;
        OutputStream out = null;
        try {
            BluetoothDevice device = adapter.getRemoteDevice(mac);
            socket = device.createRfcommSocketToServiceRecord(SPP);
            socket.connect();
            out = socket.getOutputStream();
            out.write((line + "\n").getBytes("UTF-8"));
            out.flush();
            return "";
        } catch (IllegalArgumentException e) {
            return "That address is not a Bluetooth MAC.";
        } catch (SecurityException e) {
            return Perms.explain(context);
        } catch (Exception e) {
            return "The other phone did not answer. Pair it first, keep both screens "
                    + "open, stay within a few metres, then try again.";
        } finally {
            close(out);
            close(socket);
        }
    }

    private static void close(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            if (closeable instanceof OutputStream) {
                ((OutputStream) closeable).close();
            } else if (closeable instanceof BluetoothSocket) {
                ((BluetoothSocket) closeable).close();
            }
        } catch (Exception ignored) {
            // Closing is best effort.
        }
    }
}
