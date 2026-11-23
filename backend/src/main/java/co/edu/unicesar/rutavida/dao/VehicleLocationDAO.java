package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.VehicleLocation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Position history of the units (table UBICACION_VEHICULO). The last known position
 * also lives in VEHICULO_EMERGENCIA (EmergencyVehicleDAO.updateLocation); this table
 * keeps the whole track.
 */
public interface VehicleLocationDAO {

    /**
     * Saves many new positions (id 0) with few round trips to Oracle. The generated ids
     * are not assigned to the objects. Call it inside a transaction so that a failed
     * batch saves nothing.
     */
    void insertBatch(List<VehicleLocation> locations);

    /** Positions of one unit between {@code from} and {@code to} (both included), oldest first. */
    List<VehicleLocation> findByVehicle(long vehicleId, LocalDateTime from, LocalDateTime to);

    /**
     * Most recent report of every unit. Fakes written before real GPS can keep the default;
     * the map then preserves its historical SIMULACION label.
     */
    default Map<Long, VehicleLocation> findLatestByVehicle() {
        return Map.of();
    }
}
