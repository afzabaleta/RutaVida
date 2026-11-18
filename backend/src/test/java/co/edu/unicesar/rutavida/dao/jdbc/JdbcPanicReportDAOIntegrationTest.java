package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JdbcPanicReportDAO against the local Oracle, in rolled-back transactions.
 * Skipped without Oracle or without 07_boton_panico.sql. The reports are dated in 2099 so
 * the queries of "the last minutes" never see real reports of the database.
 */
class JdbcPanicReportDAOIntegrationTest {

    private static final LocalDateTime T = LocalDateTime.of(2099, 1, 1, 10, 0);
    private static final GeoPoint PARQUE_LA_POPA = new GeoPoint(10.467172, -73.265099);

    private static JdbcTransactionManager jdbc;
    private static JdbcPanicReportDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        assumeTrue(number(jdbc, "SELECT COUNT(*) FROM user_tables WHERE table_name = 'REPORTE_PANICO'") == 1,
                "Run database/07_boton_panico.sql first");
        dao = new JdbcPanicReportDAO(jdbc);
    }

    private static PanicReport report(String device, LocalDateTime at) {
        return new PanicReport(0, PARQUE_LA_POPA, at, device, null, null, "PRUEBA A20", PanicReportStatus.PENDING,
                null);
    }

    /** An emergency of the system user, to link validated reports. */
    private static long createEmergency() {
        run(jdbc, "INSERT INTO emergencia (id_tipo_emergencia, id_usuario_reporta, direccion_referencia, latitud,"
                + " longitud, severidad, descripcion)"
                + " SELECT t.id_tipo_emergencia, u.id_usuario, 'PRUEBA A20', 10.467172, -73.265099, 'ALTA', 'PRUEBA'"
                + " FROM tipo_emergencia t, usuario u"
                + " WHERE t.nombre = 'EMERGENCIA_MEDICA' AND u.username = 'sistema.rutavida'");
        return number(jdbc, "SELECT MAX(id_emergencia) FROM emergencia WHERE direccion_referencia = 'PRUEBA A20'");
    }

    @Test
    void aReportIsSavedAndReadBack() {
        inRolledBackTransaction(jdbc, () -> {
            PanicReport report = report("PRUEBA-A20-1", T);
            long id = dao.insert(report);

            assertTrue(id > 0);
            assertEquals(id, report.getId());
            PanicReport read = dao.findById(id).orElseThrow();
            assertEquals(PARQUE_LA_POPA, read.getLocation());
            assertEquals(T, read.getReportedAt());
            assertEquals("PRUEBA-A20-1", read.getDeviceId());
            assertNull(read.getUserId(), "anonymous");
            assertEquals(PanicReportStatus.PENDING, read.getStatus());
            assertNull(read.getEmergencyId());
        });
    }

    @Test
    void thePendingReportsOfTheLastMinutesAndTheCountPerDevice() {
        inRolledBackTransaction(jdbc, () -> {
            dao.insert(report("PRUEBA-A20-1", T.plusMinutes(1)));
            dao.insert(report("PRUEBA-A20-2", T));
            dao.insert(report("PRUEBA-A20-1", T.plusMinutes(2)));
            long old = dao.insert(report("PRUEBA-A20-3", T.minusMinutes(30)));

            List<PanicReport> pending = dao.findPendingSince(T);
            assertEquals(List.of("PRUEBA-A20-2", "PRUEBA-A20-1", "PRUEBA-A20-1"),
                    pending.stream().map(PanicReport::getDeviceId).toList(), "oldest first");
            assertEquals(2, dao.countByDeviceSince("PRUEBA-A20-1", T));
            assertEquals(0, dao.countByDeviceSince("PRUEBA-A20-1", T.plusMinutes(5)));

            assertEquals(1, dao.expirePendingBefore(T.minusMinutes(10)));
            assertEquals(PanicReportStatus.EXPIRED, dao.findById(old).orElseThrow().getStatus());
            assertEquals(4, dao.findSince(T.minusHours(1)).size(), "the map sees every status");
        });
    }

    @Test
    void validatedReportsAreLinkedToTheirEmergency() {
        inRolledBackTransaction(jdbc, () -> {
            long emergency = createEmergency();
            long a = dao.insert(report("PRUEBA-A20-1", T));
            long b = dao.insert(report("PRUEBA-A20-2", T));
            long c = dao.insert(report("PRUEBA-A20-3", T));

            dao.markValidated(List.of(a, b), emergency);
            dao.updateStatus(c, PanicReportStatus.DISCARDED);

            assertEquals(PanicReportStatus.VALIDATED, dao.findById(a).orElseThrow().getStatus());
            assertEquals(emergency, dao.findById(b).orElseThrow().getEmergencyId());
            assertEquals(PanicReportStatus.DISCARDED, dao.findById(c).orElseThrow().getStatus());
            assertTrue(dao.findPendingSince(T).isEmpty());
            assertThrows(NotFoundException.class, () -> dao.markValidated(List.of(999_999_999L), emergency));
            assertThrows(IllegalArgumentException.class, () -> dao.updateStatus(c, PanicReportStatus.VALIDATED));
        });
    }
}
