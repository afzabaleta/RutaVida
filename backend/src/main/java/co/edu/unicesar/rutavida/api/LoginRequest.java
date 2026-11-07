package co.edu.unicesar.rutavida.api;

/** Body of POST /api/auth/login (docs/api.md, 4.1). */
public record LoginRequest(String username, String password) {
}