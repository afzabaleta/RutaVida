package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

/**
 * What the panic button page receives after a report (docs/api.md 16.1).
 *
 * @param reportId       the saved report
 * @param status         VALIDADO after an authenticated report
 * @param emergencyId    the new or existing open emergency
 * @param dispatchedUnit the eligible ambulance code, if assigned by this report
 * @param reportsNearby  compatibility field, now 1
 * @param reportsNeeded  compatibility field, now 1
 * @param createdEmergency whether this report created the incident
 * @param dispatchedVehicleId the assigned vehicle identifier, if any
 */
public record PanicResult(
        long reportId,
        PanicReportStatus status,
        Long emergencyId,
        String dispatchedUnit,
        int reportsNearby,
        int reportsNeeded,
        boolean createdEmergency,
        Long dispatchedVehicleId
) {
}
