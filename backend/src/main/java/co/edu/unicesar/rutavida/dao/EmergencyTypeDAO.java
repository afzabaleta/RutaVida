package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.EmergencyType;
import java.util.List;
import java.util.Optional;

/** Catalog of emergency types (table TIPO_EMERGENCIA). The application only reads it. */
public interface EmergencyTypeDAO {

    Optional<EmergencyType> findById(long id);

    /** All types, highest priority first. */
    List<EmergencyType> findAll();

}
