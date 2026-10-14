package co.edu.unicesar.rutavida.model.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract between Java and Oracle: the codes of every status enum must be exactly the
 * values allowed by the matching CHECK constraint in database/01_esquema.sql.
 * If someone changes one side and forgets the other, this test fails.
 */
class EnumSqlContractTest {

    private static final Path SCHEMA = Path.of("..", "database", "01_esquema.sql");

    /** enum → CHECK constraints that must allow exactly its codes. */
    private static final Map<Class<? extends CodedEnum>, List<String>> CONTRACT = new LinkedHashMap<>();

    static {
        CONTRACT.put(EmergencyStatus.class, List.of("ck_emergencia_estado"));
        CONTRACT.put(Severity.class, List.of("ck_emergencia_severidad", "ck_incidente_severidad"));
        CONTRACT.put(AssignmentStatus.class, List.of("ck_asignacion_estado"));
        CONTRACT.put(VehicleOperationalStatus.class, List.of("ck_vehiculo_estado"));
        CONTRACT.put(IncidentStatus.class, List.of("ck_incidente_estado"));
        CONTRACT.put(IncidentType.class, List.of("ck_incidente_tipo"));
        CONTRACT.put(IncidentOrigin.class, List.of("ck_incidente_origen"));
        CONTRACT.put(RoadSegmentStatus.class, List.of("ck_tramo_estado"));
        CONTRACT.put(NodeType.class, List.of("ck_nodo_vial_tipo"));
        CONTRACT.put(TrafficLevel.class, List.of("ck_trafico_nivel"));
        CONTRACT.put(TrafficSource.class, List.of("ck_trafico_fuente"));
        CONTRACT.put(PositionSource.class, List.of("ck_ubicacion_fuente"));
        CONTRACT.put(TrafficLightState.class, List.of("ck_semaforo_estado_act"));
        CONTRACT.put(RouteType.class, List.of("ck_ruta_tipo"));
        CONTRACT.put(RouteStatus.class, List.of("ck_ruta_estado"));
        CONTRACT.put(HospitalLevel.class, List.of("ck_hospital_nivel"));
        CONTRACT.put(AlertType.class, List.of("ck_alerta_tipo"));
        CONTRACT.put(AuditResult.class, List.of("ck_auditoria_resultado"));
        CONTRACT.put(RecordStatus.class, List.of("ck_rol_estado", "ck_usuario_estado", "ck_hospital_estado"));
    }

    @Test
    void enumCodesMatchCheckConstraints() throws IOException {
        assertTrue(Files.isRegularFile(SCHEMA), "Schema not found: " + SCHEMA.toAbsolutePath());
        String sql = Files.readString(SCHEMA, StandardCharsets.UTF_8);

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<Class<? extends CodedEnum>, List<String>> entry : CONTRACT.entrySet()) {
            Set<String> javaCodes = codes(entry.getKey());
            for (String constraint : entry.getValue()) {
                Set<String> sqlValues = checkValues(sql, constraint);
                if (!javaCodes.equals(sqlValues)) {
                    mismatches.add(entry.getKey().getSimpleName() + " " + javaCodes
                            + " vs " + constraint + " " + sqlValues);
                }
            }
        }
        assertTrue(mismatches.isEmpty(), "Java and SQL catalogs differ:\n  " + String.join("\n  ", mismatches));
    }

    @Test
    void everyCodeRoundTrips() {
        for (Class<? extends CodedEnum> type : CONTRACT.keySet()) {
            for (CodedEnum constant : type.getEnumConstants()) {
                assertEquals(constant, fromCode(type, constant.code()));
            }
        }
    }

    @Test
    void unknownCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EmergencyStatus.fromCode("Reportada"));
        assertThrows(IllegalArgumentException.class, () -> Severity.fromCode(null));
    }

    // ------------------------------------------------------------------ helpers

    private static Set<String> codes(Class<? extends CodedEnum> type) {
        return Arrays.stream(type.getEnumConstants()).map(CodedEnum::code)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> checkValues(String sql, String constraint) {
        Pattern pattern = Pattern.compile("CONSTRAINT\\s+" + constraint + "\\s+CHECK\\s*\\(\\s*\\w+\\s+IN\\s*\\(([^)]*)\\)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), "CHECK constraint not found in schema: " + constraint);
        return Arrays.stream(matcher.group(1).split(","))
                .map(value -> value.trim().replace("'", ""))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CodedEnum fromCode(Class<? extends CodedEnum> type, String code) {
        return (CodedEnum) CodedEnum.fromCode((Class) type, code);
    }
}
