package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.IncidentView;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.service.RoadMapService;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns the results of IncidentController into what the map needs (docs/api.md 9.1). It
 * receives the result of IncidentController, which already checked the session and the
 * permission: an error passes through unchanged, with the same kind and message.
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

    private static <T> OperationResult<T> run(Supplier<T> work) {
        try {
            return OperationResult.ok(work.get());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Map data failed", e);
            return OperationResult.failure(e);
        }
    }
}