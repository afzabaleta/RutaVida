package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.UserSession;

import java.time.LocalDateTime;

/**
 * The 30-day session of the phone of a citizen (DT-13), as the web session controller needs
 * it. Not sent to the web as it is: the web receives a LoginResponse.
 *
 * @param token     the token for the phone; only when the session starts, null when it is found
 * @param session   the user and the role
 * @param startedAt when the citizen logged in on that phone
 * @param expiresAt when the phone has to log in again
 */
public record CitizenLogin(
        String token,
        UserSession session,
        LocalDateTime startedAt,
        LocalDateTime expiresAt
) {
}
