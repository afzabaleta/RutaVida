package co.edu.unicesar.rutavida.osm;

import co.edu.unicesar.rutavida.model.enums.NodeType;

import java.util.List;

/**
 * Result of RoadNetworkBuilder, ready to be written as SQL: one Junction per future
 * NODO_VIAL row and one Link per future TRAMO_VIAL row (one per direction).
 */
public record RoadNetwork(List<Junction> junctions, List<Link> links, Summary summary) {

    public RoadNetwork {
        junctions = List.copyOf(junctions);
        links = List.copyOf(links);
    }

    /** A crossing, a roundabout point or the end of a street (NODO_VIAL). */
    public record Junction(long osmId, double latitude, double longitude,
                           NodeType type, String reference) {
    }

    /** One direction of the street between two junctions (TRAMO_VIAL). */
    public record Link(long fromOsmId, long toOsmId, long osmWayId,
                       String roadName, double lengthMeters, double maxSpeedKmh) {
    }

    /** Counters printed by OsmImporter and written in the script header. */
    public record Summary(int waysRead, int waysUsed, int unnamedWays,
                          int waysWithDefaultSpeed, int skippedPieces) {
    }
}