package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

import java.time.LocalDateTime;

/**
 * One press of the panic button (table REPORTE_PANICO, CU-22).
 * Only data: the validation rules live in the service layer (PanicService).
 */
public class PanicReport {

    private long id;
    private final GeoPoint location;
    private final LocalDateTime reportedAt;
    private final String deviceId;
    private final Long userId;
    private final Long emergencyTypeId;
    private final String description;
    private PanicReportStatus status;
    private Long emergencyId;

    /**
     * @param deviceId        SHA-256 hash of the id the browser generates; never the raw id
     * @param userId          the user of the session (for example a police officer); null if anonymous
     * @param emergencyTypeId what the citizen chose; null if he did not choose
     * @param emergencyId     the emergency created when the alert was validated; null before that
     */
    public PanicReport(long id, GeoPoint location, LocalDateTime reportedAt, String deviceId, Long userId,
                       Long emergencyTypeId, String description, PanicReportStatus status, Long emergencyId) {
        this.id = id;
        this.location = location;
        this.reportedAt = reportedAt;
        this.deviceId = deviceId;
        this.userId = userId;
        this.emergencyTypeId = emergencyTypeId;
        this.description = description;
        this.status = status;
        this.emergencyId = emergencyId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEmergencyTypeId() {
        return emergencyTypeId;
    }

    public String getDescription() {
        return description;
    }

    public PanicReportStatus getStatus() {
        return status;
    }

    public void setStatus(PanicReportStatus status) {
        this.status = status;
    }

    public Long getEmergencyId() {
        return emergencyId;
    }

    public void setEmergencyId(Long emergencyId) {
        this.emergencyId = emergencyId;
    }
}
