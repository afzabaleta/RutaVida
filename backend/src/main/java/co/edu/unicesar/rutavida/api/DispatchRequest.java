package co.edu.unicesar.rutavida.api;

/**
 * Body of POST /api/routes/preview and POST /api/dispatches (docs/api.md 6.2 and 6.3), for
 * example {"emergencyId": 15, "vehicleId": 1}. Objects, not long, so a missing id is null.
 */
public record DispatchRequest(Long emergencyId, Long vehicleId) {
}