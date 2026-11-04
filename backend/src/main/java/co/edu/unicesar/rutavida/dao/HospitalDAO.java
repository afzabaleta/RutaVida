package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.Hospital;

import java.util.List;
import java.util.Optional;

/**
 * Persistence of hospitals (table HOSPITAL). The services of each hospital (SERVICIO_HOSPITAL)
 * are added when the entity HospitalServiceOffering exists.
 */
public interface HospitalDAO {

    Optional<Hospital> findById(long id);

    /** Every hospital, active or not, by name. */
    List<Hospital> findAll();

    /** Active hospitals (estado 'A'), by name: the possible destinations of a transfer. */
    List<Hospital> findOperational();

    /** Saves a new hospital (id 0), assigns the generated id with {@code setId} and returns it. */
    long insert(Hospital hospital);

    /** Rewrites every column of a saved hospital. Throws NotFoundException if it does not exist. */
    void update(Hospital hospital);
}