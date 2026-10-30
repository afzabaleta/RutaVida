package co.edu.unicesar.rutavida.osm;

import co.edu.unicesar.rutavida.model.enums.NodeType;
import org.junit.jupiter.api.Test;
import co.edu.unicesar.rutavida.util.GeoUtil;
import java.util.Map;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkBuilderTest {

    private final RoadNetwork network = OsmSamples.network();

    @Test
    void aCrossingCutsBothStreetsAndBecomesAnIntersection() {
        RoadNetwork.Junction crossing = junction(2);

        assertEquals(NodeType.INTERSECTION, crossing.type());
        assertEquals("Calle 16 x Carrera 20", crossing.reference());
        // Carrera 20 (1-2-3) is cut at 2: no segment jumps from 1 to 3.
        assertTrue(links(100).stream().noneMatch(l -> l.fromOsmId() == 1 && l.toOsmId() == 3));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 7L, 8L, 10L),
                network.junctions().stream().map(RoadNetwork.Junction::osmId).toList());
    }

    @Test
    void aTwoWayStreetGivesTwoLinksAndAOneWayStreetGivesOne() {
        // Carrera 20: 2 pieces x 2 directions.
        assertEquals(4, links(100).size());
        assertTrue(hasLink(100, 1, 2) && hasLink(100, 2, 1));
        // Calle 16 (oneway=yes): 2 pieces x 1 direction, in the order of the way.
        assertEquals(2, links(200).size());
        assertTrue(hasLink(200, 5, 2) && hasLink(200, 2, 4));
        assertFalse(hasLink(200, 2, 5));
    }

    @Test
    void onewayMinusOneGoesAgainstTheOrderOfThePoints() {
        assertEquals(1, links(400).size());
        assertTrue(hasLink(400, 7, 3));
    }

    @Test
    void footwaysAndAreasAreNotPartOfTheNetwork() {
        assertTrue(links(300).isEmpty());
        assertTrue(links(600).isEmpty());
        assertEquals(9, network.links().size());
    }

    @Test
    void thePartOutsideTheAreaIsCut() {
        assertTrue(network.junctions().stream().noneMatch(j -> j.osmId() == 6));
        assertEquals(NodeType.WAY_POINT, junction(5).type());
        assertEquals("Calle 16", junction(5).reference());
    }

    @Test
    void speedComesFromTheTagOrFromTheDocumentedDefault() {
        assertEquals(40, links(200).get(0).maxSpeedKmh(), 1e-9);
        assertEquals(32.19, links(400).get(0).maxSpeedKmh(), 0.01);
        assertEquals(RoadNetworkBuilder.LOCAL_ROAD_DEFAULT_KMH, links(100).get(0).maxSpeedKmh(), 1e-9);
        assertEquals(RoadNetworkBuilder.MAIN_ROAD_DEFAULT_KMH, links(500).get(0).maxSpeedKmh(), 1e-9);

        assertEquals(40, RoadNetworkBuilder.parseSpeed("40 km/h"), 1e-9);
        assertEquals(120, RoadNetworkBuilder.parseSpeed("200"), 1e-9);
        assertNull(RoadNetworkBuilder.parseSpeed("CO:urban"));
        assertNull(RoadNetworkBuilder.parseSpeed("0"));
        assertNull(RoadNetworkBuilder.parseSpeed(null));
    }

    @Test
    void aStreetWithoutNameGetsTheDefaultNameButNoReference() {
        assertEquals(RoadNetworkBuilder.UNNAMED, links(400).get(0).roadName());
        assertNull(junction(7).reference());
        // Node 3 joins Carrera 20 with the unnamed street: only the real name is used.
        assertEquals(NodeType.INTERSECTION, junction(3).type());
        assertEquals("Carrera 20", junction(3).reference());
    }

    @Test
    void aClosedRoundaboutIsCutInTwoAndNeverStartsAndEndsInTheSameNode() {
        List<RoadNetwork.Link> roundabout = links(500);

        assertEquals(2, roundabout.size());
        assertTrue(hasLink(500, 8, 10) && hasLink(500, 10, 8));
        assertTrue(network.links().stream().allMatch(l -> l.fromOsmId() != l.toOsmId()));
        assertEquals(NodeType.ROUNDABOUT, junction(8).type());
        assertEquals(NodeType.ROUNDABOUT, junction(10).type());
    }

    @Test
    void lengthIsTheSumOfTheDistancesBetweenThePoints() {
        // 0.001 degrees of latitude = 111.19 m.
        assertEquals(111.19, link(100, 1, 2).lengthMeters(), 0.01);
        // 0.001 degrees of longitude at latitude 10 = 109.50 m.
        assertEquals(109.50, link(200, 5, 2).lengthMeters(), 0.01);
        // 8 -> 9 -> 10 is longer than the straight line 8 -> 10.
        double straight = 0.001 * Math.PI / 180 * 6_371_000 * Math.cos(Math.toRadians(10.006));
        assertTrue(link(500, 8, 10).lengthMeters() > straight + 40);
    }

    @Test
    void theSummaryCountsWhatWasUsed() {
        RoadNetwork.Summary summary = network.summary();

        assertEquals(6, summary.waysRead());
        assertEquals(4, summary.waysUsed());
        assertEquals(1, summary.unnamedWays());
        assertEquals(2, summary.waysWithDefaultSpeed());
        assertEquals(0, summary.skippedPieces());
    }

    @Test
    void aLoopThatJoinsTheSameTwoJunctionsTwiceKeepsOnlyTheShorterPath() {
        // Way 700 is a closed two-way loop 11-12-13-14-11 that touches way 800 only at 11.
        // It is cut in the middle (11 -> 13 and 13 -> 11), so both halves join 11 and 13.
        OsmExtract extract = new OsmExtract(Map.of(
                11L, new OsmExtract.Point(11, 10.0080, -72.9980),
                12L, new OsmExtract.Point(12, 10.0085, -72.9980),
                13L, new OsmExtract.Point(13, 10.0085, -72.9975),
                14L, new OsmExtract.Point(14, 10.0080, -72.9965),
                15L, new OsmExtract.Point(15, 10.0070, -72.9980)),
                List.of(
                        new OsmExtract.Way(700, List.of(11L, 12L, 13L, 14L, 11L),
                                Map.of("highway", "residential", "name", "Calle Anillo")),
                        new OsmExtract.Way(800, List.of(11L, 15L),
                                Map.of("highway", "residential", "name", "Carrera Salida"))));

        RoadNetwork loop = new RoadNetworkBuilder(OsmSamples.AREA).build(extract);
        List<RoadNetwork.Link> ring = loop.links().stream().filter(l -> l.osmWayId() == 700).toList();

        assertEquals(2, ring.size(), "one link per direction, not two");
        double viaTwelve = GeoUtil.metersBetween(10.0080, -72.9980, 10.0085, -72.9980)
                + GeoUtil.metersBetween(10.0085, -72.9980, 10.0085, -72.9975);
        assertTrue(ring.stream().allMatch(l -> Math.abs(l.lengthMeters() - viaTwelve) < 0.01),
                "the shorter half (through 12) is kept");
        assertEquals(2, loop.summary().skippedPieces(), "the two repeated links are counted");
    }
    // ------------------------------------------------------------------ helpers

    private RoadNetwork.Junction junction(long osmId) {
        return network.junctions().stream().filter(j -> j.osmId() == osmId).findFirst().orElseThrow();
    }

    private List<RoadNetwork.Link> links(long wayId) {
        return network.links().stream().filter(l -> l.osmWayId() == wayId).toList();
    }

    private boolean hasLink(long wayId, long from, long to) {
        return links(wayId).stream().anyMatch(l -> l.fromOsmId() == from && l.toOsmId() == to);
    }

    private RoadNetwork.Link link(long wayId, long from, long to) {
        return links(wayId).stream().filter(l -> l.fromOsmId() == from && l.toOsmId() == to)
                .findFirst().orElseThrow();
    }
}