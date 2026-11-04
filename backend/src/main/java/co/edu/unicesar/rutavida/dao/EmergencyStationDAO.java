package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.EmergencyStation;

import java.util.List;
import java.util.Optional;

/** Persistence of fire stations and ambulance bases (table ESTACION_EMERGENCIA). */
public interface EmergencyStationDAO {

    Optional<EmergencyStation> findById(long id);

    /** Every station, active or not, by name. */
    List<EmergencyStation> findAll();

    /** Saves a new station (id 0), assigns the generated id with {@code setId} and returns it. */
    long insert(EmergencyStation station);

    /** Rewrites every column of a saved station. Throws NotFoundException if it does not exist. */
    void update(EmergencyStation station);
}