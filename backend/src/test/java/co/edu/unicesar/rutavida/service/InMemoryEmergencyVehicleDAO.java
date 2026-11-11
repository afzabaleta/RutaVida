package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake EmergencyVehicleDAO for tests, without Oracle. Like a database, it keeps copies: a
 * change to a unit is only saved through updateStatus, updateLocation or update.
 */
public class InMemoryEmergencyVehicleDAO implements EmergencyVehicleDAO {

    private final Map<Long, EmergencyVehicle> saved = new LinkedHashMap<>();
    private long nextId = 1;
    public int locationUpdates;

    @Override
    public Optional<EmergencyVehicle> findById(long id) {
        return Optional.ofNullable(saved.get(id)).map(InMemoryEmergencyVehicleDAO::copy);
    }

    @Override
    public List<EmergencyVehicle> findAll() {
        return saved.values().stream()
                .sorted(Comparator.comparing(EmergencyVehicle::getUnitCode))
                .map(InMemoryEmergencyVehicleDAO::copy)
                .toList();
    }

    @Override
    public List<EmergencyVehicle> findAvailable() {
        return findAll().stream()
                .filter(vehicle -> vehicle.getOperationalStatus() == VehicleOperationalStatus.AVAILABLE)
                .toList();
    }

    @Override
    public long insert(EmergencyVehicle vehicle) {
        long id = nextId++;
        vehicle.setId(id);
        saved.put(id, copy(vehicle));
        return id;
    }

    @Override
    public void update(EmergencyVehicle vehicle) {
        EmergencyVehicle old = existing(vehicle.getId());
        EmergencyVehicle changed = new EmergencyVehicle(vehicle.getId(), vehicle.getVehicleTypeId(),
                vehicle.getStationId(), vehicle.getUnitCode(), vehicle.getPlate(),
                old.getOperationalStatus(), old.getLastLocation(), old.getLastLocationAt());
        saved.put(vehicle.getId(), changed);
    }

    @Override
    public void updateStatus(long id, VehicleOperationalStatus status) {
        existing(id).setOperationalStatus(status);
    }

    @Override
    public void updateLocation(long id, GeoPoint location, LocalDateTime at) {
        EmergencyVehicle vehicle = existing(id);
        vehicle.setLastLocation(location);
        vehicle.setLastLocationAt(at);
        locationUpdates++;
    }

    private EmergencyVehicle existing(long id) {
        EmergencyVehicle vehicle = saved.get(id);
        if (vehicle == null) {
            throw new NotFoundException("Unidad", id);
        }
        return vehicle;
    }

    private static EmergencyVehicle copy(EmergencyVehicle source) {
        return new EmergencyVehicle(source.getId(), source.getVehicleTypeId(), source.getStationId(),
                source.getUnitCode(), source.getPlate(), source.getOperationalStatus(),
                source.getLastLocation(), source.getLastLocationAt());
    }
}