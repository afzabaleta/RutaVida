package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteOptionTest {

    private static final List<GeoPoint> PATH = List.of(new GeoPoint(10.46, -73.26), new GeoPoint(10.465, -73.255));

    private static RouteOption option(int rank, double km, String explanation) {
        return new RouteOption(rank, RouteType.MAIN, PATH, List.of(501L), km, 6.0, 1, 0, explanation);
    }

    @Test
    void keepsItsValues() {
        RouteOption option = option(1, 3.0, "Ruta recomendada");
        assertEquals(1, option.rank());
        assertEquals(RouteType.MAIN, option.type());
        assertEquals(2, option.path().size());
        assertEquals(List.of(501L), option.segmentIds());
        assertEquals(3.0, option.distanceKm(), 0.0001);
    }

    @Test
    void listsAreCopiesThatCannotBeChanged() {
        List<Long> ids = new ArrayList<>(List.of(501L, 502L));
        RouteOption option = new RouteOption(2, RouteType.ALTERNATIVE, PATH, ids, 2.0, 7.0, 0, 2, "Alternativa");
        ids.add(503L);
        assertEquals(2, option.segmentIds().size());
        assertThrows(UnsupportedOperationException.class, () -> option.segmentIds().add(504L));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> option(0, 3.0, "x"));
        assertThrows(IllegalArgumentException.class, () -> option(1, -1.0, "x"));
        assertThrows(IllegalArgumentException.class, () -> option(1, 3.0, " "));
        assertThrows(NullPointerException.class,
                () -> new RouteOption(1, null, PATH, List.of(), 1.0, 1.0, 0, 0, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteOption(1, RouteType.MAIN, PATH, List.of(), 1.0, 1.0, -1, 0, "x"));
    }
}
