package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One calculated route of a unit (table RUTA) with its ordered segments (RUTA_TRAMO).
 * Only data: the business rules live in the service layer.
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

    public Route(long id,
            long emergencyId,
            long vehicleId,
            Long destinationHospitalId,
            RouteType type,
            double distanceKm,
            double estimatedMinutes,
            double totalCost,
            LocalDateTime generatedAt,
            boolean recommended,
            RouteStatus status,
            List<RouteSegment> segments) {
        this.id = id;
        this.emergencyId = emergencyId;
        this.vehicleId = vehicleId;
        this.destinationHospitalId = destinationHospitalId;
        this.type = type;
        this.distanceKm = distanceKm;
        this.estimatedMinutes = estimatedMinutes;
        this.totalCost = totalCost;
        this.generatedAt = generatedAt;
        this.recommended = recommended;
        this.status = status;
        this.segments = segments;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public List<RouteSegment> getSegments() {
        return segments;
    }
}
