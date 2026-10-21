
package co.edu.unicesar.rutavida.util;

/**
 * Defines password hashing and verification operations.
 * Passwords must never be stored in plain text.
 */
public interface PasswordHasher {

    /**
     * Creates a password hash using a new random salt.
     */
    String hash(char[] raw);

    /**
     * Checks whether a password matches a stored hash.
     * Returns false for invalid input.
     */
    boolean matches(char[] raw, String stored);
}
