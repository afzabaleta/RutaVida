
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmergencyStationTest {

    private static final GeoPoint LOCATION =
            new GeoPoint(10.4631, -73.2532);

    @Test
    void createsAValidStation() {
        EmergencyStation station = new EmergencyStation(
                0, "Estación Central", "Calle 16",
                "Centro", "+57 300 1234567",
                LOCATION, RecordStatus.ACTIVE);

        assertEquals(0, station.getId());
        assertEquals("Estación Central", station.getName());
        assertEquals("Calle 16", station.getAddress());
        assertEquals("Centro", station.getNeighborhood());
        assertEquals("+57 300 1234567", station.getPhone());
        assertEquals(LOCATION, station.getLocation());
        assertEquals(RecordStatus.ACTIVE, station.getStatus());
    }

    @Test
    void rejectsPhoneWithLetters() {
        assertThrows(ValidationException.class, () ->
                new EmergencyStation(
                        0, "Estación Central", "Calle 16",
                        null, "ABC1234567",
                        LOCATION, RecordStatus.ACTIVE));
    }

    @Test
    void rejectsNullLocation() {
        assertThrows(ValidationException.class, () ->
                new EmergencyStation(
                        0, "Estación Central", "Calle 16",
                        null, null, null,
                        RecordStatus.ACTIVE));
    }

    @Test
    void rejectsInvalidRequiredFields() {
        assertThrows(ValidationException.class, () ->
                new EmergencyStation(
                        0, " ", "Calle 16",
                        null, null, LOCATION,
                        RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () ->
                new EmergencyStation(
                        0, "Estación Central", " ",
                        null, null, LOCATION,
                        RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () ->
                new EmergencyStation(
                        0, "Estación Central", "Calle 16",
                        null, null, LOCATION, null));
    }

    @Test
    void optionalFieldsMayBeNull() {
        EmergencyStation station = new EmergencyStation(
                0, "Estación Norte", "Carrera 10",
                null, null, LOCATION, RecordStatus.ACTIVE);

        assertNull(station.getNeighborhood());
        assertNull(station.getPhone());
    }

    @Test
    void assignIdOnlyWorksOnce() {
        EmergencyStation station = new EmergencyStation(
                0, "Estación Central", "Calle 16",
                null, null, LOCATION, RecordStatus.ACTIVE);

        station.assignId(5);
        assertEquals(5, station.getId());

        assertThrows(IllegalStateException.class,
                () -> station.assignId(6));
    }
}
