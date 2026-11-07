package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dao.EmergencyStationDAO;
import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.HospitalDAO;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.service.CatalogService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A CatalogService over fixed in-memory lists (EXAMPLE data, no Oracle). When
 * {@link #oracleDown} is true every query fails like Oracle stopped.
 */
public final class CatalogTestSupport {

    public static final GeoPoint ROSARIO = new GeoPoint(10.469330, -73.254520);

    public static final List<EmergencyType> EMERGENCY_TYPES = List.of(
            new EmergencyType(1, "EMERGENCIA_MEDICA", 9, "Urgencia medica sin accidente de transito"),
            new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Accidente de transito con posibles lesionados"));
    public static final List<VehicleType> VEHICLE_TYPES = List.of(
            new VehicleType(1, "AMBULANCIA", 8, "Unidad de atencion medica y traslado"),
            new VehicleType(2, "BOMBERO", 8, "Vehiculo del cuerpo de bomberos"));
    public static final List<EmergencyStation> STATIONS = List.of(
            new EmergencyStation(2, "EJEMPLO - Base de ambulancias", "EJEMPLO: base ficticia", null, null,
                    ROSARIO, RecordStatus.ACTIVE));
    public static final List<EmergencyVehicle> UNITS = List.of(
            new EmergencyVehicle(1, 1, 2, "EJEMPLO-AMB-01", null, VehicleOperationalStatus.AVAILABLE,
                    ROSARIO, LocalDateTime.of(2026, 11, 7, 8, 0)),
            new EmergencyVehicle(3, 1, 2, "EJEMPLO-AMB-03", null, VehicleOperationalStatus.MAINTENANCE,
                    null, null));
    public static final Hospital ACTIVE_HOSPITAL = new Hospital(1, "HOSPITAL ROSARIO PUMAREJO DE LOPEZ",
            HospitalLevel.LEVEL_II, "Calle 16 No. 17-141", null, ROSARIO, null, RecordStatus.ACTIVE);
    public static final Hospital INACTIVE_HOSPITAL = new Hospital(9, "PRUEBA HOSPITAL INACTIVO",
            HospitalLevel.LEVEL_I, "Calle de prueba", null, ROSARIO, null, RecordStatus.INACTIVE);

    public final AtomicBoolean oracleDown = new AtomicBoolean(false);

    public CatalogService service() {
        return new CatalogService(emergencyTypes(), vehicleTypes(), stations(), units(), hospitals());
    }

    private void failIfDown() {
        if (oracleDown.get()) {
            throw new DataAccessException("ORA-12541: TNS:no listener", null);
        }
    }

    private EmergencyTypeDAO emergencyTypes() {
        return new EmergencyTypeDAO() {
            @Override
            public Optional<EmergencyType> findById(long id) {
                failIfDown();
                return EMERGENCY_TYPES.stream().filter(type -> type.getId() == id).findFirst();
            }

            @Override
            public List<EmergencyType> findAll() {
                failIfDown();
                return EMERGENCY_TYPES;
            }
        };
    }

    private VehicleTypeDAO vehicleTypes() {
        return () -> {
            failIfDown();
            return VEHICLE_TYPES;
        };
    }

    private EmergencyStationDAO stations() {
        return new EmergencyStationDAO() {
            @Override
            public Optional<EmergencyStation> findById(long id) {
                return STATIONS.stream().filter(station -> station.getId() == id).findFirst();
            }

            @Override
            public List<EmergencyStation> findAll() {
                failIfDown();
                return STATIONS;
            }

            @Override
            public long insert(EmergencyStation station) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void update(EmergencyStation station) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private EmergencyVehicleDAO units() {
        return new EmergencyVehicleDAO() {
            @Override
            public Optional<EmergencyVehicle> findById(long id) {
                return UNITS.stream().filter(unit -> unit.getId() == id).findFirst();
            }

            @Override
            public List<EmergencyVehicle> findAll() {
                failIfDown();
                return UNITS;
            }

            @Override
            public List<EmergencyVehicle> findAvailable() {
                return UNITS.stream()
                        .filter(unit -> unit.getOperationalStatus() == VehicleOperationalStatus.AVAILABLE)
                        .toList();
            }

            @Override
            public long insert(EmergencyVehicle vehicle) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void update(EmergencyVehicle vehicle) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updateStatus(long id, VehicleOperationalStatus status) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updateLocation(long id, GeoPoint location, LocalDateTime at) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private HospitalDAO hospitals() {
        return new HospitalDAO() {
            @Override
            public Optional<Hospital> findById(long id) {
                return List.of(ACTIVE_HOSPITAL, INACTIVE_HOSPITAL).stream()
                        .filter(hospital -> hospital.getId() == id).findFirst();
            }

            @Override
            public List<Hospital> findAll() {
                failIfDown();
                return List.of(ACTIVE_HOSPITAL, INACTIVE_HOSPITAL);
            }

            @Override
            public List<Hospital> findOperational() {
                failIfDown();
                return List.of(ACTIVE_HOSPITAL);
            }

            @Override
            public long insert(Hospital hospital) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void update(Hospital hospital) {
                throw new UnsupportedOperationException();
            }
        };
    }
}