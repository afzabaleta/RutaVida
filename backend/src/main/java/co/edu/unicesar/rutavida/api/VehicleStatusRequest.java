package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;

/** Body of PATCH /api/units/{id}/status (docs/api.md 15.8), for example {"status": "MANTENIMIENTO"}. */
public record VehicleStatusRequest(VehicleOperationalStatus status) {
}
