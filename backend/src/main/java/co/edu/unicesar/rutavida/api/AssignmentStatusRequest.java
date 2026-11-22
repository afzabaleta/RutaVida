package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;

/**
 * Body of PATCH /api/dispatches/{id}/status (docs/api.md 6.6), for example {"status": "EN_RUTA"}.
 * To start the transfer (CU-11) it also carries the hospital: {"status": "TRASLADANDO", "hospitalId": 2}.
 *
 * @param status     the new status of the mission
 * @param hospitalId the destination hospital; only with TRASLADANDO
 */
public record AssignmentStatusRequest(AssignmentStatus status, Long hospitalId) {
}
