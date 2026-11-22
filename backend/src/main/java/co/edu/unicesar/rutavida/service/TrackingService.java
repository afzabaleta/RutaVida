package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.VehicleLocationDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Tracking of the units (module G): reads the new positions from a VehiclePositionSource
 * and saves them. The last position goes to VEHICULO_EMERGENCIA (what the map shows now)
 * and every position to UBICACION_VEHICULO (the history).
 *
 * <p>To avoid unnecessary writes: one transaction per call, the history in one batch, and a
 * unit that did not move is not written again.
 */
public class TrackingService {

    private static final Logger LOG = Logger.getLogger(TrackingService.class.getName());

    private final VehiclePositionSource positions;
    private final EmergencyVehicleDAO vehicleDao;
    private final VehicleLocationDAO locationDao;
    private final DispatchService dispatchRules;
    private final TransactionManager transactions;

    public TrackingService(VehiclePositionSource positions, EmergencyVehicleDAO vehicleDao,
                           VehicleLocationDAO locationDao, DispatchService dispatchRules,
                           TransactionManager transactions) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.locationDao = Objects.requireNonNull(locationDao, "locationDao");
        this.dispatchRules = Objects.requireNonNull(dispatchRules, "dispatchRules");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /**
     * Asks the source for the new positions and saves them. Returns how many were saved.
     * A position of a unit that does not exist, or older than the one saved, is skipped
     * so that it does not stop the others.
     */
    public int refreshPositions() {
        List<VehicleLocation> polled = positions.poll();
        if (polled.isEmpty()) {
            return 0;
        }

        return transactions.callInTransaction(() -> {
            List<VehicleLocation> saved = new ArrayList<>();

            for (VehicleLocation position : polled) {
                Optional<EmergencyVehicle> found = vehicleDao.findById(position.getVehicleId());
                if (found.isEmpty()) {
                    LOG.warning("Position of a unit that does not exist: " + position.getVehicleId());
                    continue;
                }

                EmergencyVehicle vehicle = found.get();
                if (position.getLocation().equals(vehicle.getLastLocation())) {
                    continue;   // It did not move: nothing to write.
                }

                try {
                    dispatchRules.moveVehicle(vehicle, position.getLocation(), position.getRecordedAt());
                } catch (ValidationException e) {
                    LOG.warning("Position skipped for unit " + vehicle.getId() + ": " + e.getMessage());
                    continue;
                }

                vehicleDao.updateLocation(vehicle.getId(), vehicle.getLastLocation(), vehicle.getLastLocationAt());
                saved.add(position);
            }

            if (!saved.isEmpty()) {
                locationDao.insertBatch(saved);
            }
            return saved.size();
        });
    }

    /** Last known position of a unit; empty if it has never reported one. */
    public Optional<GeoPoint> currentPosition(long vehicleId) {
        EmergencyVehicle vehicle = vehicleDao.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Unidad", vehicleId));
        return Optional.ofNullable(vehicle.getLastLocation());
    }

    /** Positions of one unit between two moments, oldest first (to replay a mission). */
    public List<VehicleLocation> history(long vehicleId, LocalDateTime from, LocalDateTime to) {
        ValidationUtil.requireNonNull(from, "fecha inicial");
        ValidationUtil.requireNonNull(to, "fecha final");
        if (from.isAfter(to)) {
            throw new ValidationException("La fecha inicial no puede ser posterior a la final.");
        }
        return locationDao.findByVehicle(vehicleId, from, to);
    }
}