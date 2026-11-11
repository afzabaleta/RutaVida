package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.time.LocalDateTime;

/**
 * A user as the screens see it (CU-03).
 * The password hash never leaves the service layer.
 */
public record UserView(
        long id,
        long roleId,
        String document,
        String firstNames,
        String lastNames,
        String email,
        String username,
        RecordStatus status,
        LocalDateTime registeredAt
) {

    public static UserView of(User user) {
        return new UserView(
                user.getId(),
                user.getRoleId(),
                user.getDocument(),
                user.getFirstNames(),
                user.getLastNames(),
                user.getEmail(),
                user.getUsername(),
                user.getStatus(),
                user.getRegisteredAt()
        );
    }
}