
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;
import java.util.Locale;

/** A system user (table USUARIO). */
public class User {

    private long id;
    private final long roleId;
    private final String document;
    private final String firstNames;
    private final String lastNames;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final RecordStatus status;
    private final LocalDateTime registeredAt;

    public User(long id, long roleId, String document,
                String firstNames, String lastNames, String email,
                String username, String passwordHash,
                RecordStatus status, LocalDateTime registeredAt) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.roleId = ValidationUtil.requirePositiveId(
                roleId, "rol");

        this.document = ValidationUtil.requireText(
                document, "documento", 20);
        if (!this.document.matches("[a-zA-Z0-9]{5,20}")) {
            throw new ValidationException(
                    "El documento debe tener entre 5 y 20 letras o números.");
        }

        this.firstNames = ValidationUtil.requireText(
                firstNames, "nombres", 80);
        this.lastNames = ValidationUtil.requireText(
                lastNames, "apellidos", 80);

        this.email = ValidationUtil.requireText(
                email, "correo", 120).toLowerCase(Locale.ROOT);
        if (!this.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ValidationException(
                    "El correo electrónico no tiene un formato válido.");
        }

        this.username = ValidationUtil.requireText(
                username, "usuario", 40).toLowerCase(Locale.ROOT);
        if (!this.username.matches("[a-z0-9._]{4,40}")) {
            throw new ValidationException(
                    "El usuario debe tener entre 4 y 40 caracteres válidos.");
        }

        this.passwordHash = ValidationUtil.requireText(
                passwordHash, "contraseña protegida", 255);

        this.status = ValidationUtil.requireNonNull(
                status, "estado");
        this.registeredAt = ValidationUtil.requireNonNull(
                registeredAt, "fecha de registro");
    }

    /** Assigns an identifier only to an unsaved user. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }

        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    public long getId() {
        return id;
    }

    public long getRoleId() {
        return roleId;
    }

    public String getDocument() {
        return document;
    }

    public String getFirstNames() {
        return firstNames;
    }

    public String getLastNames() {
        return lastNames;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public String getFullName() {
        return firstNames + " " + lastNames;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof User that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id
                + ", username=" + username
                + ", status=" + status + "}";
    }
}

