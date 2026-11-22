package co.edu.unicesar.rutavida.dao.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Creates new physical connections. {@code DatabaseConnection::open} satisfies it;
 * tests can pass a fake.
 */
@FunctionalInterface
public interface ConnectionFactory {

    Connection open() throws SQLException;
}
