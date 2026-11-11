
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.HospitalDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dto.HospitalRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.List;
import java.util.Objects;

public class HospitalService {

    static final String AUDIT_MODULE = "HOSPITALES";

    private final HospitalDAO hospitalDao;
    private final AuditService auditService;
    private final TransactionManager transactions;

    public HospitalService(
            HospitalDAO hospitalDao,
            AuditService auditService,
            TransactionManager transactions) {

        this.hospitalDao = Objects.requireNonNull(hospitalDao, "hospitalDao");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public List<Hospital> listOperational() {
        return hospitalDao.findOperational();
    }

    public Hospital findById(long id) {
        return hospitalDao.findById(id)
                .orElseThrow(() -> new NotFoundException("Hospital", id));
    }

    public Hospital register(HospitalRequest request, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");

        Hospital hospital = build(0, request, RecordStatus.ACTIVE);

        return transactions.callInTransaction(() -> {
            requireUniqueName(hospital);

            long id = hospitalDao.insert(hospital);

            auditService.record(
                    session.user().getId(),
                    AUDIT_MODULE,
                    "CREAR",
                    id,
                    AuditResult.SUCCESS,
                    null
            );

            return hospital;
        });
    }

    public Hospital update(
            long id,
            HospitalRequest request,
            UserSession session) {

        ValidationUtil.requireNonNull(session, "sesión");

        return transactions.callInTransaction(() -> {
            Hospital saved = findById(id);

            Hospital hospital = build(
                    saved.getId(),
                    request,
                    saved.getStatus()
            );

            requireUniqueName(hospital);
            hospitalDao.update(hospital);

            auditService.record(
                    session.user().getId(),
                    AUDIT_MODULE,
                    "EDITAR",
                    id,
                    AuditResult.SUCCESS,
                    null
            );

            return hospital;
        });
    }

    public Hospital changeStatus(
            long id,
            RecordStatus status,
            UserSession session) {

        ValidationUtil.requireNonNull(status, "estado");
        ValidationUtil.requireNonNull(session, "sesión");

        return transactions.callInTransaction(() -> {
            Hospital saved = findById(id);

            if (saved.getStatus() == status) {
                throw new BusinessRuleException(
                        status == RecordStatus.ACTIVE
                                ? "El hospital ya está activo."
                                : "El hospital ya está inactivo."
                );
            }

            Hospital hospital = new Hospital(
                    saved.getId(),
                    saved.getName(),
                    saved.getLevel(),
                    saved.getAddress(),
                    saved.getPhone(),
                    saved.getLocation(),
                    saved.getEmergencyCapacity(),
                    status
            );

            hospitalDao.update(hospital);

            auditService.record(
                    session.user().getId(),
                    AUDIT_MODULE,
                    status == RecordStatus.ACTIVE
                            ? "ACTIVAR" : "DESACTIVAR",
                    id,
                    AuditResult.SUCCESS,
                    null
            );

            return hospital;
        });
    }

    private static Hospital build(
            long id,
            HospitalRequest request,
            RecordStatus status) {

        ValidationUtil.requireNonNull(request, "hospital");

        String phone = ValidationUtil.optionalText(
                request.phone(), "teléfono", 20
        );

        if (phone != null && !phone.matches("[0-9+() -]{7,20}")) {
            throw new ValidationException(
                    "El teléfono solo puede tener números, espacios y los signos + ( ) -."
            );
        }

        Integer capacity = request.emergencyCapacity();

        if (capacity != null && (capacity < 0 || capacity > 99999)) {
            throw new ValidationException(
                    "La capacidad de urgencias debe estar entre 0 y 99999."
            );
        }

        return new Hospital(
                id,
                ValidationUtil.requireText(request.name(), "nombre", 150),
                ValidationUtil.requireNonNull(request.level(), "nivel"),
                ValidationUtil.requireText(request.address(), "dirección", 180),
                phone,
                ValidationUtil.requireLocation(request.location()),
                capacity,
                status
        );
    }

    private void requireUniqueName(Hospital hospital) {
        boolean taken = hospitalDao.findAll().stream()
                .anyMatch(other ->
                        other.getId() != hospital.getId()
                                && other.getName().equalsIgnoreCase(
                                hospital.getName()
                        )
                );

        if (taken) {
            throw new BusinessRuleException(
                    "Ya existe un hospital con ese nombre."
            );
        }
    }
}
