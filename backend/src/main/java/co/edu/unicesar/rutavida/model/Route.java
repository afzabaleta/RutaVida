
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One calculated route of a unit (RUTA) with its ordered segments
 * (RUTA_TRAMO).
 *
 * The main route and the alternatives are rows of the same table.
 * Without a destination hospital the route goes to the emergency;
 * with one, it is a transfer.
 *
 * The route does not change after it is created:
 * RouteDAO.updateStatus changes its status in Oracle,
 * and a recalculation creates a new Route.
 */
public class Route {

    private long id;
    private final long emergencyId;
    private final long vehicleId;
    private final Long destinationHospitalId;
    private final RouteType type;
    private final double distanceKm;
    private final double estimatedMinutes;
    private final double totalCost;
    private final LocalDateTime generatedAt;
    private final boolean recommended;
    private final RouteStatus status;
    private final List<RouteSegment> segments;

    public Route(long id, long emergencyId, long vehicleId,
                 Long destinationHospitalId, RouteType type,
                 double distanceKm, double estimatedMinutes,
                 double totalCost, LocalDateTime generatedAt,
                 boolean recommended, RouteStatus status,
                 List<RouteSegment> segments) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        if (destinationHospitalId != null) {
            ValidationUtil.requirePositiveId(
                    destinationHospitalId, "hospital de destino");
        }

        this.id = id;
        this.emergencyId = ValidationUtil.requirePositiveId(
                emergencyId, "emergencia");
        this.vehicleId = ValidationUtil.requirePositiveId(
                vehicleId, "unidad");
        this.destinationHospitalId = destinationHospitalId;
        this.type = ValidationUtil.requireNonNull(
                type, "tipo de ruta");
        this.distanceKm = ValidationUtil.requireInRange(
                distanceKm, 0, 999_999.99, "distancia");
        this.estimatedMinutes = ValidationUtil.requireInRange(
                estimatedMinutes, 0, 999_999.99, "tiempo estimado");
        this.totalCost = ValidationUtil.requireInRange(
                totalCost, 0, 9_999_999_999.99, "costo total");
        this.generatedAt = ValidationUtil.requireNonNull(
                generatedAt, "fecha de generación");
        this.recommended = recommended;
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
        this.segments = validateSegments(segments);
    }

    /**
     * Requires at least one segment, numbered 1, 2, 3...
     * without gaps.
     */
    private static List<RouteSegment> validateSegments(
            List<RouteSegment> segments) {

        ValidationUtil.requireNonNull(
                segments, "tramos de la ruta");

        if (segments.isEmpty()) {
            throw new ValidationException(
                    "Una ruta debe tener al menos un tramo.");
        }

        for (int i = 0; i < segments.size(); i++) {
            RouteSegment segment = ValidationUtil.requireNonNull(
                    segments.get(i), "tramo de la ruta");

            if (segment.order() != i + 1) {
                throw new ValidationException(
                        "Los tramos de la ruta deben estar en orden "
                                + "1, 2, 3... sin saltos.");
            }
        }

        return List.copyOf(segments);
    }

    /** Assigns an identifier only to an unsaved route. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }

        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    /** True when the route is a transfer to a hospital. */
    public boolean isToHospital() {
        return destinationHospitalId != null;
    }

    /** Checks whether a road segment belongs to the route. */
    public boolean usesSegment(long segmentId) {
        return segments.stream()
                .anyMatch(segment -> segment.segmentId() == segmentId);
    }

    public long getId() {
        return id;
    }

    public long getEmergencyId() {
        return emergencyId;
    }

    public long getVehicleId() {
        return vehicleId;
    }

    public Long getDestinationHospitalId() {
        return destinationHospitalId;
    }

    public RouteType getType() {
        return type;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public double getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public RouteStatus getStatus() {
        return status;
    }

    /** Returns the ordered, unmodifiable segment list. */
    public List<RouteSegment> getSegments() {
        return segments;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof Route that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Route{id=" + id
                + ", emergencyId=" + emergencyId
                + ", type=" + type
                + ", segments=" + segments.size()
                + ", status=" + status + "}";
    }
}
