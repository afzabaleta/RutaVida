package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.Severity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JSON rules of docs/api.md, section 1. Needs Jackson, not Oracle. */
class JsonMapperFactoryTest {

    private final ObjectMapper json = JsonMapperFactory.create();

    /** A request with a catalog value, like the ones of A12b. */
    record Sample(Severity severity, LocalDateTime at, GeoPoint location) {
    }

    @Test
    void catalogValuesTravelWithTheirSpanishCode() throws Exception {
        String written = json.writeValueAsString(Map.of("severity", Severity.CRITICAL));
        assertEquals("{\"severity\":\"CRITICA\"}", written);

        Sample read = json.readValue("{\"severity\":\"ALTA\"}", Sample.class);
        assertEquals(Severity.HIGH, read.severity());
    }

    @Test
    void anUnknownCodeIsRejected() {
        assertThrows(JsonProcessingException.class,
                () -> json.readValue("{\"severity\":\"HIGH\"}", Sample.class),
                "the Java name is not accepted, only the code");
    }

    @Test
    void datesAreIsoTextAndPointsAreObjects() throws Exception {
        Sample sample = new Sample(Severity.LOW, LocalDateTime.of(2026, 11, 4, 9, 30, 15),
                new GeoPoint(10.467172, -73.265099));

        String written = json.writeValueAsString(sample);

        assertTrue(written.contains("\"at\":\"2026-11-04T09:30:15\""), written);
        assertTrue(written.contains("\"location\":{\"latitude\":10.467172,\"longitude\":-73.265099}"), written);
        assertEquals(sample, json.readValue(written, Sample.class));
    }

    @Test
    void entitiesUseTheNamesOfTheirGettersAndUnknownFieldsAreIgnored() throws Exception {
        JsonNode written = json.readTree(json.writeValueAsString(
                List.of(new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Choque"))));
        JsonNode type = written.get(0);
        assertEquals(4, type.size(), "id, name, priority and description; nothing else");
        assertEquals(3, type.get("id").asInt());
        assertEquals("ACCIDENTE_VIAL", type.get("name").asText());
        assertEquals(8, type.get("priority").asInt());
        assertEquals("Choque", type.get("description").asText());

        Sample read = json.readValue("{\"severity\":\"BAJA\",\"reportedAt\":\"ignorado\"}", Sample.class);
        assertEquals(Severity.LOW, read.severity());
    }
}