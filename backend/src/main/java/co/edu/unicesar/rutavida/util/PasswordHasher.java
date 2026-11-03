
package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.exception.ValidationException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes and verifies passwords with PBKDF2-HMAC-SHA256 (passwords are never stored in plain text).
 *
 * Stored format:
 * pbkdf2_sha256$iterations$saltBase64$hashBase64
 */
public final class PasswordHasher {

    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final int MIN_ITERATIONS = 1_000;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /**
     * Tests use fewer iterations to execute faster.
     */
    public PasswordHasher(int iterations) {
        if (iterations < MIN_ITERATIONS) {
            throw new IllegalArgumentException(
                    "iterations must be at least " + MIN_ITERATIONS);
        }
        this.iterations = iterations;
    }

    public String hash(char[] raw) {
        if (raw == null || raw.length == 0) {
            throw new ValidationException(
                    "La contraseña es obligatoria.");
        }

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);

        byte[] key = derive(raw, salt, iterations);
        Base64.Encoder base64 = Base64.getEncoder();

        return PREFIX + "$" + iterations + "$"
                + base64.encodeToString(salt) + "$"
                + base64.encodeToString(key);
    }

    public boolean matches(char[] raw, String stored) {
        if (raw == null || raw.length == 0 || stored == null) {
            return false;
        }

        String[] parts = stored.split("\\$");

        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int storedIterations = Integer.parseInt(parts[1]);

            if (storedIterations < MIN_ITERATIONS) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(raw, salt, storedIterations);

            return MessageDigest.isEqual(expected, actual);

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(
            char[] raw, byte[] salt, int iterations) {

        PBEKeySpec spec =
                new PBEKeySpec(raw, salt, iterations, KEY_BITS);

        try {
            return SecretKeyFactory
                    .getInstance(ALGORITHM)
                    .generateSecret(spec)
                    .getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "PBKDF2 is not available in this JDK", e);

        } finally {
            spec.clearPassword();
        }
    }
}
