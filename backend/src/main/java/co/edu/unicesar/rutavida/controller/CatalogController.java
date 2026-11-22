package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.service.CatalogService;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Catalogs for the web: emergency and vehicle types, units, stations and hospitals.
 * The permission of each route is checked by the web server before calling here.
 */
public class CatalogController {

    private static final Logger LOG = Logger.getLogger(CatalogController.class.getName());

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
    }

    public OperationResult<List<EmergencyType>> emergencyTypes() {
        return run(catalogService::emergencyTypes);
    }

    public OperationResult<List<VehicleType>> vehicleTypes() {
        return run(catalogService::vehicleTypes);
    }

    public OperationResult<List<EmergencyVehicle>> units() {
        return run(catalogService::units);
    }

    public OperationResult<List<EmergencyStation>> stations() {
        return run(catalogService::stations);
    }

    public OperationResult<List<Hospital>> hospitals(boolean includeInactive) {
        return run(() -> catalogService.hospitals(includeInactive));
    }

    private static <T> OperationResult<T> run(Supplier<T> query) {
        try {
            return OperationResult.ok(query.get());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Catalog query failed", e);
            return OperationResult.failure(e);
        }
    }
}