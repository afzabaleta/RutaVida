
package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pbkdf2PasswordHasherTest {

    // Use fewer iterations so the tests run quickly.
    private final PasswordHasher hasher =
            new Pbkdf2PasswordHasher(1_000);

    @Test
    void theRightPasswordMatchesAndAWrongOneDoesNot() {
        String stored = hasher.hash("Clave-Segura1".toCharArray());

        assertTrue(hasher.matches("Clave-Segura1".toCharArray(), stored));
        assertFalse(hasher.matches("clave-segura1".toCharArray(), stored));
        assertFalse(hasher.matches("Clave-Segura2".toCharArray(), stored));
    }

    @Test
    void theSamePasswordGivesADifferentHashEachTime() {
        String first = hasher.hash("Clave-Segura1".toCharArray());
        String second = hasher.hash("Clave-Segura1".toCharArray());

        assertNotEquals(first, second);
        assertTrue(hasher.matches("Clave-Segura1".toCharArray(), first));
        assertTrue(hasher.matches("Clave-Segura1".toCharArray(), second));
    }

    @Test
    void theHashHasTheDocumentedFormatAndNeverContainsThePassword() {
        String stored = hasher.hash("Clave-Segura1".toCharArray());
        String[] parts = stored.split("\\$");

        assertEquals(4, parts.length);
        assertEquals("pbkdf2_sha256", parts[0]);
        assertEquals("1000", parts[1]);
        assertFalse(stored.contains("Clave-Segura1"));

        assertTrue(stored.length() <= 255,
                "must fit in USUARIO.password_hash");
    }

    @Test
    void aHashMadeWithOtherIterationsIsStillChecked() {
        String stored = new Pbkdf2PasswordHasher(2_000)
                .hash("Clave-Segura1".toCharArray());

        assertTrue(hasher.matches("Clave-Segura1".toCharArray(), stored));
    }

    @Test
    void badInputNeverThrowsInMatches() {
        String stored = hasher.hash("Clave-Segura1".toCharArray());

        assertFalse(hasher.matches(null, stored));
        assertFalse(hasher.matches(new char[0], stored));
        assertFalse(hasher.matches("Clave-Segura1".toCharArray(), null));
        assertFalse(hasher.matches("Clave-Segura1".toCharArray(), "texto-plano"));
        assertFalse(hasher.matches(
                "Clave-Segura1".toCharArray(),
                "pbkdf2_sha256$abc$@@@$@@@"));
        assertFalse(hasher.matches(
                "Clave-Segura1".toCharArray(),
                "pbkdf2_sha256$10$AAAA$AAAA"));
    }

    @Test
    void emptyPasswordAndTooFewIterationsAreRejected() {
        assertThrows(ValidationException.class,
                () -> hasher.hash(new char[0]));

        assertThrows(ValidationException.class,
                () -> hasher.hash(null));

        assertThrows(IllegalArgumentException.class,
                () -> new Pbkdf2PasswordHasher(999));
    }
}
