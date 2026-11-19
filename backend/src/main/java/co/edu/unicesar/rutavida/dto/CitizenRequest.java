package co.edu.unicesar.rutavida.dto;

/**
 * A person registers to use the panic button (CU-23, docs/api.md 16.5). The password is a
 * char[] so the service can erase it after hashing it.
 *
 * @param dataConsent the person authorized the processing of the personal data (Ley 1581
 *                    de 2012); without it there is no registration
 */
public record CitizenRequest(
        String document,
        String firstNames,
        String lastNames,
        String email,
        String username,
        char[] password,
        Boolean dataConsent
) {
}
