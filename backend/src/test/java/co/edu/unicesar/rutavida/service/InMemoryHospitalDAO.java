
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.HospitalDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake HospitalDAO for service tests:
 * keeps hospitals in memory without Oracle.
 */
public class InMemoryHospitalDAO implements HospitalDAO {

    private final Map<Long, Hospital> hospitals = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Optional<Hospital> findById(long id) {
        return Optional.ofNullable(hospitals.get(id));
    }

    @Override
    public List<Hospital> findAll() {
        return hospitals.values().stream()
                .sorted(Comparator.comparing(Hospital::getName))
                .toList();
    }

    @Override
    public List<Hospital> findOperational() {
        return findAll().stream()
                .filter(hospital ->
                        hospital.getStatus() == RecordStatus.ACTIVE)
                .toList();
    }

    @Override
    public long insert(Hospital hospital) {
        long id = nextId++;
        hospital.setId(id);
        hospitals.put(id, hospital);
        return id;
    }

    @Override
    public void update(Hospital hospital) {
        if (!hospitals.containsKey(hospital.getId())) {
            throw new NotFoundException("Hospital", hospital.getId());
        }
        hospitals.put(hospital.getId(), hospital);
    }
}
