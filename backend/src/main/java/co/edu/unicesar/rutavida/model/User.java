package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import java.time.LocalDateTime;

/**
 * A system user (table USUARIO).
 * Only data: the business rules live in the service layer.
 */
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

    public User(long id,
            long roleId,
            String document,
            String firstNames,
            String lastNames,
            String email,
            String username,
            String passwordHash,
            RecordStatus status,
            LocalDateTime registeredAt) {
        this.id = id;
        this.roleId = roleId;
        this.document = document;
        this.firstNames = firstNames;
        this.lastNames = lastNames;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = status;
        this.registeredAt = registeredAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
}
