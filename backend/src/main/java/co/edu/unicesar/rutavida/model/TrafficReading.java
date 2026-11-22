package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import java.time.LocalDateTime;

/**
 * One traffic measurement of a road segment (table TRAFICO).
 * Only data: the business rules live in the service layer.
 */
public class TrafficReading {

    private long id;
    private final long segmentId;
    private final LocalDateTime measuredAt;
    private final TrafficLevel level;
    private final Double averageSpeedKmh;
    private final double congestionFactor;
    private final TrafficSource source;

    public TrafficReading(long id,
            long segmentId,
            LocalDateTime measuredAt,
            TrafficLevel level,
            Double averageSpeedKmh,
            double congestionFactor,
            TrafficSource source) {
        this.id = id;
        this.segmentId = segmentId;
        this.measuredAt = measuredAt;
        this.level = level;
        this.averageSpeedKmh = averageSpeedKmh;
        this.congestionFactor = congestionFactor;
        this.source = source;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSegmentId() {
        return segmentId;
    }

    public LocalDateTime getMeasuredAt() {
        return measuredAt;
    }

    public TrafficLevel getLevel() {
        return level;
    }

    public Double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public double getCongestionFactor() {
        return congestionFactor;
    }

    public TrafficSource getSource() {
        return source;
    }
}
