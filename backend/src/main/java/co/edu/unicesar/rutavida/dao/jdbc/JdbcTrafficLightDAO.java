package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.TrafficLightDAO;
import co.edu.unicesar.rutavida.model.TrafficLight;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;

import java.util.List;

/** TrafficLightDAO on Oracle (table SEMAFORO). */
public class JdbcTrafficLightDAO extends AbstractJdbcDAO implements TrafficLightDAO {

    private static final String SELECT_ALL =
            "SELECT id_semaforo, id_nodo, codigo, estado_actual, tiempo_ciclo_seg, sincronizado, estado"
                    + " FROM semaforo ORDER BY id_semaforo";

    private static final RowMapper<TrafficLight> MAPPER = row -> new TrafficLight(
            row.getLong("id_semaforo"),
            row.getLong("id_nodo"),
            row.getString("codigo"),
            TrafficLightState.fromCode(row.getString("estado_actual")),
            getNullableInt(row, "tiempo_ciclo_seg"),
            getFlag(row, "sincronizado"),
            RecordStatus.fromCode(row.getString("estado")));

    public JdbcTrafficLightDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public List<TrafficLight> findAll() {
        return queryList(SELECT_ALL, MAPPER);
    }
}