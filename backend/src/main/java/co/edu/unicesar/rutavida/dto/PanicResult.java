package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

/**
 * What the panic button page receives after a report (docs/api.md 16.1).
 *
 * @param reportId       the saved report
 * @param status         PENDIENTE while it waits for other people, VALIDADO when the alert was validated
 * @param emergencyId    the emergency of the alert; null while PENDIENTE
 * @param dispatchedUnit the unit sent on its own (for example EJEMPLO-AMB-01); null if none was sent
 * @param reportsNearby  distinct devices that reported within the radius and the window, this one included
 * @param reportsNeeded  devices needed to validate an alert (RN-14)
 */
public record PanicResult(
        long reportId,
        PanicReportStatus status,
        Long emergencyId,
        String dispatchedUnit,
        int reportsNearby,
        int reportsNeeded
) {
}