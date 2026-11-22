
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.StationRequest;
import co.edu.unicesar.rutavida.dto.VehicleRequest;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.FleetService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Units and stations for the view (CU-04). Seeing them needs UNIDADES / CONSULTAR; changing
 * them, UNIDADES / GESTIONAR (stations included: they are part of the same permission). It
 * delegates to FleetService and turns every error into a message in Spanish with its kind.
 */
public class FleetController {

    private static final Logger LOG = Logger.getLogger(FleetController.class.getName());

    static final String MODULE = "UNIDADES";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final FleetService fleetService;
    private final AuthService authService;
    private final SessionContext session;

    public FleetController(FleetService fleetService, AuthService authService,
                           SessionContext session) {
        this.fleetService = Objects.requireNonNull(fleetService, "fleetService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Every unit, in any status. */
    public OperationResult<List<EmergencyVehicle>> listAll() {
        return run(VIEW, user -> fleetService.listAll());
    }

    /** CU-04: registers a unit (DISPONIBLE). */
    public OperationResult<EmergencyVehicle> registerVehicle(VehicleRequest request) {
        return run(MANAGE, user -> fleetService.registerVehicle(request, user));
    }

    /** CU-04: edits the type, station, code and plate of a unit. */
    public OperationResult<EmergencyVehicle> updateVehicle(long vehicleId, VehicleRequest request) {
        return run(MANAGE, user -> fleetService.updateVehicle(vehicleId, request, user));
    }

    /** CU-04: DISPONIBLE, MANTENIMIENTO or FUERA_SERVICIO by hand. */
    public OperationResult<EmergencyVehicle> changeVehicleStatus(
            long vehicleId, VehicleOperationalStatus status) {
        return run(MANAGE, user -> fleetService.changeVehicleStatus(vehicleId, status, user));
    }

    /** Every station, active or not. */
    public OperationResult<List<EmergencyStation>> listStations() {
        return run(VIEW, user -> fleetService.listStations());
    }

    /** CU-04: registers a station. */
    public OperationResult<EmergencyStation> registerStation(StationRequest request) {
        return run(MANAGE, user -> fleetService.registerStation(request, user));
    }

    /** CU-04: edits the data of a station. */
    public OperationResult<EmergencyStation> updateStation(long stationId, StationRequest request) {
        return run(MANAGE, user -> fleetService.updateStation(stationId, request, user));
    }

    /** CU-04: activates or deactivates a station. */
    public OperationResult<EmergencyStation> changeStationStatus(
            long stationId, RecordStatus status) {
        return run(MANAGE, user -> fleetService.changeStationStatus(stationId, status, user));
    }

    private <T> OperationResult<T> run(String action, Function<UserSession, T> work) {
        if (!session.isActive()) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }

        try {
            UserSession user = session.current();
            authService.requirePermission(user, MODULE, action);
            return OperationResult.ok(work.apply(user));

        } catch (RuntimeException e) {
            OperationResult<T> failure = OperationResult.failure(e);

            if (failure.error() == ErrorKind.UNEXPECTED
                    || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Fleet operation failed", e);
            }
            return failure;
        }
    }
}
