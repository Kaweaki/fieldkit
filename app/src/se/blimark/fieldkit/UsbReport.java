package se.blimark.fieldkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The USB binary, read as numbers.
 *
 * Android's UsbDevice hands over vendor id, product id and interface lists, but
 * it hides the descriptor bytes themselves: bcdUSB, bMaxPacketSize0, the string
 * indices, the power attributes and the endpoint intervals are only in the raw
 * blob that getRawDescriptors() returns. This class parses that blob and names
 * what it finds, with no android import, so the JVM self test can prove the
 * parse against a hand-built descriptor without a cable.
 */
public final class UsbReport {

    private UsbReport() {
    }

    /** 0x0200 -> "2.0", 0x0310 -> "3.1", 0x0320 -> "3.2". */
    public static String usbVersion(int bcd) {
        int major = (bcd >> 8) & 0xff;
        int minor = ((bcd >> 4) & 0xf);
        int sub = bcd & 0xf;
        String value = major + "." + minor;
        if (sub != 0) {
            value += sub;
        }
        return value;
    }

    /** The USB-IF device class names the descriptors use. */
    public static String className(int code) {
        switch (code) {
            case 0x00: return "per interface";
            case 0x01: return "audio";
            case 0x02: return "communications (CDC)";
            case 0x03: return "human interface (HID)";
            case 0x05: return "physical";
            case 0x06: return "image (PTP)";
            case 0x07: return "printer";
            case 0x08: return "mass storage";
            case 0x09: return "hub";
            case 0x0a: return "CDC data";
            case 0x0b: return "smart card";
            case 0x0d: return "content security";
            case 0x0e: return "video";
            case 0x0f: return "personal healthcare";
            case 0x10: return "audio/video";
            case 0x11: return "billboard";
            case 0xdc: return "diagnostic";
            case 0xe0: return "wireless controller";
            case 0xef: return "miscellaneous";
            case 0xfe: return "application specific";
            case 0xff: return "vendor specific";
            default: return "class 0x" + hex2(code);
        }
    }

    /** The subclass names worth knowing on a field cable. */
    public static String subclassName(int base, int sub) {
        if (base == 0x03) {
            switch (sub) {
                case 0x00: return "no boot interface";
                case 0x01: return "boot interface";
                default: break;
            }
        }
        if (base == 0x08) {
            switch (sub) {
                case 0x01: return "reduced block (RBC)";
                case 0x02: return "CD/DVD (MMC-5)";
                case 0x03: return "tape (QIC-157)";
                case 0x04: return "floppy (UFI)";
                case 0x05: return "floppy (SFF-8070i)";
                case 0x06: return "SCSI transparent";
                default: break;
            }
        }
        if (base == 0x02) {
            switch (sub) {
                case 0x02: return "abstract control model (AT commands)";
                case 0x06: return "ethernet control model";
                default: break;
            }
        }
        if (base == 0x0e) {
            switch (sub) {
                case 0x01: return "video control";
                case 0x02: return "video streaming";
                default: break;
            }
        }
        return sub == 0 ? "none" : "0x" + hex2(sub);
    }

    /** Protocol byte, named where the class defines it. */
    public static String protocolName(int base, int proto) {
        if (base == 0x03) {
            switch (proto) {
                case 0x01: return "keyboard";
                case 0x02: return "mouse";
                default: break;
            }
        }
        if (base == 0x08) {
            switch (proto) {
                case 0x00: return "control/bulk/interrupt";
                case 0x01: return "control/bulk";
                case 0x50: return "bulk only";
                default: break;
            }
        }
        return proto == 0 ? "none" : "0x" + hex2(proto);
    }

    /** The vendor ids a field kit actually meets, plus the big ones. */
    public static String vendorName(int vid) {
        switch (vid) {
            case 0x0403: return "Future Technology Devices (FTDI)";
            case 0x10c4: return "Silicon Labs (CP210x)";
            case 0x1a86: return "QinHeng (CH340/CH341)";
            case 0x067b: return "Prolific (PL2303)";
            case 0x2341: return "Arduino";
            case 0x2e8a: return "Raspberry Pi";
            case 0x0483: return "STMicroelectronics";
            case 0x303a: return "Espressif";
            case 0x1915: return "Nordic Semiconductor";
            case 0x239a: return "Adafruit";
            case 0x1b4f: return "SparkFun";
            case 0x1546: return "u-blox";
            case 0x2fe3: return "Zephyr project";
            case 0x1209: return "pid.codes (open hardware)";
            case 0x04e8: return "Samsung";
            case 0x18d1: return "Google";
            case 0x05ac: return "Apple";
            case 0x046d: return "Logitech";
            case 0x0781: return "SanDisk";
            case 0x0951: return "Kingston";
            case 0x0bda: return "Realtek";
            case 0x05e3: return "Genesys Logic";
            case 0x2109: return "VIA Labs";
            case 0x1d6b: return "Linux Foundation (root hub)";
            default: return "";
        }
    }

    /** Product names for the bridges this app can recognise on its own. */
    public static String productName(int vid, int pid) {
        if (vid == 0x0403) {
            switch (pid) {
                case 0x6001: return "FT232R serial";
                case 0x6010: return "FT2232 dual serial";
                case 0x6014: return "FT232H";
                case 0x6015: return "FT231X";
                default: break;
            }
        }
        if (vid == 0x10c4) {
            switch (pid) {
                case 0xea60: return "CP2102 serial";
                case 0xea70: return "CP2105 dual serial";
                default: break;
            }
        }
        if (vid == 0x1a86) {
            switch (pid) {
                case 0x7523: return "CH340 serial";
                case 0x5523: return "CH341A";
                default: break;
            }
        }
        if (vid == 0x067b) {
            switch (pid) {
                case 0x2303: return "PL2303 serial";
                default: break;
            }
        }
        if (vid == 0x2341 && pid == 0x0043) {
            return "Uno";
        }
        if (vid == 0x2e8a && pid == 0x000a) {
            return "Pico (CDC)";
        }
        return "";
    }

    /**
     * bMaxPower is in 2 mA units on USB 2 and below, 8 mA units from USB 3.
     * Android's own getMaxPower() already does this, but the raw descriptor is
     * here because the spec asks for the conversion to be visible.
     */
    public static int milliamp(int maxPowerByte, int bcdUSB) {
        int units = ((bcdUSB >> 8) & 0xff) >= 3 ? 8 : 2;
        return (maxPowerByte & 0xff) * units;
    }

    /** "self-powered, remote wakeup" from bmAttributes. */
    public static String configFlags(int attributes) {
        StringBuilder out = new StringBuilder();
        out.append((attributes & 0x40) != 0 ? "self-powered" : "bus-powered");
        if ((attributes & 0x20) != 0) {
            out.append(", remote wakeup");
        }
        return out.toString();
    }

    /**
     * Decodes the descriptor blob: device descriptor, then every configuration
     * with its interfaces and endpoints. Unknown descriptor types are named and
     * skipped by their bLength, so a truncated or unusual blob still parses as
     * far as it goes.
     */
    public static String[] parse(byte[] raw) {
        List<String> lines = new ArrayList<String>();
        if (raw == null || raw.length < 2) {
            lines.add("the descriptor blob is empty");
            return lines.toArray(new String[0]);
        }
        int i = 0;
        int configs = -1;
        while (i + 2 <= raw.length) {
            int length = raw[i] & 0xff;
            int type = raw[i + 1] & 0xff;
            if (length < 2 || i + length > raw.length) {
                lines.add("descriptor at byte " + i + " claims " + length
                        + " bytes; the blob ends first");
                break;
            }
            if (type == 0x01 && length >= 18) {
                int bcdUSB = u16(raw, i + 2);
                configs = raw[i + 17] & 0xff;
                lines.add("device descriptor, " + length + " bytes");
                lines.add("  bcdUSB " + hex4(bcdUSB) + " (USB " + usbVersion(bcdUSB) + ")");
                lines.add("  class " + hex2(raw[i + 4] & 0xff) + " ("
                        + className(raw[i + 4] & 0xff) + ")");
                lines.add("  subclass " + hex2(raw[i + 5] & 0xff)
                        + ", protocol " + hex2(raw[i + 6] & 0xff) + " ("
                        + protocolName(raw[i + 4] & 0xff, raw[i + 6] & 0xff) + ")");
                lines.add("  bMaxPacketSize0 " + (raw[i + 7] & 0xff) + " bytes");
                lines.add("  idVendor " + hex4(u16(raw, i + 8)) + " ("
                        + u16(raw, i + 8) + "), idProduct " + hex4(u16(raw, i + 10))
                        + " (" + u16(raw, i + 10) + ")");
                lines.add("  bcdDevice " + version(u16(raw, i + 12)));
                lines.add("  string indices: manufacturer " + (raw[i + 14] & 0xff)
                        + ", product " + (raw[i + 15] & 0xff)
                        + ", serial " + (raw[i + 16] & 0xff));
                lines.add("  configurations " + configs);
            } else if (type == 0x02 && length >= 9) {
                int attributes = raw[i + 7] & 0xff;
                int bcdUSB = deviceUsbVersion(lines, raw);
                lines.add("configuration " + (raw[i + 5] & 0xff) + ", " + length + " bytes");
                lines.add("  " + configFlags(attributes) + ", "
                        + milliamp(raw[i + 8], bcdUSB) + " mA requested");
                lines.add("  interfaces " + (raw[i + 4] & 0xff)
                        + ", string index " + (raw[i + 6] & 0xff));
            } else if (type == 0x04 && length >= 9) {
                int base = raw[i + 5] & 0xff;
                lines.add("interface " + (raw[i + 2] & 0xff)
                        + " alt " + (raw[i + 3] & 0xff) + ": "
                        + className(base) + ", subclass "
                        + subclassName(base, raw[i + 6] & 0xff) + ", protocol "
                        + protocolName(base, raw[i + 7] & 0xff));
                lines.add("  endpoints " + (raw[i + 4] & 0xff)
                        + ", string index " + (raw[i + 8] & 0xff));
            } else if (type == 0x05 && length >= 7) {
                int address = raw[i + 2] & 0xff;
                int attributes = raw[i + 3] & 0xff;
                lines.add("endpoint " + hex2(address) + " "
                        + ((address & 0x80) != 0 ? "IN" : "OUT") + " "
                        + endpointType(attributes & 0x03)
                        + ", " + u16(raw, i + 4) + " bytes, interval "
                        + (raw[i + 6] & 0xff)
                        + " (" + endpointUnits(attributes & 0x03) + ")");
            } else {
                lines.add("descriptor type " + hex2(type) + ", " + length + " bytes");
            }
            i += length;
        }
        if (configs == 0) {
            lines.add("the device advertises no configuration");
        }
        return lines.toArray(new String[0]);
    }

    public static String endpointType(int code) {
        switch (code) {
            case 0: return "control";
            case 1: return "isochronous";
            case 2: return "bulk";
            case 3: return "interrupt";
            default: return "unknown";
        }
    }

    /** Intervals are frames for full speed and microframes for high speed. */
    public static String endpointUnits(int type) {
        return type == 1 ? "frames or microframes" : "frames";
    }

    /** The usage pages a HID report descriptor declares, in the order it declares them. */
    public static String hidUsages(byte[] report) {
        List<String> pages = new ArrayList<String>();
        if (report == null) {
            return "none";
        }
        int i = 0;
        while (i < report.length) {
            int prefix = report[i] & 0xff;
            if (prefix == 0xfe) {
                i += 3;
                continue;
            }
            int size = prefix & 0x03;
            if (size == 3) {
                size = 4;
            }
            int type = (prefix >> 2) & 0x03;
            int tag = (prefix >> 4) & 0x0f;
            if (i + 1 + size > report.length) {
                break;
            }
            int value = 0;
            for (int b = 0; b < size; b++) {
                value |= (report[i + 1 + b] & 0xff) << (8 * b);
            }
            // Usage page is a global item: type 1, tag 0.
            if (type == 1 && tag == 0 && !pages.contains(usagePage(value))) {
                pages.add(usagePage(value));
            }
            i += 1 + size;
        }
        if (pages.isEmpty()) {
            return "no usage page declared";
        }
        StringBuilder out = new StringBuilder();
        for (int p = 0; p < pages.size(); p++) {
            if (p > 0) {
                out.append(", ");
            }
            out.append(pages.get(p));
        }
        return out.toString();
    }

    private static String usagePage(int page) {
        String name;
        switch (page) {
            case 0x01: name = "generic desktop"; break;
            case 0x02: name = "simulation"; break;
            case 0x05: name = "game controls"; break;
            case 0x06: name = "generic device"; break;
            case 0x07: name = "keyboard"; break;
            case 0x08: name = "LED"; break;
            case 0x09: name = "button"; break;
            case 0x0c: name = "consumer control"; break;
            case 0x0d: name = "digitizer"; break;
            case 0x0f: name = "physical interface"; break;
            case 0x10: name = "unicode"; break;
            case 0xff: name = "vendor defined"; break;
            default: name = "page"; break;
        }
        return "0x" + hex2(page) + " (" + name + ")";
    }

    /** "1.10" for a bcdDevice of 0x0110. */
    public static String version(int bcd) {
        return ((bcd >> 8) & 0xff) + "." + hex2(bcd & 0xff);
    }

    /** A hex dump with an offset column, capped so a long blob stays readable. */
    public static String hex(byte[] data, int limit) {
        if (data == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int end = Math.min(data.length, limit);
        for (int offset = 0; offset < end; offset += 16) {
            out.append(String.format(Locale.US, "%04x  ", offset));
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                int index = offset + j;
                if (index < end) {
                    int value = data[index] & 0xff;
                    out.append(String.format(Locale.US, "%02x ", value));
                    ascii.append(value >= 32 && value < 127 ? (char) value : '.');
                } else {
                    out.append("   ");
                }
                if (j == 7) {
                    out.append(' ');
                }
            }
            out.append(' ').append(ascii).append('\n');
        }
        if (data.length > limit) {
            out.append("... ").append(data.length - limit).append(" more bytes\n");
        }
        return out.toString();
    }

    /** JSON string escaping, so a report can be saved without breaking the file. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static int deviceUsbVersion(List<String> lines, byte[] raw) {
        for (int i = 0; i + 4 <= raw.length; i++) {
            if ((raw[i] & 0xff) >= 18 && (raw[i + 1] & 0xff) == 0x01) {
                return u16(raw, i + 2);
            }
        }
        return 0x0200;
    }

    private static int u16(byte[] raw, int offset) {
        return (raw[offset] & 0xff) | ((raw[offset + 1] & 0xff) << 8);
    }

    private static String hex2(int value) {
        return String.format(Locale.US, "%02x", value & 0xff);
    }

    private static String hex4(int value) {
        return String.format(Locale.US, "%04x", value & 0xffff);
    }
}
