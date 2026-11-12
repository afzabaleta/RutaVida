package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;

/**
 * Body of PATCH /api/dispatches/{id}/status (docs/api.md 6.6), for example {"status": "EN_RUTA"}.
 * The hospitalId of TRASLADANDO is added with CU-11 (K7b); until then DispatchService answers 400.
 */
public record AssignmentStatusRequest(AssignmentStatus status) {
}