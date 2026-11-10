package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.Alert;

import java.util.List;
import java.util.Optional;

/** Operational alerts of the users (table ALERTA). */
public interface AlertDAO {

    long insert(Alert alert);

    Optional<Alert> findById(long id);

    List<Alert> findByUser(long userId, int limit);

    List<Alert> findUnreadByUser(long userId);

    void markAsRead(long id);
}