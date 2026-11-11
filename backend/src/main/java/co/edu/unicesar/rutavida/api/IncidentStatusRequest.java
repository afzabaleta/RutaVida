package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.enums.IncidentStatus;

/** Body of PATCH /api/incidents/{id}/status (docs/api.md 9.3), for example {"status": "ACTIVO"}. */
public record IncidentStatusRequest(IncidentStatus status) {
}