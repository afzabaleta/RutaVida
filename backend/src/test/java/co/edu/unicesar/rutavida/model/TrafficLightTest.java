
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrafficLightTest {

    @Test
    void createsAValidTrafficLightWithItsCodeInCapitals() {
        TrafficLight light = new TrafficLight(
                0, 4, " sem-0001 ",
                TrafficLightState.GREEN,
                90, true, RecordStatus.ACTIVE
        );

        assertEquals(4, light.getNodeId());
        assertEquals("SEM-0001", light.getCode());
        assertEquals(TrafficLightState.GREEN, light.getCurrentState());
        assertEquals(90, light.getCycleSeconds());
        assertTrue(light.isSynchronizedSignal());
        assertTrue(light.isActive());
    }

    @Test
    void cycleIsOptionalButMustFitTheColumn() {
        assertNull(new TrafficLight(
                0, 4, "SEM-1",
                TrafficLightState.RED,
                null, false, RecordStatus.ACTIVE
        ).getCycleSeconds());

        assertThrows(ValidationException.class, () ->
                new TrafficLight(0, 4, "SEM-1",
                        TrafficLightState.RED, 0,
                        false, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () ->
                new TrafficLight(0, 4, "SEM-1",
                        TrafficLightState.RED, 10_000,
                        false, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () ->
                new TrafficLight(0, 0, "SEM-1",
                        TrafficLightState.RED, 60,
                        false, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () ->
                new TrafficLight(0, 4, " ",
                        TrafficLightState.RED, 60,
                        false, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () ->
                new TrafficLight(0, 4, "SEM-1",
                        null, 60,
                        false, RecordStatus.ACTIVE));
    }

    @Test
    void theSimulationChangesTheLight() {
        TrafficLight light = new TrafficLight(
                0, 4, "SEM-1",
                TrafficLightState.GREEN,
                60, false, RecordStatus.ACTIVE
        );

        light.changeState(TrafficLightState.RED);

        assertEquals(TrafficLightState.RED, light.getCurrentState());

        assertThrows(ValidationException.class, () ->
                light.changeState(null));
    }
}
