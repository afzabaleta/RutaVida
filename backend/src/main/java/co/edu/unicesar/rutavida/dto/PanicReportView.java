package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

import java.time.LocalDateTime;

/**
 * A panic report as the control panel receives it (docs/api.md 16.2). It never carries the
 * device hash nor the user: only whether a police officer sent it.
 */
public record PanicReportView(
        long id,
        GeoPoint location,
        LocalDateTime reportedAt,
        boolean byPolice,
        Long emergencyTypeId,
        String description,
        PanicReportStatus status,
        Long emergencyId
) {

    public static PanicReportView of(PanicReport report, boolean byPolice) {
        return new PanicReportView(report.getId(), report.getLocation(), report.getReportedAt(),
                byPolice, report.getEmergencyTypeId(), report.getDescription(),
                report.getStatus(), report.getEmergencyId());
    }
}
