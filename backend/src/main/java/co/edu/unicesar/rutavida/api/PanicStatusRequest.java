package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

/** Body of PATCH /api/panic/reports/{id}/status (docs/api.md 16.3): only DESCARTADO is accepted. */
public record PanicStatusRequest(PanicReportStatus status) {
}
