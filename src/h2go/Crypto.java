package h2go;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encryption for the data H2-Go saves in MySQL.
 *   Names:          AES-256-GCM with the secret key (the h2go.key file, or H2GO_KEY online). MySQL never sees the key.
 *   Passwords:      PBKDF2 with a random salt. One-way: a password can be checked, never read back.
 *   Sign-in tokens: SHA-256, so a copy of the sessions table can't be used to sign in.
 */
public class Crypto {

    private static final String AES_PREFIX = "aes:";
    private static final String PBKDF2_PREFIX = "pbkdf2";
    private static final int PBKDF2_ROUNDS = 65536;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    private Crypto(byte[] keyBytes) {
        key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Reads the 256-bit key from the file, or makes a random one and saves it there the first time. */
    public static Crypto loadKey(Path file) throws IOException {
        if (Files.exists(file)) {
            byte[] bytes = Base64.getDecoder().decode(Files.readString(file).trim());
            if (bytes.length != 32) {
                throw new IOException(file + " is not a 256-bit key.");
            }
            return new Crypto(bytes);
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        Files.writeString(file, Base64.getEncoder().encodeToString(bytes) + System.lineSeparator());
        System.out.println("Made a new secret key in " + file.toAbsolutePath()
                + ". Keep it with H2-Go: without it the names in MySQL can't be read.");
        return new Crypto(bytes);
    }

    /**
     * The key from text, for a website host where files don't last (H2GO_KEY). A 256-bit key in Base64, like the
     * line in h2go.key, is used as it is. Any other text is made into a 256-bit key with SHA-256.
     */
    public static Crypto fromText(String text) {
        text = text.trim();
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            if (bytes.length == 32) {
                return new Crypto(bytes);
            }
        } catch (IllegalArgumentException notBase64) {
            // not Base64: hashed below
        }
        return new Crypto(sha256Bytes(text));
    }

    /** "aes:" + Base64 of a random 12-byte IV followed by the encrypted text. */
    public String encrypt(String text) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, stored, 0, iv.length);
            System.arraycopy(sealed, 0, stored, iv.length, sealed.length);
            return AES_PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES is not available in this Java", e);
        }
    }

    /** The original text. Text without "aes:" was saved before encryption was added and is returned as it is. */
    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(stored.substring(AES_PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, 12));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return "(encrypted with another key)";
        }
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(AES_PREFIX);
    }

    /** A new password's hash: "pbkdf2$rounds$salt$hash", the salt and hash in Base64. */
    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return PBKDF2_PREFIX + "$" + PBKDF2_ROUNDS + "$" + b64.encodeToString(salt) + "$"
                + b64.encodeToString(pbkdf2(password, salt, PBKDF2_ROUNDS));
    }

    /** True when the password matches the saved hash: a PBKDF2 hash, or an old unsalted SHA-256 one. */
    public static boolean checkPassword(String password, String stored) {
        if (isOldHash(stored)) {
            return MessageDigest.isEqual(sha256(password).getBytes(StandardCharsets.UTF_8),
                    stored.getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = stored.split("\\$");
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] hash = Base64.getDecoder().decode(parts[3]);
        return MessageDigest.isEqual(pbkdf2(password, salt, Integer.parseInt(parts[1])), hash);
    }

    /** True for a hash from before salting (64 hex digits). H2-Go replaces it at that user's next sign-in. */
    public static boolean isOldHash(String stored) {
        return !stored.startsWith(PBKDF2_PREFIX + "$");
    }

    /** SHA-256 as 64 hex digits, the same as MySQL's SHA2(text, 256). */
    public static String sha256(String text) {
        StringBuilder hex = new StringBuilder();
        for (byte b : sha256Bytes(text)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static byte[] sha256Bytes(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available in this Java", e);
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int rounds) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this Java", e);
        }
    }
}
