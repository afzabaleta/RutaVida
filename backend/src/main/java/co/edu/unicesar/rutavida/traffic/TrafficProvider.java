package co.edu.unicesar.rutavida.traffic;

import co.edu.unicesar.rutavida.model.enums.TrafficSource;

import java.util.List;

/** Source of traffic changes: simulated in phase 1, external in phase 2. Never returns null. */
public interface TrafficProvider {

    /** Traffic changes since the last call. */
    List<EdgeUpdate> poll();

    /** Where the data comes from (used to fill TRAFICO.fuente). */
    TrafficSource source();
}