package se.blimark.fieldkit.v2;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import se.blimark.fieldkit.Backup;

/**
 * Everything the app remembers: one preference file, one sealed journal file,
 * and the backup that carries both.
 *
 * The journal is sealed with AES-256-GCM from a key stretched out of the
 * passphrase with PBKDF2. There is no keystore key and no recovery: lose the
 * passphrase and the notes are gone, which is the only honest promise an offline
 * app can make.
 */
public final class Store {

    public static final String NAME = "HARALD BLIMARK";

    private static final String PREFS = "fieldkit";
    private static final String JOURNAL_FILE = "journal.fk";
    private static final int ITERATIONS = 60000;
    private static final int SALT = 16;
    private static final int IV = 12;
    private static final int TAG_BITS = 128;

    private Store() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String get(Context context, String key, String fallback) {
        String value = prefs(context).getString(key, fallback);
        return value == null ? fallback : value;
    }

    public static void put(Context context, String key, String value) {
        prefs(context).edit().putString(key, value == null ? "" : value).apply();
    }

    public static double number(Context context, String key, double fallback) {
        String raw = get(context, key, null);
        if (raw == null || raw.length() == 0) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The profile's display name, used in the header and the ICE card. */
    public static String owner(Context context) {
        String name = get(context, "owner", NAME);
        return name.trim().length() == 0 ? NAME : name.trim();
    }

    public static Map<String, String> all(Context context) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            Object value = entry.getValue();
            out.put(entry.getKey(), value == null ? "" : String.valueOf(value));
        }
        return out;
    }

    public static String backupText(Context context) {
        return Backup.encode(all(context));
    }

    /** Replaces every stored value with the backup's contents. */
    public static int restoreText(Context context, String text) {
        Map<String, String> values = Backup.decode(text);
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.clear();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.apply();
        return values.size();
    }

    // ---- journal -----------------------------------------------------------

    public static File journalFile(Context context) {
        return new File(context.getFilesDir(), JOURNAL_FILE);
    }

    public static boolean hasJournal(Context context) {
        File file = journalFile(context);
        return file.exists() && file.length() > 0;
    }

    public static String seal(String passphrase, String plain) throws Exception {
        byte[] salt = new byte[SALT];
        byte[] iv = new byte[IV];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), new GCMParameterSpec(TAG_BITS, iv));
        byte[] sealed = cipher.doFinal(plain.getBytes("UTF-8"));
        byte[] out = new byte[SALT + IV + sealed.length];
        System.arraycopy(salt, 0, out, 0, SALT);
        System.arraycopy(iv, 0, out, SALT, IV);
        System.arraycopy(sealed, 0, out, SALT + IV, sealed.length);
        return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP);
    }

    /** Throws when the passphrase is wrong: GCM refuses to authenticate. */
    public static String open(String passphrase, String sealedBase64) throws Exception {
        byte[] all = android.util.Base64.decode(sealedBase64, android.util.Base64.NO_WRAP);
        if (all.length < SALT + IV + 16) {
            throw new IOException("the sealed note is too short to be one");
        }
        byte[] salt = new byte[SALT];
        byte[] iv = new byte[IV];
        System.arraycopy(all, 0, salt, 0, SALT);
        System.arraycopy(all, SALT, iv, 0, IV);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = cipher.doFinal(all, SALT + IV, all.length - SALT - IV);
        return new String(plain, "UTF-8");
    }

    public static void writeJournal(Context context, String sealed) throws IOException {
        FileOutputStream out = new FileOutputStream(journalFile(context));
        try {
            out.write(sealed.getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
    }

    public static String readJournal(Context context) throws IOException {
        File file = journalFile(context);
        byte[] buffer = new byte[(int) file.length()];
        FileInputStream in = new FileInputStream(file);
        try {
            int read = 0;
            while (read < buffer.length) {
                int step = in.read(buffer, read, buffer.length - read);
                if (step <= 0) {
                    break;
                }
                read += step;
            }
        } finally {
            in.close();
        }
        return new String(buffer, "UTF-8");
    }

    private static SecretKeySpec key(String passphrase, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] raw = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(raw, "AES");
    }
}
