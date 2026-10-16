package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests (no database) of how AbstractJdbcDAO recognises the errors that our PL/SQL
 * procedures raise with RAISE_APPLICATION_ERROR (ORA-20000 … ORA-20999).
 */
class ApplicationErrorTest {

    private static SQLException oracleError(int code, String message) {
        return new SQLException(message, "72000", code);
    }

    @Test
    void codesFrom20000To20999AreApplicationErrors() {
        assertTrue(AbstractJdbcDAO.isApplicationError(oracleError(20000, "ORA-20000: x")));
        assertTrue(AbstractJdbcDAO.isApplicationError(oracleError(20004, "ORA-20004: x")));
        assertTrue(AbstractJdbcDAO.isApplicationError(oracleError(20999, "ORA-20999: x")));
    }

    @Test
    void otherOracleErrorsAreNotApplicationErrors() {
        assertFalse(AbstractJdbcDAO.isApplicationError(oracleError(1, "ORA-00001: unique constraint")));
        assertFalse(AbstractJdbcDAO.isApplicationError(oracleError(2290, "ORA-02290: check constraint")));
        assertFalse(AbstractJdbcDAO.isApplicationError(oracleError(6550, "ORA-06550: PLS-00201")));
        assertFalse(AbstractJdbcDAO.isApplicationError(oracleError(21000, "ORA-21000: x")));
    }

    @Test
    void messageKeepsOnlyTheProcedureText() {
        SQLException error = oracleError(20004,
                "ORA-20004: La unidad 5 no esta disponible (OCUPADO)\n"
                        + "ORA-06512: at \"RUTAVIDA.SP_ASIGNAR_UNIDAD\", line 32\n"
                        + "ORA-06512: at line 1\n");
        assertEquals("La unidad 5 no esta disponible (OCUPADO)", AbstractJdbcDAO.applicationMessage(error));
    }

    @Test
    void messageWithoutOraclePrefixIsReturnedAsIs() {
        assertEquals("Texto libre", AbstractJdbcDAO.applicationMessage(oracleError(20001, "Texto libre")));
        assertEquals("", AbstractJdbcDAO.applicationMessage(oracleError(20001, null)));
    }

    @Test
    void applicationErrorBecomesBusinessRuleException() {
        BusinessRuleException thrown = assertThrows(BusinessRuleException.class,
                () -> AbstractJdbcDAO.rejectIfApplicationError(
                        oracleError(20002, "ORA-20002: La emergencia 7 ya esta cerrada")));
        assertEquals("La emergencia 7 ya esta cerrada", thrown.getMessage());
    }

    @Test
    void otherErrorsAreLeftForDataAccessException() {
        AbstractJdbcDAO.rejectIfApplicationError(oracleError(1, "ORA-00001: unique constraint"));
    }
}