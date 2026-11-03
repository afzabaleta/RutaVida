package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import java.time.LocalDateTime;

/**
 * An emergency reported in the system (table EMERGENCIA).
 * Only data: the business rules live in the service layer.
 */
public class Emergency {

    private long id;
    private final long emergencyTypeId;
    private final long reportedByUserId;
    private final String addressReference;
    private final GeoPoint location;
    private final LocalDateTime reportedAt;
    private final Severity severity;
    private final String description;
    private EmergencyStatus status;
    private LocalDateTime closedAt;

    public Emergency(long id,
            long emergencyTypeId,
            long reportedByUserId,
            String addressReference,
            GeoPoint location,
            LocalDateTime reportedAt,
            Severity severity,
            String description,
            EmergencyStatus status,
            LocalDateTime closedAt) {
        this.id = id;
        this.emergencyTypeId = emergencyTypeId;
        this.reportedByUserId = reportedByUserId;
        this.addressReference = addressReference;
        this.location = location;
        this.reportedAt = reportedAt;
        this.severity = severity;
        this.description = description;
        this.status = status;
        this.closedAt = closedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getEmergencyTypeId() {
        return emergencyTypeId;
    }

    public long getReportedByUserId() {
        return reportedByUserId;
    }

    public String getAddressReference() {
        return addressReference;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public EmergencyStatus getStatus() {
        return status;
    }

    public void setStatus(EmergencyStatus status) {
        this.status = status;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
