package co.edu.unicesar.rutavida.model.enums;

import java.util.Objects;

/**
 * An enum whose constants are stored in Oracle as a Spanish catalog code.
 *
 * <p>The Java constant is in English (code convention) and {@link #code()} returns
 * the value used in the database CHECK constraint, e.g. {@code EmergencyStatus.EN_ROUTE}
 * is stored as {@code "EN_RUTA"}.
 */
public interface CodedEnum {

    /** Code stored in the database. */
    String code();

    /**
     * Finds the constant of {@code type} whose code is {@code code}.
     *
     * @throws IllegalArgumentException if no constant has that code
     */
    static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> type, String code) {
        Objects.requireNonNull(type, "type");
        for (E constant : type.getEnumConstants()) {
            if (constant.code().equals(code)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown code '" + code + "' for " + type.getSimpleName());
    }
}
