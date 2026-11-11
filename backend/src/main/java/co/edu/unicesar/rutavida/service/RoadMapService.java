package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadMapDAO;
import co.edu.unicesar.rutavida.dto.IncidentView;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prepares road data for the map of the web: it adds to each incident the street and the
 * coordinates of its segment, with one query for all of them (docs/api.md 9.1). It does not
 * change anything: reporting and validating incidents is IncidentService (S7).
 */
public class RoadMapService {

    private final RoadMapDAO mapDao;

    public RoadMapService(RoadMapDAO mapDao) {
        this.mapDao = Objects.requireNonNull(mapDao, "mapDao");
    }

    /** The incidents with their segments, in the same order. */
    public List<IncidentView> describe(List<RoadIncident> incidents) {
        ValidationUtil.requireNonNull(incidents, "incidentes");
        if (incidents.isEmpty()) {
            return List.of();
        }
        Map<Long, SegmentView> segments = mapDao.segmentViews(
                        incidents.stream().map(RoadIncident::getSegmentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SegmentView::segmentId, Function.identity()));
        return incidents.stream()
                .map(incident -> IncidentView.of(incident, segments.get(incident.getSegmentId())))
                .toList();
    }

    /** One incident with its segment. */
    public IncidentView describe(RoadIncident incident) {
        ValidationUtil.requireNonNull(incident, "incidente");
        return describe(List.of(incident)).get(0);
    }
}