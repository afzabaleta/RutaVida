package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.model.VehicleType;

import java.util.List;

/** VehicleTypeDAO on Oracle (table TIPO_VEHICULO). Read only: the catalog comes from 02_datos_iniciales.sql. */
public class JdbcVehicleTypeDAO extends AbstractJdbcDAO implements VehicleTypeDAO {

    private static final String SELECT_ALL =
            "SELECT id_tipo_vehiculo, nombre, prioridad, descripcion FROM tipo_vehiculo ORDER BY nombre";

    private static final RowMapper<VehicleType> MAPPER = row -> new VehicleType(
            row.getLong("id_tipo_vehiculo"),
            row.getString("nombre"),
            row.getInt("prioridad"),
            row.getString("descripcion"));

    public JdbcVehicleTypeDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public List<VehicleType> findAll() {
        return queryList(SELECT_ALL, MAPPER);
    }
}