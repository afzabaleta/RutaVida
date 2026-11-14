package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;

/**
 * A mission still open, for the live map (docs/api.md 8.1).
 *
 * @param activeRouteId the route the unit follows now; null if it has none (it arrived)
 * @param routesVersion routes saved for this mission so far: it grows when the system
 *                      recalculates (CU-17), and then the web asks for the routes again (6.4)
 */
public record TrackedMission(
        long assignmentId,
        long emergencyId,
        long vehicleId,
        AssignmentStatus status,
        Long activeRouteId,
        int routesVersion
) {
}
