package co.edu.unicesar.rutavida.model;

import java.time.LocalDateTime;

/** The authenticated user, its role and when the session started. */
public record UserSession(User user, Role role, LocalDateTime startedAt) {
}
