package co.edu.unicesar.rutavida.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the web receives after logging in (POST /api/auth/login) or when it asks for the
 * current session (GET /api/auth/session, with {@code token} null). See docs/api.md, 4.1.
 *
 * @param token              value for the header Authorization: Bearer (null in GET /api/auth/session)
 * @param expiresAt          end of the 8 hour limit
 * @param idleTimeoutMinutes the session also ends after this many minutes without use
 * @param user               the logged-in user
 * @param role               role name, e.g. OPERADOR_CENTRAL
 * @param permissions        "MODULE:ACTION" pairs, e.g. "EMERGENCIAS:GESTIONAR"; only to show or hide buttons
 */
public record LoginResponse(String token, LocalDateTime expiresAt, long idleTimeoutMinutes,
                            SessionUser user, String role, List<String> permissions) {

    public LoginResponse {
        permissions = List.copyOf(permissions);
    }
}