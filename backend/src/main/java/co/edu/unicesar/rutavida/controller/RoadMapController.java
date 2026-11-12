package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.DispatchedMission;
import co.edu.unicesar.rutavida.dto.IncidentView;
import co.edu.unicesar.rutavida.dto.MissionRoute;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.service.RoadMapService;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns the results of IncidentController (docs/api.md 9.1) and DispatchController (6.3 and
 * 6.4) into what the map needs. It receives their result, which already checked the session
 * and the permission: an error passes through unchanged, with the same kind and message.
 */
public class RoadMapController {

    private static final Logger LOG = Logger.getLogger(RoadMapController.class.getName());

    private final RoadMapService mapService;

    public RoadMapController(RoadMapService mapService) {
        this.mapService = Objects.requireNonNull(mapService, "mapService");
    }

    /** A list of incidents with their segments. */
    public OperationResult<List<IncidentView>> incidentsOnMap(OperationResult<List<RoadIncident>> incidents) {
        Objects.requireNonNull(incidents, "incidents");
        if (!incidents.success()) {
            return OperationResult.error(incidents.error(), incidents.message());
        }
        return run(() -> mapService.describe(incidents.value()));
    }

    /** One incident with its segment (after reporting it or changing its status). */
    public OperationResult<IncidentView> incidentOnMap(OperationResult<RoadIncident> incident) {
        Objects.requireNonNull(incident, "incident");
        if (!incident.success()) {
            return OperationResult.error(incident.error(), incident.message());
        }
        return run(() -> mapService.describe(incident.value()));
    }

    /** The routes of a mission with their lines (GET /api/dispatches/{id}/routes). */
    public OperationResult<List<MissionRoute>> routesOnMap(OperationResult<List<Route>> routes) {
        Objects.requireNonNull(routes, "routes");
        if (!routes.success()) {
            return OperationResult.error(routes.error(), routes.message());
        }
        return run(() -> mapService.describeRoutes(routes.value()));
    }

    /** A new dispatch with the lines of its routes (POST /api/dispatches). */
    public OperationResult<DispatchedMission> dispatchOnMap(OperationResult<DispatchResult> dispatch) {
        Objects.requireNonNull(dispatch, "dispatch");
        if (!dispatch.success()) {
            return OperationResult.error(dispatch.error(), dispatch.message());
        }
        return run(() -> mapService.describeDispatch(dispatch.value()));
    }

    private static <T> OperationResult<T> run(Supplier<T> work) {
        try {
            return OperationResult.ok(work.get());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Map data failed", e);
            return OperationResult.failure(e);
        }
    }
}