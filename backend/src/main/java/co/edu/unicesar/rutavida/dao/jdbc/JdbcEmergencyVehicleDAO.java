package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * EmergencyVehicleDAO on Oracle (table VEHICULO_EMERGENCIA). The procedures sp_asignar_unidad
 * and sp_cerrar_emergencia also change estado_operativo (OCUPADO / DISPONIBLE).
 */
public class JdbcEmergencyVehicleDAO extends AbstractJdbcDAO implements EmergencyVehicleDAO {

    private static final String SELECT =
            "SELECT id_vehiculo, id_tipo_vehiculo, id_estacion, codigo_unidad, placa, estado_operativo,"
                    + " latitud, longitud, fecha_actualizacion FROM vehiculo_emergencia";

    private static final String INSERT =
            "INSERT INTO vehiculo_emergencia (id_tipo_vehiculo, id_estacion, codigo_unidad, placa,"
                    + " estado_operativo, latitud, longitud, fecha_actualizacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE =
            "UPDATE vehiculo_emergencia SET id_tipo_vehiculo = ?, id_estacion = ?, codigo_unidad = ?, placa = ?"
                    + " WHERE id_vehiculo = ?";

    private static final String UPDATE_STATUS =
            "UPDATE vehiculo_emergencia SET estado_operativo = ? WHERE id_vehiculo = ?";

    private static final String UPDATE_LOCATION =
            "UPDATE vehiculo_emergencia SET latitud = ?, longitud = ?, fecha_actualizacion = ? WHERE id_vehiculo = ?";

    private static final RowMapper<EmergencyVehicle> MAPPER = row -> new EmergencyVehicle(
            row.getLong("id_vehiculo"),
            row.getLong("id_tipo_vehiculo"),
            row.getLong("id_estacion"),
            row.getString("codigo_unidad"),
            row.getString("placa"),
            VehicleOperationalStatus.fromCode(row.getString("estado_operativo")),
            getNullableLocation(row),
            getLocalDateTime(row, "fecha_actualizacion"));

    public JdbcEmergencyVehicleDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public Optional<EmergencyVehicle> findById(long id) {
        return queryOne(SELECT + " WHERE id_vehiculo = ?", MAPPER, id);
    }

    @Override
    public List<EmergencyVehicle> findAll() {
        return queryList(SELECT + " ORDER BY codigo_unidad", MAPPER);
    }

    @Override
    public List<EmergencyVehicle> findAvailable() {
        return queryList(SELECT + " WHERE estado_operativo = ? ORDER BY codigo_unidad",
                MAPPER, VehicleOperationalStatus.AVAILABLE);
    }

    @Override
    public long insert(EmergencyVehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");
        if (vehicle.getId() != 0) {
            throw new IllegalArgumentException("The vehicle is already saved with id " + vehicle.getId());
        }
        GeoPoint location = vehicle.getLastLocation();
        long id = insertReturningId(INSERT, "id_vehiculo",
                vehicle.getVehicleTypeId(), vehicle.getStationId(), vehicle.getUnitCode(), vehicle.getPlate(),
                vehicle.getOperationalStatus(),
                location == null ? null : location.latitude(),
                location == null ? null : location.longitude(),
                vehicle.getLastLocationAt());
        vehicle.setId(id);
        return id;
    }

    @Override
    public void update(EmergencyVehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");
        if (vehicle.getId() <= 0) {
            throw new IllegalArgumentException("Only a saved vehicle can be updated");
        }
        int rows = execute(UPDATE, vehicle.getVehicleTypeId(), vehicle.getStationId(),
                vehicle.getUnitCode(), vehicle.getPlate(), vehicle.getId());
        requireFound(rows, vehicle.getId());
    }

    @Override
    public void updateStatus(long id, VehicleOperationalStatus status) {
        Objects.requireNonNull(status, "status");
        requireFound(execute(UPDATE_STATUS, status, id), id);
    }

    @Override
    public void updateLocation(long id, GeoPoint location, LocalDateTime at) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(at, "at");
        requireFound(execute(UPDATE_LOCATION, location.latitude(), location.longitude(), at, id), id);
    }

    private static void requireFound(int rows, long id) {
        if (rows == 0) {
            throw new NotFoundException("Unidad", id);
        }
    }

    /** latitud and longitud are both NULL until the first position (ck_vehiculo_posicion). */
    private static GeoPoint getNullableLocation(ResultSet row) throws SQLException {
        double latitude = row.getDouble("latitud");
        if (row.wasNull()) {
            return null;
        }
        return new GeoPoint(latitude, row.getDouble("longitud"));
    }
}