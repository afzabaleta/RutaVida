package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.VehicleLocation;

import java.time.LocalDateTime;
import java.util.List;

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
}