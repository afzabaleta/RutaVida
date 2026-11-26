package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;

import java.time.LocalDateTime;

/**
 * Operational context shown only to the authenticated field driver.  It deliberately contains
 * one unit and its own mission, never the fleet positions shown in the coordination map.
 */
public record DriverMission(
        DriverUnit unit,
        Long assignmentId,
        Long emergencyId,
        String emergencyAddress,
        GeoPoint emergencyLocation,
        AssignmentStatus status,
        LocalDateTime departedAt,
        LocalDateTime arrivedAt,
        LocalDateTime finishedAt,
        String emergencyType,
        String emergencyDescription,
        LocalDateTime reportedAt,
        TrackedMission tracking,
        boolean canTransfer
) {
    public boolean active() {
        return assignmentId != null;
    }
}
