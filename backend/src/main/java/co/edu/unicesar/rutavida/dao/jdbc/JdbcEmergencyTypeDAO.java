package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.model.EmergencyType;

import java.util.List;
import java.util.Optional;

/** EmergencyTypeDAO on Oracle (table TIPO_EMERGENCIA). Read only: the catalog comes from 02_datos_iniciales.sql. */
public class JdbcEmergencyTypeDAO extends AbstractJdbcDAO implements EmergencyTypeDAO {

    private static final String SELECT =
            "SELECT id_tipo_emergencia, nombre, prioridad, descripcion FROM tipo_emergencia";

    private static final RowMapper<EmergencyType> MAPPER = row -> new EmergencyType(
            row.getLong("id_tipo_emergencia"),
            row.getString("nombre"),
            row.getInt("prioridad"),
            row.getString("descripcion"));

    public JdbcEmergencyTypeDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public Optional<EmergencyType> findById(long id) {
        return queryOne(SELECT + " WHERE id_tipo_emergencia = ?", MAPPER, id);
    }

    @Override
    public List<EmergencyType> findAll() {
        return queryList(SELECT + " ORDER BY prioridad DESC, nombre", MAPPER);
    }
}