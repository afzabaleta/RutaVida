
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.Locale;

/**
 * A traffic light placed on a road node (SEMAFORO).
 * RutaVida only reads or simulates its state:
 * it never controls a real traffic light.
 */
public class TrafficLight {

    private long id;
    private final long nodeId;
    private final String code;
    private TrafficLightState currentState;
    private final Integer cycleSeconds;
    private final boolean synchronizedSignal;
    private final RecordStatus status;

    public TrafficLight(long id, long nodeId, String code,
                        TrafficLightState currentState,
                        Integer cycleSeconds,
                        boolean synchronizedSignal,
                        RecordStatus status) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        if (cycleSeconds != null
                && (cycleSeconds < 1 || cycleSeconds > 9999)) {
            throw new ValidationException(
                    "El tiempo de ciclo debe estar entre 1 y 9999 segundos.");
        }

        this.id = id;
        this.nodeId = ValidationUtil.requirePositiveId(
                nodeId, "nodo");
        this.code = ValidationUtil.requireText(
                        code, "código del semáforo", 30)
                .toUpperCase(Locale.ROOT);
        this.currentState = ValidationUtil.requireNonNull(
                currentState, "estado del semáforo");
        this.cycleSeconds = cycleSeconds;
        this.synchronizedSignal = synchronizedSignal;
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
    }

    /** Assigns an identifier only to an unsaved traffic light. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }

        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    public long getId() {
        return id;
    }

    public long getNodeId() {
        return nodeId;
    }

    public String getCode() {
        return code;
    }

    public TrafficLightState getCurrentState() {
        return currentState;
    }

    public Integer getCycleSeconds() {
        return cycleSeconds;
    }

    public boolean isSynchronizedSignal() {
        return synchronizedSignal;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    /** Changes the light shown by the simulation (CU-18). */
    public void changeState(TrafficLightState next) {
        currentState = ValidationUtil.requireNonNull(
                next, "nuevo estado del semáforo");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof TrafficLight that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "TrafficLight{id=" + id
                + ", code=" + code
                + ", state=" + currentState + "}";
    }
}
