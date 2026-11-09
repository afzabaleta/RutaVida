package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadSegmentDAO;
import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.model.RoadSegment;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;

import java.util.List;
import java.util.Optional;

/**
 * Fakes shared by the incident tests (service and controller), without Oracle.
 * The segment and the incidents are invented for the tests.
 */
public final class IncidentTestSupport {

    /** The only segment that exists in these tests. */
    public static final long SEGMENT_ID = 413;

    public static final RoadSegment SEGMENT = new RoadSegment(
            SEGMENT_ID, 1, 2, null,
            "Calle de prueba", 120.0, 30.0, RoadSegmentStatus.OPEN
    );

    /** A report of an accident that, according to the operator, closes the street. */
    public static final IncidentRequest CLOSING_ACCIDENT = new IncidentRequest(
            SEGMENT_ID,
            IncidentType.ACCIDENT,
            Severity.HIGH,
            true,
            "Choque de prueba"
    );

    public final InMemoryRoadIncidentDAO incidentDao =
            new InMemoryRoadIncidentDAO();

    public final InMemoryAuditDAO auditDao =
            new InMemoryAuditDAO();

    public final EmergencyTestSupport.CountingTransactions transactions =
            new EmergencyTestSupport.CountingTransactions();

    public IncidentService service() {
        return new IncidentService(
                incidentDao,
                segments(),
                new AuditService(auditDao, EmergencyTestSupport.CLOCK),
                transactions,
                EmergencyTestSupport.CLOCK
        );
    }

    /** Only SEGMENT exists. */
    private static RoadSegmentDAO segments() {
        return new RoadSegmentDAO() {

            @Override
            public List<RoadSegment> findAll() {
                return List.of(SEGMENT);
            }

            @Override
            public Optional<RoadSegment> findById(long id) {
                return id == SEGMENT_ID
                        ? Optional.of(SEGMENT)
                        : Optional.empty();
            }

            @Override
            public void updateStatus(long id, RoadSegmentStatus status) {
                throw new UnsupportedOperationException(
                        "IncidentService does not change segments"
                );
            }
        };
    }
}