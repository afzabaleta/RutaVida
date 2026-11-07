package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.HospitalDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** HospitalDAO on Oracle (table HOSPITAL). */
public class JdbcHospitalDAO extends AbstractJdbcDAO implements HospitalDAO {

    private static final String SELECT =
            "SELECT id_hospital, nombre, nivel, direccion, telefono, latitud, longitud,"
                    + " capacidad_urgencias, estado FROM hospital";

    private static final String INSERT =
            "INSERT INTO hospital (nombre, nivel, direccion, telefono, latitud, longitud,"
                    + " capacidad_urgencias, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE =
            "UPDATE hospital SET nombre = ?, nivel = ?, direccion = ?, telefono = ?, latitud = ?,"
                    + " longitud = ?, capacidad_urgencias = ?, estado = ? WHERE id_hospital = ?";

    private static final RowMapper<Hospital> MAPPER = row -> new Hospital(
            row.getLong("id_hospital"),
            row.getString("nombre"),
            HospitalLevel.fromCode(row.getString("nivel")),
            row.getString("direccion"),
            row.getString("telefono"),
            new GeoPoint(row.getDouble("latitud"), row.getDouble("longitud")),
            getNullableInt(row, "capacidad_urgencias"),
            RecordStatus.fromCode(row.getString("estado")));

    public JdbcHospitalDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public Optional<Hospital> findById(long id) {
        return queryOne(SELECT + " WHERE id_hospital = ?", MAPPER, id);
    }

    @Override
    public List<Hospital> findAll() {
        return queryList(SELECT + " ORDER BY nombre, id_hospital", MAPPER);
    }

    @Override
    public List<Hospital> findOperational() {
        return queryList(SELECT + " WHERE estado = ? ORDER BY nombre, id_hospital", MAPPER, RecordStatus.ACTIVE);
    }

    @Override
    public long insert(Hospital hospital) {
        Objects.requireNonNull(hospital, "hospital");
        if (hospital.getId() != 0) {
            throw new IllegalArgumentException("The hospital is already saved with id " + hospital.getId());
        }
        GeoPoint location = Objects.requireNonNull(hospital.getLocation(), "location");
        long id = insertReturningId(INSERT, "id_hospital",
                hospital.getName(), hospital.getLevel(), hospital.getAddress(), hospital.getPhone(),
                location.latitude(), location.longitude(), hospital.getEmergencyCapacity(), hospital.getStatus());
        hospital.setId(id);
        return id;
    }

    @Override
    public void update(Hospital hospital) {
        Objects.requireNonNull(hospital, "hospital");
        if (hospital.getId() <= 0) {
            throw new IllegalArgumentException("Only a saved hospital can be updated");
        }
        GeoPoint location = Objects.requireNonNull(hospital.getLocation(), "location");
        int rows = execute(UPDATE,
                hospital.getName(), hospital.getLevel(), hospital.getAddress(), hospital.getPhone(),
                location.latitude(), location.longitude(), hospital.getEmergencyCapacity(), hospital.getStatus(),
                hospital.getId());
        if (rows == 0) {
            throw new NotFoundException("Hospital", hospital.getId());
        }
    }
}