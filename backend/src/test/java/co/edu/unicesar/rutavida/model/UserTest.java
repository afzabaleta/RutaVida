
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final LocalDateTime REGISTERED_AT =
            LocalDateTime.of(2026, 10, 15, 10, 0);

    private User createUser(String document, String email,
                            String username, String passwordHash) {
        return new User(
                0, 1, document,
                "Sherly", "Perez", email,
                username, passwordHash,
                RecordStatus.ACTIVE, REGISTERED_AT);
    }

    @Test
    void createsAValidUserAndNormalizesEmailAndUsername() {
        User user = createUser(
                "AB12345", "SHERLY@EXAMPLE.COM",
                "SHERLY_01", "hashed-value");

        assertEquals(0, user.getId());
        assertEquals("AB12345", user.getDocument());
        assertEquals("sherly@example.com", user.getEmail());
        assertEquals("sherly_01", user.getUsername());
        assertEquals("Sherly Perez", user.getFullName());
        assertTrue(user.isActive());
    }

    @Test
    void rejectsInvalidDocument() {
        assertThrows(ValidationException.class, () ->
                createUser("123", "test@example.com",
                        "usuario1", "hashed-value"));

        assertThrows(ValidationException.class, () ->
                createUser("ABC-123", "test@example.com",
                        "usuario1", "hashed-value"));
    }

    @Test
    void rejectsInvalidEmail() {
        assertThrows(ValidationException.class, () ->
                createUser("AB12345", "correo-invalido",
                        "usuario1", "hashed-value"));

        assertThrows(ValidationException.class, () ->
                createUser("AB12345", "correo@",
                        "usuario1", "hashed-value"));
    }

    @Test
    void rejectsInvalidUsername() {
        assertThrows(ValidationException.class, () ->
                createUser("AB12345", "test@example.com",
                        "abc", "hashed-value"));

        assertThrows(ValidationException.class, () ->
                createUser("AB12345", "test@example.com",
                        "nombre usuario", "hashed-value"));
    }

    @Test
    void rejectsInvalidRoleIdAndRequiredFields() {
        assertThrows(ValidationException.class, () ->
                new User(0, 0, "AB12345",
                        "Sherly", "Perez", "test@example.com",
                        "usuario1", "hashed-value",
                        RecordStatus.ACTIVE, REGISTERED_AT));

        assertThrows(ValidationException.class, () ->
                createUser("AB12345", "test@example.com",
                        "usuario1", " "));
    }

    @Test
    void toStringNeverExposesPasswordHash() {
        User user = createUser(
                "AB12345", "test@example.com",
                "usuario1", "secret-hash-value");

        assertFalse(user.toString().contains("secret-hash-value"));
        assertTrue(user.toString().contains("usuario1"));
    }

    @Test
    void assignIdOnlyWorksOnce() {
        User user = createUser(
                "AB12345", "test@example.com",
                "usuario1", "hashed-value");

        user.assignId(12);

        assertEquals(12, user.getId());

        assertThrows(IllegalStateException.class,
                () -> user.assignId(13));
    }

    @Test
    void inactiveUserIsNotActive() {
        User user = new User(
                0, 1, "AB12345",
                "Sherly", "Perez", "test@example.com",
                "usuario1", "hashed-value",
                RecordStatus.INACTIVE, REGISTERED_AT);

        assertFalse(user.isActive());
    }
}
