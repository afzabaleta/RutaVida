package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four fleet and hospital DAOs against the local Oracle. The catalogs come from
 * 02_datos_iniciales.sql and 03_datos_valledupar.sql; the rows created here (PRUEBA A7C…)
 * live in transactions that are rolled back, so Oracle is left unchanged. Skipped without Oracle.
 */
class JdbcFleetAndHospitalDAOIntegrationTest {

    /** Coordinates with 6 decimals, like the NUMBER(9,6) columns. */
    private static final GeoPoint PLAZA = new GeoPoint(10.476921, -73.243645);
    private static final GeoPoint POPA = new GeoPoint(10.467172, -73.265099);
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 4, 9, 30, 15);

    private static JdbcTransactionManager jdbc;
    private static JdbcVehicleTypeDAO typeDao;
    private static JdbcEmergencyStationDAO stationDao;
    private static JdbcHospitalDAO hospitalDao;
    private static JdbcEmergencyVehicleDAO vehicleDao;
    private static long ambulanceType;
    private static long fireTruckType;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        typeDao = new JdbcVehicleTypeDAO(jdbc);
        stationDao = new JdbcEmergencyStationDAO(jdbc);
        hospitalDao = new JdbcHospitalDAO(jdbc);
        vehicleDao = new JdbcEmergencyVehicleDAO(jdbc);
        ambulanceType = number(jdbc, "SELECT id_tipo_vehiculo FROM tipo_vehiculo WHERE nombre = 'AMBULANCIA'");
        fireTruckType = number(jdbc, "SELECT id_tipo_vehiculo FROM tipo_vehiculo WHERE nombre = 'BOMBERO'");
    }

    private static EmergencyStation newStation(String name) {
        return new EmergencyStation(0, name, "Calle de prueba", null, null, PLAZA, RecordStatus.ACTIVE);
    }

    private static EmergencyVehicle newVehicle(long stationId, String code, String plate) {
        return new EmergencyVehicle(0, ambulanceType, stationId, code, plate,
                VehicleOperationalStatus.AVAILABLE, null, null);
    }

    @Test
    void theCatalogsAndTheValleduparDataAreRead() {
        Map<String, VehicleType> types = typeDao.findAll().stream()
                .collect(Collectors.toMap(VehicleType::getName, type -> type));
        assertEquals(ambulanceType, types.get("AMBULANCIA").getId());
        assertEquals(fireTruckType, types.get("BOMBERO").getId());

        List<String> stations = stationDao.findAll().stream().map(EmergencyStation::getName).toList();
        assertTrue(stations.contains("Estacion de Bomberos"), "03_datos_valledupar.sql");

        Hospital rosario = hospitalDao.findAll().stream()
                .filter(h -> h.getName().equals("HOSPITAL ROSARIO PUMAREJO DE LOPEZ"))
                .findFirst().orElseThrow();
        assertEquals(HospitalLevel.LEVEL_II, rosario.getLevel());
        assertEquals(new GeoPoint(10.469330, -73.254520), rosario.getLocation());
        assertNull(rosario.getEmergencyCapacity(), "capacidad_urgencias is NULL in the script");
        assertEquals(RecordStatus.ACTIVE, rosario.getStatus());
        assertEquals(rosario.getName(), hospitalDao.findById(rosario.getId()).orElseThrow().getName());

        assertTrue(stationDao.findById(-1).isEmpty());
        assertTrue(hospitalDao.findById(-1).isEmpty());
        assertTrue(vehicleDao.findById(-1).isEmpty());
    }

    @Test
    void aStationIsInsertedAndUpdated() {
        inRolledBackTransaction(jdbc, () -> {
            EmergencyStation station = newStation("PRUEBA A7C Estación");
            long id = stationDao.insert(station);

            assertEquals(id, station.getId(), "insert assigns the id to the object");
            EmergencyStation read = stationDao.findById(id).orElseThrow();
            assertEquals("PRUEBA A7C Estación", read.getName());
            assertEquals(PLAZA, read.getLocation());
            assertNull(read.getNeighborhood());

            stationDao.update(new EmergencyStation(id, "PRUEBA A7C Estación", "Otra calle", "Centro",
                    "6055550000", POPA, RecordStatus.INACTIVE));
            EmergencyStation changed = stationDao.findById(id).orElseThrow();
            assertEquals("Otra calle", changed.getAddress());
            assertEquals("Centro", changed.getNeighborhood());
            assertEquals(POPA, changed.getLocation());
            assertEquals(RecordStatus.INACTIVE, changed.getStatus());
        });
    }

    @Test
    void anInactiveHospitalIsNotOperational() {
        inRolledBackTransaction(jdbc, () -> {
            Hospital hospital = new Hospital(0, "PRUEBA A7C HOSPITAL", HospitalLevel.LEVEL_III,
                    "Calle de prueba", null, POPA, 12, RecordStatus.ACTIVE);
            long id = hospitalDao.insert(hospital);

            Hospital read = hospitalDao.findById(id).orElseThrow();
            assertEquals(HospitalLevel.LEVEL_III, read.getLevel());
            assertEquals(Integer.valueOf(12), read.getEmergencyCapacity());
            assertTrue(hospitalDao.findOperational().stream().anyMatch(h -> h.getId() == id));

            hospitalDao.update(new Hospital(id, "PRUEBA A7C HOSPITAL", HospitalLevel.LEVEL_III,
                    "Calle de prueba", null, POPA, null, RecordStatus.INACTIVE));
            assertNull(hospitalDao.findById(id).orElseThrow().getEmergencyCapacity());
            assertFalse(hospitalDao.findOperational().stream().anyMatch(h -> h.getId() == id));
            assertTrue(hospitalDao.findAll().stream().anyMatch(h -> h.getId() == id), "still listed in findAll");
            assertTrue(hospitalDao.findOperational().stream().allMatch(h -> h.getStatus() == RecordStatus.ACTIVE));
        });
    }

    @Test
    void aVehicleChangesRegistrationStatusAndPositionSeparately() {
        inRolledBackTransaction(jdbc, () -> {
            long station = stationDao.insert(newStation("PRUEBA A7C Base"));
            EmergencyVehicle vehicle = newVehicle(station, "PRUEBA-A7C-01", null);
            long id = vehicleDao.insert(vehicle);

            EmergencyVehicle read = vehicleDao.findById(id).orElseThrow();
            assertEquals(id, vehicle.getId());
            assertEquals(ambulanceType, read.getVehicleTypeId());
            assertEquals(station, read.getStationId());
            assertNull(read.getLastLocation(), "no position yet");
            assertNull(read.getLastLocationAt());
            assertTrue(vehicleDao.findAvailable().stream().anyMatch(v -> v.getId() == id));

            vehicleDao.updateLocation(id, POPA, AT);
            vehicleDao.updateStatus(id, VehicleOperationalStatus.MAINTENANCE);
            vehicleDao.update(new EmergencyVehicle(id, fireTruckType, station, "PRUEBA-A7C-02", "PRB7C1",
                    VehicleOperationalStatus.AVAILABLE, null, null));

            EmergencyVehicle changed = vehicleDao.findById(id).orElseThrow();
            assertEquals(fireTruckType, changed.getVehicleTypeId());
            assertEquals("PRUEBA-A7C-02", changed.getUnitCode());
            assertEquals("PRB7C1", changed.getPlate());
            assertEquals(VehicleOperationalStatus.MAINTENANCE, changed.getOperationalStatus(),
                    "update does not touch the status");
            assertEquals(POPA, changed.getLastLocation(), "update does not touch the position");
            assertEquals(AT, changed.getLastLocationAt());
            assertFalse(vehicleDao.findAvailable().stream().anyMatch(v -> v.getId() == id));
            assertTrue(vehicleDao.findAll().stream().anyMatch(v -> v.getId() == id));
        });
    }

    @Test
    void wrongCallsAreRejected() {
        EmergencyStation savedStation = newStation("PRUEBA");
        savedStation.setId(5);
        assertThrows(IllegalArgumentException.class, () -> stationDao.insert(savedStation));
        assertThrows(IllegalArgumentException.class, () -> stationDao.update(newStation("PRUEBA")));
        assertThrows(NotFoundException.class, () -> stationDao.update(
                new EmergencyStation(999_999_999L, "PRUEBA", "Calle", null, null, PLAZA, RecordStatus.ACTIVE)));

        Hospital missing = new Hospital(999_999_999L, "PRUEBA", HospitalLevel.LEVEL_I, "Calle", null, POPA,
                null, RecordStatus.ACTIVE);
        assertThrows(NotFoundException.class, () -> hospitalDao.update(missing));
        assertThrows(IllegalArgumentException.class, () -> hospitalDao.insert(missing));

        EmergencyVehicle savedVehicle = newVehicle(1, "PRUEBA", null);
        savedVehicle.setId(5);
        assertThrows(IllegalArgumentException.class, () -> vehicleDao.insert(savedVehicle));
        assertThrows(IllegalArgumentException.class, () -> vehicleDao.update(newVehicle(1, "PRUEBA", null)));
        assertThrows(NotFoundException.class,
                () -> vehicleDao.updateStatus(-1, VehicleOperationalStatus.AVAILABLE));
        assertThrows(NotFoundException.class, () -> vehicleDao.updateLocation(-1, POPA, AT));
    }
}