package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.VehicleLocationDAO;
import co.edu.unicesar.rutavida.model.VehicleLocation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fake VehicleLocationDAO for tests: keeps the position history in a list, without Oracle. */
public class InMemoryVehicleLocationDAO implements VehicleLocationDAO {

    public final List<VehicleLocation> saved = new ArrayList<>();
    public int batches;

    @Override
    public void insertBatch(List<VehicleLocation> locations) {
        batches++;
        saved.addAll(locations);
    }

    @Override
    public List<VehicleLocation> findByVehicle(long vehicleId, LocalDateTime from, LocalDateTime to) {
        return saved.stream()
                .filter(location -> location.getVehicleId() == vehicleId)
                .filter(location -> !location.getRecordedAt().isBefore(from) && !location.getRecordedAt().isAfter(to))
                .sorted(Comparator.comparing(VehicleLocation::getRecordedAt))
                .toList();
    }
}