package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyStationDAO;
import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.HospitalDAO;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.VehicleType;

import java.util.List;
import java.util.Objects;

/**
 * Read-only catalogs that the web needs to fill its forms and its map: emergency and
 * vehicle types (initial data), stations and hospitals (03_datos_valledupar.sql) and the
 * units (06_unidades_ejemplo.sql, EXAMPLE data).
 */
public class CatalogService {

    private final EmergencyTypeDAO emergencyTypeDao;
    private final VehicleTypeDAO vehicleTypeDao;
    private final EmergencyStationDAO stationDao;
    private final EmergencyVehicleDAO vehicleDao;
    private final HospitalDAO hospitalDao;

    public CatalogService(EmergencyTypeDAO emergencyTypeDao, VehicleTypeDAO vehicleTypeDao,
                          EmergencyStationDAO stationDao, EmergencyVehicleDAO vehicleDao,
                          HospitalDAO hospitalDao) {
        this.emergencyTypeDao = Objects.requireNonNull(emergencyTypeDao, "emergencyTypeDao");
        this.vehicleTypeDao = Objects.requireNonNull(vehicleTypeDao, "vehicleTypeDao");
        this.stationDao = Objects.requireNonNull(stationDao, "stationDao");
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.hospitalDao = Objects.requireNonNull(hospitalDao, "hospitalDao");
    }

    /** Emergency types, highest priority first (GET /api/emergency-types). */
    public List<EmergencyType> emergencyTypes() {
        return emergencyTypeDao.findAll();
    }

    /** Vehicle types, by name (GET /api/vehicle-types). */
    public List<VehicleType> vehicleTypes() {
        return vehicleTypeDao.findAll();
    }

    /** Every unit with its status and last position, by unit code (GET /api/units). */
    public List<EmergencyVehicle> units() {
        return vehicleDao.findAll();
    }

    /** Fire stations and ambulance bases, by name (GET /api/stations). */
    public List<EmergencyStation> stations() {
        return stationDao.findAll();
    }

    /** Active hospitals, or all of them when {@code includeInactive} (GET /api/hospitals). */
    public List<Hospital> hospitals(boolean includeInactive) {
        return includeInactive ? hospitalDao.findAll() : hospitalDao.findOperational();
    }
}