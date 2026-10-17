package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.User;

import java.util.List;
import java.util.Optional;

/** Persistence of users (table USUARIO). */
public interface UserDAO {

    Optional<User> findById(long id);

    /** Search for the login: the username is compared in lower case, as User stores it. */
    Optional<User> findByUsername(String username);

    /** All users, ordered by username. */
    List<User> findAll();

    /**
     * Saves a new user (id 0), assigns the generated id to it with {@code assignId} and
     * returns that id. A repeated document, email or username is rejected by Oracle.
     */
    long insert(User user);

    /** Saves every field of an existing user (id greater than 0). */
    void update(User user);
}