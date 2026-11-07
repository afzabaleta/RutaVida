package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.model.EmergencyType;

import java.util.List;
import java.util.Objects;

/**
 * Read-only catalogs that the web needs to fill its forms and its map. They come from
 * the initial data scripts. The fleet and hospital catalogs are added here in A12b.
 */
public class CatalogService {

    private final EmergencyTypeDAO emergencyTypeDao;

    public CatalogService(EmergencyTypeDAO emergencyTypeDao) {
        this.emergencyTypeDao = Objects.requireNonNull(emergencyTypeDao, "emergencyTypeDao");
    }

    /** Emergency types, highest priority first (GET /api/emergency-types). */
    public List<EmergencyType> emergencyTypes() {
        return emergencyTypeDao.findAll();
    }
}