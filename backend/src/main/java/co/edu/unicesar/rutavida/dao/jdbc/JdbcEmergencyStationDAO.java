package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.EmergencyStationDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** EmergencyStationDAO on Oracle (table ESTACION_EMERGENCIA). */
public class JdbcEmergencyStationDAO extends AbstractJdbcDAO implements EmergencyStationDAO {

    private static final String SELECT =
            "SELECT id_estacion, nombre, direccion, barrio, telefono, latitud, longitud, estado"
                    + " FROM estacion_emergencia";

    private static final String INSERT =
            "INSERT INTO estacion_emergencia (nombre, direccion, barrio, telefono, latitud, longitud, estado)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE =
            "UPDATE estacion_emergencia SET nombre = ?, direccion = ?, barrio = ?, telefono = ?,"
                    + " latitud = ?, longitud = ?, estado = ? WHERE id_estacion = ?";

    private static final RowMapper<EmergencyStation> MAPPER = row -> new EmergencyStation(
            row.getLong("id_estacion"),
            row.getString("nombre"),
            row.getString("direccion"),
            row.getString("barrio"),
            row.getString("telefono"),
            new GeoPoint(row.getDouble("latitud"), row.getDouble("longitud")),
            RecordStatus.fromCode(row.getString("estado")));

    public JdbcEmergencyStationDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public Optional<EmergencyStation> findById(long id) {
        return queryOne(SELECT + " WHERE id_estacion = ?", MAPPER, id);
    }

    @Override
    public List<EmergencyStation> findAll() {
        return queryList(SELECT + " ORDER BY nombre, id_estacion", MAPPER);
    }

    @Override
    public long insert(EmergencyStation station) {
        Objects.requireNonNull(station, "station");
        if (station.getId() != 0) {
            throw new IllegalArgumentException("The station is already saved with id " + station.getId());
        }
        GeoPoint location = Objects.requireNonNull(station.getLocation(), "location");
        long id = insertReturningId(INSERT, "id_estacion",
                station.getName(), station.getAddress(), station.getNeighborhood(), station.getPhone(),
                location.latitude(), location.longitude(), station.getStatus());
        station.setId(id);
        return id;
    }

    @Override
    public void update(EmergencyStation station) {
        Objects.requireNonNull(station, "station");
        if (station.getId() <= 0) {
            throw new IllegalArgumentException("Only a saved station can be updated");
        }
        GeoPoint location = Objects.requireNonNull(station.getLocation(), "location");
        int rows = execute(UPDATE,
                station.getName(), station.getAddress(), station.getNeighborhood(), station.getPhone(),
                location.latitude(), location.longitude(), station.getStatus(), station.getId());
        if (rows == 0) {
            throw new NotFoundException("Estación", station.getId());
        }
    }
}