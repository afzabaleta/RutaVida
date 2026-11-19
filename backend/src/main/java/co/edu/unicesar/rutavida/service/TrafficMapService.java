package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadMapDAO;
import co.edu.unicesar.rutavida.dao.TrafficMapDAO;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.dto.TrafficSegmentView;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * The traffic layer of the map (docs/api.md 8.2): the segments whose last reading in TRAFICO
 * is MODERADO or CONGESTIONADO. TrafficRecorder saves a level again every 10 minutes while it
 * lasts, so a 15-minute window (the same as the dashboard) shows the current state; when the
 * simulation stops, the layer empties by itself after those 15 minutes.
 */
public class TrafficMapService {

    /** How far back a reading still counts as current (DashboardService uses the same window). */
    static final Duration WINDOW = Duration.ofMinutes(15);

    static final String ONLY_CONGESTION = "El nivel debe ser MODERADO o CONGESTIONADO.";

    private final TrafficMapDAO trafficDao;
    private final RoadMapDAO mapDao;
    private final Clock clock;

    public TrafficMapService(TrafficMapDAO trafficDao, RoadMapDAO mapDao, Clock clock) {
        this.trafficDao = Objects.requireNonNull(trafficDao, "trafficDao");
        this.mapDao = Objects.requireNonNull(mapDao, "mapDao");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The congested segments now, the most congested first.
     *
     * @param only null for MODERADO and CONGESTIONADO; one of them to see only that level
     */
    public List<TrafficSegmentView> current(TrafficLevel only) {
        if (only == TrafficLevel.FLUID) {
            throw new ValidationException(ONLY_CONGESTION);
        }
        List<TrafficSegmentView> segments = trafficDao.congestedSince(LocalDateTime.now(clock).minus(WINDOW));
        return only == null ? segments : segments.stream().filter(segment -> segment.level() == only).toList();
    }

    /**
     * How a segment forced for the demo looks on the map (docs/api.md 10.3), before the
     * simulation saves it in its next cycle.
     *
     * @throws NotFoundException if the segment does not exist
     */
    public TrafficSegmentView forced(long segmentId, TrafficLevel level, TrafficSource source) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        SegmentView segment = mapDao.segmentViews(List.of(segmentId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Tramo", segmentId));
        return new TrafficSegmentView(segmentId, segment.streetName(), level, TrafficRecorder.congestionOf(level),
                LocalDateTime.now(clock), source, segment.from(), segment.to());
    }
}
