
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HospitalTest {

    private static final GeoPoint LOCATION =
            new GeoPoint(10.4631, -73.2532);

    private static final HospitalLevel LEVEL =
            HospitalLevel.values()[0];

    @Test
    void createsAValidHospital() {
        Hospital hospital = new Hospital(
                0, "Hospital Central", LEVEL,
                "Calle 16", "+57 300 1234567",
                LOCATION, 20, RecordStatus.ACTIVE);

        assertEquals(0, hospital.getId());
        assertEquals("Hospital Central", hospital.getName());
        assertEquals(LEVEL, hospital.getLevel());
        assertEquals("Calle 16", hospital.getAddress());
        assertEquals(LOCATION, hospital.getLocation());
        assertEquals(20, hospital.getEmergencyCapacity());
        assertTrue(hospital.isOperational());
    }

    @Test
    void rejectsPhoneWithLetters() {
        assertThrows(ValidationException.class, () ->
                new Hospital(
                        0, "Hospital Central", LEVEL,
                        "Calle 16", "ABC1234567",
                        LOCATION, 20, RecordStatus.ACTIVE));
    }

    @Test
    void rejectsNullLocation() {
        assertThrows(ValidationException.class, () ->
                new Hospital(
                        0, "Hospital Central", LEVEL,
                        "Calle 16", null,
                        null, 20, RecordStatus.ACTIVE));
    }

    @Test
    void rejectsNegativeEmergencyCapacity() {
        assertThrows(ValidationException.class, () ->
                new Hospital(
                        0, "Hospital Central", LEVEL,
                        "Calle 16", null,
                        LOCATION, -1, RecordStatus.ACTIVE));
    }

    @Test
    void acceptsNullEmergencyCapacity() {
        Hospital hospital = new Hospital(
                0, "Hospital Central", LEVEL,
                "Calle 16", null,
                LOCATION, null, RecordStatus.ACTIVE);

        assertNull(hospital.getEmergencyCapacity());
    }

    @Test
    void operationalStatusDependsOnRecordStatus() {
        Hospital active = new Hospital(
                0, "Hospital Central", LEVEL,
                "Calle 16", null,
                LOCATION, 20, RecordStatus.ACTIVE);

        Hospital inactive = new Hospital(
                0, "Hospital Central", LEVEL,
                "Calle 16", null,
                LOCATION, 20, RecordStatus.INACTIVE);

        assertTrue(active.isOperational());
        assertFalse(inactive.isOperational());
    }

    @Test
    void rejectsNullLevelOrStatus() {
        assertThrows(ValidationException.class, () ->
                new Hospital(
                        0, "Hospital Central", null,
                        "Calle 16", null,
                        LOCATION, 20, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () ->
                new Hospital(
                        0, "Hospital Central", LEVEL,
                        "Calle 16", null,
                        LOCATION, 20, null));
    }

    @Test
    void assignIdOnlyWorksOnce() {
        Hospital hospital = new Hospital(
                0, "Hospital Central", LEVEL,
                "Calle 16", null,
                LOCATION, 20, RecordStatus.ACTIVE);

        hospital.assignId(8);

        assertEquals(8, hospital.getId());

        assertThrows(IllegalStateException.class,
                () -> hospital.assignId(9));
    }
}
