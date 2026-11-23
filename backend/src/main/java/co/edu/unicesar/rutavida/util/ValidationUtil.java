
package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.exception.ValidationException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Input checks shared by the services (the model classes only hold data).
 * Every method throws ValidationException with a Spanish message.
 */
public final class ValidationUtil {

    private static final Pattern PERSON_NAME = Pattern.compile("\\p{L}+(?:\\s+\\p{L}+)*");
    private static final Pattern PLACE_NAME = Pattern.compile("\\p{L}+(?:[\\s-]+\\p{L}+)*");
    private static final Pattern DOCUMENT = Pattern.compile("\\d{10}");
    private static final Pattern PHONE = Pattern.compile("\\d{10}");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

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

    /** A personal name contains letters (including accents and ñ) and spaces only. */
    public static String requirePersonName(String value, String field, int maxLength) {
        String text = requireText(value, field, maxLength).replaceAll("\\s+", " ");
        if (!PERSON_NAME.matcher(text).matches()) {
            throw new ValidationException("El campo " + field
                    + " solo puede contener letras y espacios.");
        }
        return text;
    }

    /** A station or hospital name may contain words and the existing hyphenated place names, never digits. */
    public static String requirePlaceName(String value, String field, int maxLength) {
        String text = requireText(value, field, maxLength).replaceAll("\\s+", " ");
        if (!PLACE_NAME.matcher(text).matches()) {
            throw new ValidationException("El campo " + field
                    + " solo puede contener letras, espacios o guiones; no se permiten números.");
        }
        return text;
    }

    /** Colombian identity document used by RutaVida: exactly ten digits, without separators. */
    public static String requireDocument(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("El campo documento es obligatorio.");
        }
        if (!DOCUMENT.matcher(value).matches()) {
            throw new ValidationException("El documento debe tener exactamente 10 dígitos, sin espacios ni símbolos.");
        }
        return value;
    }

    /** Optional mobile number: when present it has exactly ten digits. */
    public static String optionalMobilePhone(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!PHONE.matcher(value).matches()) {
            throw new ValidationException("El campo " + field
                    + " debe tener exactamente 10 dígitos, sin espacios ni símbolos.");
        }
        return value;
    }

    /** Validates the email shape before services check whether it is already registered. */
    public static String requireEmail(String value) {
        String text = requireText(value, "correo", 120).toLowerCase(java.util.Locale.ROOT);
        if (!EMAIL.matcher(text).matches()) {
            throw new ValidationException("El correo electrónico no tiene un formato válido.");
        }
        return text;
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

    /** Requires a point with a valid latitude and longitude. */
    public static GeoPoint requireLocation(GeoPoint point) {
        requireNonNull(point, "ubicación");
        requireInRange(point.latitude(), -90, 90, "latitud");
        requireInRange(point.longitude(), -180, 180, "longitud");
        return point;
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

