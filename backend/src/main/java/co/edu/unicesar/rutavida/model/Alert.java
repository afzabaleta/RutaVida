
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.AlertType;
import java.time.LocalDateTime;

/**
 * An operational alert for one user.
 * Only data: business rules live in AlertService.
 */
public class Alert {

    private long id;
    private final Long emergencyId;
    private final long userId;
    private final AlertType type;
    private final String message;
    private final LocalDateTime createdAt;
    private boolean read;

    public Alert(long id,
                 Long emergencyId,
                 long userId,
                 AlertType type,
                 String message,
                 LocalDateTime createdAt,
                 boolean read) {
        this.id = id;
        this.emergencyId = emergencyId;
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
        this.read = read;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getEmergencyId() {
        return emergencyId;
    }

    public long getUserId() {
        return userId;
    }

    public AlertType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
