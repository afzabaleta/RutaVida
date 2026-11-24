package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.UnitDriverDAO;

import java.util.OptionalLong;

/** Oracle implementation of UNIDAD_CONDUCTOR, installed by database/14_gps_conductor.sql. */
public final class JdbcUnitDriverDAO extends AbstractJdbcDAO implements UnitDriverDAO {

    private static final String BY_USER = "SELECT id_vehiculo FROM unidad_conductor WHERE id_usuario = ?";
    private static final String BY_VEHICLE = "SELECT id_usuario FROM unidad_conductor WHERE id_vehiculo = ?";
    private static final String DELETE_USER = "DELETE FROM unidad_conductor WHERE id_usuario = ?";
    private static final String DELETE_VEHICLE = "DELETE FROM unidad_conductor WHERE id_vehiculo = ?";
    private static final String INSERT = "INSERT INTO unidad_conductor (id_usuario, id_vehiculo) VALUES (?, ?)";

    public JdbcUnitDriverDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public OptionalLong findVehicleIdByUser(long userId) {
        return queryOne(BY_USER, row -> row.getLong("id_vehiculo"), userId)
                .map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    @Override
    public OptionalLong findUserIdByVehicle(long vehicleId) {
        return queryOne(BY_VEHICLE, row -> row.getLong("id_usuario"), vehicleId)
                .map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    @Override
    public void assign(long userId, long vehicleId) {
        connections.withConnection(connection -> {
            try (var removeUser = connection.prepareStatement(DELETE_USER);
                 var removeVehicle = connection.prepareStatement(DELETE_VEHICLE);
                 var insert = connection.prepareStatement(INSERT)) {
                removeUser.setLong(1, userId);
                removeUser.executeUpdate();
                removeVehicle.setLong(1, vehicleId);
                removeVehicle.executeUpdate();
                insert.setLong(1, userId);
                insert.setLong(2, vehicleId);
                insert.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void removeByVehicle(long vehicleId) {
        execute(DELETE_VEHICLE, vehicleId);
    }
}
