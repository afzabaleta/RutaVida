package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.CitizenSession;

import java.time.LocalDateTime;
import java.util.Optional;

/** Sessions of the phones of the citizens (table SESION_CIUDADANO of 08_ciudadanos.sql). */
public interface CitizenSessionDAO {

    /** Saves a new session (id 0), assigns the generated id with {@code setId} and returns it. */
    long insert(CitizenSession session);

    /** The session of a token hash that is not revoked and has not expired at {@code now}. */
    Optional<CitizenSession> findValid(String tokenHash, LocalDateTime now);

    /** Logout of one phone. Returns how many sessions it revoked (0 or 1). */
    int revoke(String tokenHash);

    /** Every phone of the user is logged out (the account was blocked). Returns how many. */
    int revokeAllOf(long userId);
}
