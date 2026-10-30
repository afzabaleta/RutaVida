package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;

/**
 * A traffic light placed on a road node (table SEMAFORO). RutaVida never controls real traffic lights.
 * Only data: the business rules live in the service layer.
 */
public class TrafficLight {

    private long id;
    private final long nodeId;
    private final String code;
    private TrafficLightState currentState;
    private final Integer cycleSeconds;
    private final boolean synchronizedSignal;
    private final RecordStatus status;

    public TrafficLight(long id,
            long nodeId,
            String code,
            TrafficLightState currentState,
            Integer cycleSeconds,
            boolean synchronizedSignal,
            RecordStatus status) {
        this.id = id;
        this.nodeId = nodeId;
        this.code = code;
        this.currentState = currentState;
        this.cycleSeconds = cycleSeconds;
        this.synchronizedSignal = synchronizedSignal;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public void setCurrentState(TrafficLightState currentState) {
        this.currentState = currentState;
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
}
