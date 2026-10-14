package co.edu.unicesar.rutavida.dao;

import java.util.function.Supplier;

/**
 * Runs several DAO operations as one database transaction.
 *
 * <p>Services use it for operations that touch several tables, e.g. dispatching a
 * unit: insert ASIGNACION_EMERGENCIA + update EMERGENCIA + update
 * VEHICULO_EMERGENCIA + insert AUDITORIA. Either everything is saved or nothing is.
 *
 * <pre>{@code
 * transactions.runInTransaction(() -> {
 *     assignmentDao.insert(assignment);
 *     emergencyDao.updateStatus(emergencyId, EmergencyStatus.ASSIGNED);
 *     vehicleDao.updateStatus(vehicleId, VehicleOperationalStatus.BUSY);
 * });
 * }</pre>
 *
 * <p>This interface has no JDBC types, so services do not depend on the database
 * technology (SOLID Dependency Inversion, GRASP Protected Variations).
 *
 * <p>If a transaction is already open in the current thread, the work joins it.
 * Any RuntimeException thrown by the work rolls the whole transaction back.
 */
public interface TransactionManager {

    /** Runs {@code work} in a transaction and returns its result. */
    <T> T callInTransaction(Supplier<T> work);

    /** Runs {@code work} in a transaction. */
    default void runInTransaction(Runnable work) {
        callInTransaction(() -> {
            work.run();
            return null;
        });
    }
}
