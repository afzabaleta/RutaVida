package co.edu.unicesar.rutavida.traffic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EdgeUpdateTest {

    @Test
    void validUpdate() {
        EdgeUpdate update = new EdgeUpdate(3, 2.0, 15);
        assertEquals(3, update.edgeIndex());
        assertEquals(2.0, update.congestion(), 0.001);
    }

    @Test
    void rejectsCongestionBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeUpdate(0, 0.5, 0));
    }

    @Test
    void rejectsNegativeIndex() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeUpdate(-1, 1.0, 0));
    }
}
