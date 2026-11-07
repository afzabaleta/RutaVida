package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence of ambulances and fire trucks (table VEHICULO_EMERGENCIA). Each kind of change
 * has its own method so that they do not overwrite each other: {@link #update} the registration
 * data, {@link #updateStatus} the availability and {@link #updateLocation} the last position.
 */
public interface EmergencyVehicleDAO {

    Optional<EmergencyVehicle> findById(long id);

    /** Every unit, by unit code. */
    List<EmergencyVehicle> findAll();

    /** Units in DISPONIBLE status, by unit code. */
    List<EmergencyVehicle> findAvailable();

    /** Saves a new unit (id 0), assigns the generated id with {@code setId} and returns it. */
    long insert(EmergencyVehicle vehicle);

    /**
     * Rewrites the registration data (type, station, unit code and plate). It does not touch
     * the status or the position. Throws NotFoundException if the unit does not exist.
     */
    void update(EmergencyVehicle vehicle);

    /** Changes the availability. Throws NotFoundException if the unit does not exist. */
    void updateStatus(long id, VehicleOperationalStatus status);

    /**
     * Saves the last known position (the history goes to VehicleLocationDAO).
     * Throws NotFoundException if the unit does not exist.
     */
    void updateLocation(long id, GeoPoint location, LocalDateTime at);
}