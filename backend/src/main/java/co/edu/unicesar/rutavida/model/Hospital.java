package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

/**
 * A hospital that can receive patients (table HOSPITAL).
 * Only data: the business rules live in the service layer.
 */
public class Hospital {

    private long id;
    private final String name;
    private final HospitalLevel level;
    private final String address;
    private final String phone;
    private final GeoPoint location;
    private final Integer emergencyCapacity;
    private final RecordStatus status;

    public Hospital(long id,
            String name,
            HospitalLevel level,
            String address,
            String phone,
            GeoPoint location,
            Integer emergencyCapacity,
            RecordStatus status) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.address = address;
        this.phone = phone;
        this.location = location;
        this.emergencyCapacity = emergencyCapacity;
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

    public HospitalLevel getLevel() {
        return level;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public Integer getEmergencyCapacity() {
        return emergencyCapacity;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
