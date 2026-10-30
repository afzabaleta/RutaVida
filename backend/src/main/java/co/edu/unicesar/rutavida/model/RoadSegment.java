package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;

/**
 * One direction of a street between two road nodes (table TRAMO_VIAL).
 * Only data: the business rules live in the service layer.
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

    public RoadSegment(long id,
            long fromNodeId,
            long toNodeId,
            Long osmWayId,
            String roadName,
            double lengthMeters,
            double maxSpeedKmh,
            RoadSegmentStatus status) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.osmWayId = osmWayId;
        this.roadName = roadName;
        this.lengthMeters = lengthMeters;
        this.maxSpeedKmh = maxSpeedKmh;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
}
