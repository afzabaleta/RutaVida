package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.enums.RecordStatus;

/**
 * Body of PATCH /api/users/{id}/status, /api/stations/{id}/status and /api/hospitals/{id}/status
 * (docs/api.md 15): {"status": "A"} to activate, {"status": "I"} to deactivate.
 */
public record RecordStatusRequest(RecordStatus status) {
}
