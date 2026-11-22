package co.edu.unicesar.rutavida.dto;

/**
 * The user as the web sees it: never the password hash nor the document.
 */
public record SessionUser(long id, String username, String firstNames, String lastNames) {
}