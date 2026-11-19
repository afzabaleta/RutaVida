package co.edu.unicesar.rutavida.model;

import java.time.LocalDateTime;

/**
 * One service that a hospital offers (table SERVICIO_HOSPITAL), for example URGENCIAS.
 * Only data: which hospital fits a transfer is decided in the service layer (K10, RN-10).
 */
public class HospitalServiceOffering {

    private long id;
    private final long hospitalId;
    private final String serviceName;
    private final boolean available;
    private final Integer capacity;
    private final LocalDateTime updatedAt;

    /**
     * @param serviceName in capital letters, like the column (URGENCIAS, CIRUGIA...)
     * @param available   true if the hospital confirmed that the service is available now
     *                    (disponible = 'S'); the data of D2 is 'N' until it is confirmed
     * @param capacity    places of the service; null if unknown
     */
    public HospitalServiceOffering(long id, long hospitalId, String serviceName, boolean available,
                                   Integer capacity, LocalDateTime updatedAt) {
        this.id = id;
        this.hospitalId = hospitalId;
        this.serviceName = serviceName;
        this.available = available;
        this.capacity = capacity;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getHospitalId() {
        return hospitalId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public boolean isAvailable() {
        return available;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
