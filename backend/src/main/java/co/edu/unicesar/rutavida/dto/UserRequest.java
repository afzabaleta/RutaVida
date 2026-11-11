package co.edu.unicesar.rutavida.dto;

/**
 * Data of a new user (CU-03).
 * The password travels as char[] so the service can erase it
 * after hashing it; it is never stored nor returned.
 */
public record UserRequest(
        long roleId,
        String document,
        String firstNames,
        String lastNames,
        String email,
        String username,
        char[] password
) {
}