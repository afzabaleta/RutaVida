
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

/**
 * One direction of a street between two road nodes (TRAMO_VIAL).
 * A two-way street is stored as two segments.
 */
public class RoadSegment {

    private long id;
    private final long fromNodeId;
    private final long toNodeId;
    private final Long osmWayId;
    private final String roadName;
    private final double lengthMeters;
    private final double maxSpeedKmh;
    private final RoadSegmentStatus status;

    public RoadSegment(long id, long fromNodeId, long toNodeId,
                       Long osmWayId, String roadName,
                       double lengthMeters, double maxSpeedKmh,
                       RoadSegmentStatus status) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.fromNodeId = ValidationUtil.requirePositiveId(
                fromNodeId, "nodo de origen");
        this.toNodeId = ValidationUtil.requirePositiveId(
                toNodeId, "nodo de destino");

        if (fromNodeId == toNodeId) {
            throw new ValidationException(
                    "Un tramo no puede empezar y terminar en el mismo nodo.");
        }

        if (osmWayId != null && osmWayId <= 0) {
            throw new ValidationException(
                    "El identificador de vía de OpenStreetMap no es válido.");
        }

        this.osmWayId = osmWayId;
        this.roadName = ValidationUtil.requireText(
                roadName, "nombre de la vía", 120);
        this.lengthMeters = ValidationUtil.requireInRange(
                lengthMeters, 0.01, 99_999_999.99, "distancia");
        this.maxSpeedKmh = ValidationUtil.requireInRange(
                maxSpeedKmh, 0.01, 120, "velocidad máxima");
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
    }

    /** Assigns an identifier only to an unsaved segment. */
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

    public long getFromNodeId() {
        return fromNodeId;
    }

    public long getToNodeId() {
        return toNodeId;
    }

    public Long getOsmWayId() {
        return osmWayId;
    }

    public String getRoadName() {
        return roadName;
    }

    public double getLengthMeters() {
        return lengthMeters;
    }

    public double getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public RoadSegmentStatus getStatus() {
        return status;
    }

    /**
     * Seconds to drive the segment at maximum speed, without traffic.
     * Same formula as the Oracle virtual column tiempo_base_seg.
     */
    public double getBaseTimeSeconds() {
        return lengthMeters / (maxSpeedKmh / 3.6);
    }

    /**
     * Only CLOSED segments forbid driving.
     * RESTRICTED segments still allow vehicles to pass.
     */
    public boolean isOpen() {
        return status != RoadSegmentStatus.CLOSED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof RoadSegment that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "RoadSegment{id=" + id
                + ", from=" + fromNodeId
                + ", to=" + toNodeId
                + ", status=" + status + "}";
    }
}
