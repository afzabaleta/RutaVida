
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;

/** An emergency reported in the RutaVida system. */
public class Emergency implements Locatable {

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

    public Emergency(long id, long emergencyTypeId,
                     long reportedByUserId, String addressReference,
                     GeoPoint location, LocalDateTime reportedAt,
                     Severity severity, String description,
                     EmergencyStatus status, LocalDateTime closedAt) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.emergencyTypeId = ValidationUtil.requirePositiveId(
                emergencyTypeId, "tipo de emergencia");
        this.reportedByUserId = ValidationUtil.requirePositiveId(
                reportedByUserId, "usuario que reporta");
        this.addressReference = ValidationUtil.requireText(
                addressReference, "dirección de referencia", 200);
        this.location = ValidationUtil.requireNonNull(
                location, "ubicación");
        this.reportedAt = ValidationUtil.requireNonNull(
                reportedAt, "fecha de reporte");
        this.severity = ValidationUtil.requireNonNull(
                severity, "gravedad");
        this.description = ValidationUtil.optionalText(
                description, "descripción", 400);
        this.status = ValidationUtil.requireNonNull(
                status, "estado");

        validateClosure(this.status, closedAt);
        this.closedAt = closedAt;
    }

    private void validateClosure(EmergencyStatus next,
                                 LocalDateTime closingTime) {
        if (next == EmergencyStatus.CLOSED) {
            if (closingTime == null) {
                throw new ValidationException(
                        "La fecha de cierre es obligatoria.");
            }
            if (closingTime.isBefore(reportedAt)) {
                throw new ValidationException(
                        "La fecha de cierre no puede ser anterior al reporte.");
            }
        } else if (closingTime != null) {
            throw new ValidationException(
                    "Solo las emergencias cerradas pueden tener fecha de cierre.");
        }
    }

    /** Assigns an identifier only to an unsaved emergency. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }
        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    public long getId() {
        return id;
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

    @Override
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

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    /** Changes status according to the existing enum transitions. */
    public void changeStatus(EmergencyStatus next, LocalDateTime at) {
        ValidationUtil.requireNonNull(next, "nuevo estado");

        if (!status.canChangeTo(next)) {
            throw new BusinessRuleException(
                    "No se puede cambiar la emergencia de "
                            + status.code() + " a " + next.code() + ".");
        }

        LocalDateTime nextClosedAt =
                next == EmergencyStatus.CLOSED ? at : null;

        validateClosure(next, nextClosedAt);

        this.status = next;
        this.closedAt = nextClosedAt;
    }

    /** Checks whether the emergency remains active. */
    public boolean isActive() {
        return status.isActive();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof Emergency that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Emergency{id=" + id
                + ", severity=" + severity
                + ", status=" + status + "}";
    }
}
