package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadMapDAO;
import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.DispatchedMission;
import co.edu.unicesar.rutavida.dto.IncidentView;
import co.edu.unicesar.rutavida.dto.MissionRoute;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prepares road data for the map of the web, with one query for all the segments it needs:
 * the street and the coordinates of each incident (docs/api.md 9.1) and the line of each
 * saved route of a mission (docs/api.md 6.3 and 6.4). It does not change anything:
 * reporting incidents is IncidentService (S7) and saving routes is DispatchService (K7).
 */
public class RoadMapService {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final double SAME_MINUTES = 0.05;

    /** The recommended route first, then the fastest; the id breaks ties. */
    private static final Comparator<Route> RECOMMENDED_FIRST = Comparator
            .comparing((Route route) -> !route.isRecommended())
            .thenComparingDouble(Route::getEstimatedMinutes)
            .thenComparingLong(Route::getId);

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
        Map<Long, SegmentView> segments = segmentsById(
                incidents.stream().map(RoadIncident::getSegmentId).collect(Collectors.toSet()));
        return incidents.stream()
                .map(incident -> IncidentView.of(incident, segments.get(incident.getSegmentId())))
                .toList();
    }

    /** One incident with its segment. */
    public IncidentView describe(RoadIncident incident) {
        ValidationUtil.requireNonNull(incident, "incidente");
        return describe(List.of(incident)).get(0);
    }

    /**
     * The saved routes of a mission with their lines: the recommended one first (rank 1),
     * then the alternatives from the fastest. A segment that is no longer in TRAMO_VIAL is
     * left out of the line, but its id stays in segmentIds.
     */
    public List<MissionRoute> describeRoutes(List<Route> routes) {
        ValidationUtil.requireNonNull(routes, "rutas");
        if (routes.isEmpty()) {
            return List.of();
        }
        Map<Long, SegmentView> segments = segmentsById(routes.stream()
                .flatMap(route -> stepsOf(route).stream())
                .map(RouteSegment::segmentId)
                .collect(Collectors.toSet()));
        List<Route> ordered = routes.stream().sorted(RECOMMENDED_FIRST).toList();
        Route best = ordered.get(0).isRecommended() ? ordered.get(0) : null;
        List<MissionRoute> views = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            views.add(toMissionRoute(ordered.get(i), i + 1, best, segments));
        }
        return views;
    }

    /** The answer of a dispatch: the assignment and its routes ready for the map. */
    public DispatchedMission describeDispatch(DispatchResult result) {
        ValidationUtil.requireNonNull(result, "despacho");
        return new DispatchedMission(result.assignment(), describeRoutes(result.routes()));
    }

    private Map<Long, SegmentView> segmentsById(Set<Long> segmentIds) {
        if (segmentIds.isEmpty()) {
            return Map.of();
        }
        return mapDao.segmentViews(segmentIds).stream()
                .collect(Collectors.toMap(SegmentView::segmentId, Function.identity(), (a, b) -> a));
    }

    private static MissionRoute toMissionRoute(Route route, int rank, Route best, Map<Long, SegmentView> segments) {
        List<GeoPoint> path = new ArrayList<>();
        List<Long> segmentIds = new ArrayList<>();
        int lights = 0;
        int congested = 0;
        for (RouteSegment step : stepsOf(route)) {
            segmentIds.add(step.segmentId());
            SegmentView segment = segments.get(step.segmentId());
            if (segment != null) {
                if (path.isEmpty()) {
                    path.add(segment.from());
                }
                path.add(segment.to());
            }
            if (step.trafficLightCount() != null && step.trafficLightCount() > 0) {
                lights++;
            }
            if (step.congestionFactor() != null
                    && step.congestionFactor() >= RouteExplanationBuilder.CONGESTED_FROM) {
                congested++;
            }
        }
        return new MissionRoute(route.getId(), route.getStatus(), route.isRecommended(),
                route.getDestinationHospitalId(), rank, route.getType(), path, segmentIds,
                route.getDistanceKm(), route.getEstimatedMinutes(), lights, congested,
                explain(route, best, lights, congested));
    }

    /** The segments in travel order (RUTA_TRAMO.orden). */
    private static List<RouteSegment> stepsOf(Route route) {
        Collection<RouteSegment> steps = route.getSegments() == null ? List.of() : route.getSegments();
        return steps.stream().sorted(Comparator.comparingInt(RouteSegment::order)).toList();
    }

    /** A short summary with the numbers saved when the route was calculated. */
    private static String explain(Route route, Route best, int lights, int congested) {
        String numbers = minutes(route.getEstimatedMinutes()) + " y " + km(route.getDistanceKm());
        if (route == best) {
            return "Ruta recomendada: " + numbers + " con el tráfico de cuando se calculó. Tiene "
                    + count(lights, "semáforo", "semáforos") + " y "
                    + count(congested, "tramo congestionado", "tramos congestionados") + ".";
        }
        if (best == null) {
            return "Alternativa: " + numbers + " con el tráfico de cuando se calculó.";
        }
        double diff = route.getEstimatedMinutes() - best.getEstimatedMinutes();
        String summary = " (" + minutes(route.getEstimatedMinutes()) + ", " + km(route.getDistanceKm()) + ").";
        if (diff >= SAME_MINUTES) {
            return "Alternativa: " + minutes(diff) + " más lenta que la recomendada" + summary;
        }
        if (diff <= -SAME_MINUTES) {
            return "Alternativa: " + minutes(-diff) + " más rápida que la recomendada" + summary;
        }
        return "Alternativa: tarda lo mismo que la recomendada" + summary;
    }

    private static String minutes(double value) {
        return String.format(SPANISH, "%.1f min", value);
    }

    private static String km(double value) {
        return String.format(SPANISH, "%.1f km", value);
    }

    private static String count(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}