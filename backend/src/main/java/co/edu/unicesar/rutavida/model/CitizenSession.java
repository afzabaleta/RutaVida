package co.edu.unicesar.rutavida.model;

import java.time.LocalDateTime;

/**
 * The 30-day session of the phone of a citizen (table SESION_CIUDADANO, DT-13). Only the
 * SHA-256 hash of the token is kept; the token itself is only on the phone.
 * Only data: the rules live in CitizenService.
 */
public class CitizenSession {

    private long id;
    private final long userId;
    private final String tokenHash;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final boolean revoked;

    public CitizenSession(long id, long userId, String tokenHash,
                          LocalDateTime createdAt, LocalDateTime expiresAt, boolean revoked) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
}
