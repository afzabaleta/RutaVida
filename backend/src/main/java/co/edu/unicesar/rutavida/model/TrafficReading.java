
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;

/**
 * One traffic measurement of a road segment (TRAFICO).
 * It is history: nothing changes after it is created
 * except the initial identifier assignment.
 */
public class TrafficReading {

    private long id;
    private final long segmentId;
    private final LocalDateTime measuredAt;
    private final TrafficLevel level;
    private final Double averageSpeedKmh;
    private final double congestionFactor;
    private final TrafficSource source;

    public TrafficReading(long id, long segmentId,
                          LocalDateTime measuredAt, TrafficLevel level,
                          Double averageSpeedKmh, double congestionFactor,
                          TrafficSource source) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.segmentId = ValidationUtil.requirePositiveId(
                segmentId, "tramo");
        this.measuredAt = ValidationUtil.requireNonNull(
                measuredAt, "fecha de la medición");
        this.level = ValidationUtil.requireNonNull(
                level, "nivel de tráfico");

        if (averageSpeedKmh != null) {
            ValidationUtil.requireInRange(
                    averageSpeedKmh, 0, 9999.99, "velocidad promedio");
        }

        this.averageSpeedKmh = averageSpeedKmh;
        this.congestionFactor = ValidationUtil.requireInRange(
                congestionFactor, 1, 999.99, "factor de congestión");
        this.source = ValidationUtil.requireNonNull(
                source, "fuente");
    }

    /** Assigns an identifier only to an unsaved reading. */
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

    /** Simulated measurements must never be presented as real. */
    public boolean isSimulated() {
        return source == TrafficSource.SIMULATION;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof TrafficReading that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "TrafficReading{id=" + id
                + ", segmentId=" + segmentId
                + ", level=" + level
                + ", source=" + source + "}";
    }
}
