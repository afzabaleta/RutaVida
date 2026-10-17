
package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.exception.ValidationException;

import java.math.BigDecimal;

/**
 * Input checks shared by the entities and services.
 * Every method throws ValidationException with a Spanish message.
 */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    /** Trims the text and rejects null, blank or overly long values. */
    public static String requireText(
            String value, String field, int maxLength) {

        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "El campo " + field + " es obligatorio.");
        }

        return checkLength(value.trim(), field, maxLength);
    }

    /** Returns null for blank optional text. */
    public static String optionalText(
            String value, String field, int maxLength) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return checkLength(value.trim(), field, maxLength);
    }

    /** Checks that a value is within the given range. */
    public static double requireInRange(
            double value, double min, double max, String field) {

        if (!(value >= min && value <= max)) {
            throw new ValidationException(
                    "El campo " + field + " debe estar entre "
                            + plain(min) + " y " + plain(max) + ".");
        }

        return value;
    }

    /** Requires a positive identifier. */
    public static long requirePositiveId(long id, String field) {

        if (id <= 0) {
            throw new ValidationException(
                    "El campo " + field + " no es válido.");
        }

        return id;
    }

    /** Rejects null required values. */
    public static <T> T requireNonNull(T value, String field) {

        if (value == null) {
            throw new ValidationException(
                    "El campo " + field + " es obligatorio.");
        }

        return value;
    }

    private static String checkLength(
            String text, String field, int maxLength) {

        if (text.length() > maxLength) {
            throw new ValidationException(
                    "El campo " + field + " admite máximo "
                            + maxLength + " caracteres.");
        }

        return text;
    }

    private static String plain(double number) {
        return BigDecimal.valueOf(number)
                .stripTrailingZeros()
                .toPlainString();
    }
}

