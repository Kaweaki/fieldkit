package se.blimark.fieldkit;

import java.io.ByteArrayOutputStream;

/**
 * The metadata wipe: a JPEG in, the same picture out without the blocks that
 * carry who, when and where.
 *
 * The scan data is never touched, so the pixels are bit for bit what they were.
 * Only whole segments are dropped: APP1 (EXIF, XMP), APP13 (IPTC), every other
 * vendor APPn block and the free-text comments. APP0 (JFIF) and APP2 (the ICC
 * colour profile) stay, because dropping them changes how the image renders
 * without removing anything about the photographer.
 *
 * Pure Java on purpose: it runs in the JVM self test, and the app only supplies
 * the bytes.
 */
public final class Jpeg {

    private static final int SOI = 0xD8;
    private static final int EOI = 0xD9;
    private static final int SOS = 0xDA;
    private static final int APP0 = 0xE0;
    private static final int APP1 = 0xE1;
    private static final int APP2 = 0xE2;
    private static final int COMMENT = 0xFE;

    private Jpeg() {
    }

    /**
     * The same JPEG with every metadata block removed.
     *
     * Throws IllegalArgumentException for anything that is not a JPEG, so the
     * caller can say so instead of writing a broken file.
     */
    public static byte[] strip(byte[] in) {
        if (in == null || in.length < 4 || (in[0] & 0xFF) != 0xFF || (in[1] & 0xFF) != SOI) {
            throw new IllegalArgumentException("not a JPEG");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 2);
        int at = 2;
        while (at + 1 < in.length) {
            if ((in[at] & 0xFF) != 0xFF) {
                throw new IllegalArgumentException("the marker chain is broken at byte " + at);
            }
            int marker = in[at + 1] & 0xFF;
            if (marker == EOI) {
                break;
            }
            // Standalone markers carry no length: TEM and the eight restarts.
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(in, at, 2);
                at += 2;
                continue;
            }
            if (at + 3 >= in.length) {
                throw new IllegalArgumentException("truncated segment header");
            }
            int length = ((in[at + 2] & 0xFF) << 8) | (in[at + 3] & 0xFF);
            if (length < 2 || at + 2 + length > in.length) {
                throw new IllegalArgumentException("truncated segment at byte " + at);
            }
            if (!dropped(marker)) {
                out.write(in, at, length + 2);
            }
            at += 2 + length;
            if (marker == SOS) {
                // Everything after the header is entropy-coded scan data, right
                // up to and including the EOI. Copy it verbatim.
                out.write(in, at, in.length - at);
                return out.toByteArray();
            }
        }
        throw new IllegalArgumentException("no scan data, so this JPEG is incomplete");
    }

    /** True when the segment carries something about the person or the device. */
    private static boolean dropped(int marker) {
        if (marker == APP0 || marker == APP2) {
            return false;
        }
        return marker == COMMENT || marker == APP1 || (marker >= 0xE3 && marker <= 0xEF);
    }
}
