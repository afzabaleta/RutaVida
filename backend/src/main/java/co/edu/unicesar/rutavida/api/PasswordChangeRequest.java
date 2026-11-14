package co.edu.unicesar.rutavida.api;

/** Body of POST /api/auth/password (docs/api.md 15.5): the current password and the new one. */
public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
