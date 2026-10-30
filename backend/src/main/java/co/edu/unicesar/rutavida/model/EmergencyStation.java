package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.RecordStatus;

/**
 * A fire station or ambulance base (table ESTACION_EMERGENCIA).
 * Only data: the business rules live in the service layer.
 */
public class EmergencyStation {

    private long id;
    private final String name;
    private final String address;
    private final String neighborhood;
    private final String phone;
    private final GeoPoint location;
    private final RecordStatus status;

    public EmergencyStation(long id,
            String name,
            String address,
            String neighborhood,
            String phone,
            GeoPoint location,
            RecordStatus status) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.neighborhood = neighborhood;
        this.phone = phone;
        this.location = location;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getPhone() {
        return phone;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
