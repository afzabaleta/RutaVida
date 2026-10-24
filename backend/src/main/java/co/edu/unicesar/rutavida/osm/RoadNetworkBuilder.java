package co.edu.unicesar.rutavida.osm;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.NodeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the OSM streets into the road graph of RutaVida.
 *
 * <ol>
 *   <li>Keeps only streets a vehicle can use (see {@link #ROAD_TYPES}) and only the
 *       part that is inside the bounding box.</li>
 *   <li>Cuts each street at every point shared with another street (a crossing) and
 *       at its ends. Each piece becomes one segment; the points in between only add
 *       distance.</li>
 *   <li>Creates one link per direction: two-way streets give two TRAMO_VIAL rows,
 *       one-way streets give one.</li>
 * </ol>
 *
 * <p>Assumptions (they are not OSM data and are written in the script header):
 * streets without {@code maxspeed} get 50 km/h if they are main roads and 30 km/h
 * otherwise (Ley 2251 de 2022: at most 50 km/h in urban roads and 30 km/h in
 * residential zones). Streets without a name get {@link #UNNAMED}.
 */
public final class RoadNetworkBuilder {

    static final String UNNAMED = "Vía sin nombre (OSM)";
    static final double MAIN_ROAD_DEFAULT_KMH = 50;
    static final double LOCAL_ROAD_DEFAULT_KMH = 30;

    /** Same limits as the CHECK constraints of TRAMO_VIAL. */
    private static final double MAX_SPEED_KMH = 120;
    private static final double MIN_LENGTH_METERS = 0.01;
    private static final int MAX_TEXT = 120;

    static final Set<String> MAIN_ROADS = Set.of(
            "motorway", "motorway_link", "trunk", "trunk_link",
            "primary", "primary_link", "secondary", "secondary_link",
            "tertiary", "tertiary_link");

    static final Set<String> ROAD_TYPES = Set.of(
            "motorway", "motorway_link", "trunk", "trunk_link",
            "primary", "primary_link", "secondary", "secondary_link",
            "tertiary", "tertiary_link",
            "unclassified", "residential", "living_street");

    private enum Direction { FORWARD, BACKWARD, BOTH }

    private record Run(OsmExtract.Way way, List<Long> nodeIds) {
    }

    private record Piece(OsmExtract.Way way, long fromId, long toId, double lengthMeters) {
    }

    private final BoundingBox area;

    public RoadNetworkBuilder(BoundingBox area) {
        this.area = Objects.requireNonNull(area, "area");
    }

    public RoadNetwork build(OsmExtract extract) {
        Map<Long, OsmExtract.Point> points = extract.points();

        // 1. Streets for vehicles, cut to the parts inside the area.
        List<Run> runs = new ArrayList<>();
        int waysUsed = 0;
        int unnamedWays = 0;
        int defaultSpeedWays = 0;
        for (OsmExtract.Way way : extract.ways()) {
            if (!isRoad(way)) {
                continue;
            }
            List<List<Long>> inside = partsInside(way, points);
            if (inside.isEmpty()) {
                continue;
            }
            waysUsed++;
            if (nameOf(way) == null) {
                unnamedWays++;
            }
            if (parseSpeed(way.tag("maxspeed")) == null) {
                defaultSpeedWays++;
            }
            for (List<Long> part : inside) {
                runs.add(new Run(way, part));
            }
        }

        // 2. How many times each point is used: 2 or more means a crossing.
        Map<Long, Integer> uses = new HashMap<>();
        for (Run run : runs) {
            for (long id : run.nodeIds()) {
                uses.merge(id, 1, Integer::sum);
            }
        }

        // 3. Cut every run at its crossings.
        List<Piece> pieces = new ArrayList<>();
        int[] skipped = {0};
        for (Run run : runs) {
            List<Long> ids = run.nodeIds();
            int start = 0;
            for (int i = 1; i < ids.size(); i++) {
                boolean last = i == ids.size() - 1;
                if (last || uses.get(ids.get(i)) > 1) {
                    addPiece(run.way(), ids.subList(start, i + 1), points, pieces, skipped);
                    start = i;
                }
            }
        }

        // 4. One link per direction.
        List<RoadNetwork.Link> links = new ArrayList<>();
        Map<Long, List<OsmExtract.Way>> waysAtJunction = new LinkedHashMap<>();
        for (Piece piece : pieces) {
            OsmExtract.Way way = piece.way();
            String name = nameOf(way) == null ? UNNAMED : nameOf(way);
            double speed = speedOf(way);
            Direction direction = directionOf(way);
            if (direction != Direction.BACKWARD) {
                links.add(new RoadNetwork.Link(piece.fromId(), piece.toId(), way.id(),
                        name, piece.lengthMeters(), speed));
            }
            if (direction != Direction.FORWARD) {
                links.add(new RoadNetwork.Link(piece.toId(), piece.fromId(), way.id(),
                        name, piece.lengthMeters(), speed));
            }
            addWay(waysAtJunction, piece.fromId(), way);
            addWay(waysAtJunction, piece.toId(), way);
        }
        links.sort(Comparator.comparingLong(RoadNetwork.Link::osmWayId)
                .thenComparingLong(RoadNetwork.Link::fromOsmId)
                .thenComparingLong(RoadNetwork.Link::toOsmId));

        // 5. The junctions that the links use.
        List<RoadNetwork.Junction> junctions = new ArrayList<>();
        for (Map.Entry<Long, List<OsmExtract.Way>> entry : waysAtJunction.entrySet()) {
            OsmExtract.Point point = points.get(entry.getKey());
            junctions.add(new RoadNetwork.Junction(point.id(), point.latitude(), point.longitude(),
                    typeOf(entry.getValue()), referenceOf(entry.getValue())));
        }
        junctions.sort(Comparator.comparingLong(RoadNetwork.Junction::osmId));

        RoadNetwork.Summary summary = new RoadNetwork.Summary(extract.ways().size(), waysUsed,
                unnamedWays, defaultSpeedWays, skipped[0]);
        return new RoadNetwork(junctions, links, summary);
    }

    // ------------------------------------------------------------------ steps

    static boolean isRoad(OsmExtract.Way way) {
        return ROAD_TYPES.contains(way.tag("highway")) && !"yes".equals(way.tag("area"));
    }

    /** Consecutive points of the way that exist in the file and are inside the area. */
    private List<List<Long>> partsInside(OsmExtract.Way way, Map<Long, OsmExtract.Point> points) {
        List<List<Long>> parts = new ArrayList<>();
        List<Long> current = new ArrayList<>();
        for (long id : way.nodeIds()) {
            OsmExtract.Point point = points.get(id);
            if (point != null && area.contains(point.latitude(), point.longitude())) {
                current.add(id);
            } else {
                if (current.size() >= 2) {
                    parts.add(current);
                }
                current = new ArrayList<>();
            }
        }
        if (current.size() >= 2) {
            parts.add(current);
        }
        return parts;
    }

    private static void addPiece(OsmExtract.Way way, List<Long> ids, Map<Long, OsmExtract.Point> points,
                                 List<Piece> pieces, int[] skipped) {
        long first = ids.get(0);
        long last = ids.get(ids.size() - 1);
        if (first == last) {
            // A closed street (a roundabout) with no crossing: TRAMO_VIAL does not accept
            // a segment that starts and ends at the same node, so it is cut in the middle.
            if (ids.size() >= 3) {
                int middle = ids.size() / 2;
                addPiece(way, ids.subList(0, middle + 1), points, pieces, skipped);
                addPiece(way, ids.subList(middle, ids.size()), points, pieces, skipped);
            } else {
                skipped[0]++;
            }
            return;
        }
        double length = 0;
        for (int i = 1; i < ids.size(); i++) {
            length += pointOf(points, ids.get(i - 1)).distanceMetersTo(pointOf(points, ids.get(i)));
        }
        if (length < MIN_LENGTH_METERS) {
            // Two OSM points in the same place: Oracle requires distancia_m > 0.
            skipped[0]++;
            return;
        }
        pieces.add(new Piece(way, first, last, length));
    }

    private static GeoPoint pointOf(Map<Long, OsmExtract.Point> points, long id) {
        OsmExtract.Point point = points.get(id);
        return new GeoPoint(point.latitude(), point.longitude());
    }

    private static void addWay(Map<Long, List<OsmExtract.Way>> waysAtJunction, long id, OsmExtract.Way way) {
        List<OsmExtract.Way> ways = waysAtJunction.computeIfAbsent(id, key -> new ArrayList<>());
        if (ways.stream().noneMatch(w -> w.id() == way.id())) {
            ways.add(way);
        }
    }

    private static NodeType typeOf(List<OsmExtract.Way> ways) {
        if (ways.stream().anyMatch(RoadNetworkBuilder::isRoundabout)) {
            return NodeType.ROUNDABOUT;
        }
        return ways.size() >= 2 ? NodeType.INTERSECTION : NodeType.WAY_POINT;
    }

    /** "Calle 16 x Carrera 20": the names of up to two streets that meet there. */
    private static String referenceOf(List<OsmExtract.Way> ways) {
        Set<String> names = new TreeSet<>();
        for (OsmExtract.Way way : ways) {
            String name = nameOf(way);
            if (name != null) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        String reference = String.join(" x ", names.stream().limit(2).toList());
        return reference.length() > MAX_TEXT ? reference.substring(0, MAX_TEXT) : reference;
    }

    static String nameOf(OsmExtract.Way way) {
        String name = way.tag("name");
        if (name == null || name.isBlank()) {
            name = way.tag("ref");
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        name = name.strip().replaceAll("\\s+", " ");
        return name.length() > MAX_TEXT ? name.substring(0, MAX_TEXT) : name;
    }

    private static double speedOf(OsmExtract.Way way) {
        Double tagged = parseSpeed(way.tag("maxspeed"));
        if (tagged != null) {
            return tagged;
        }
        return MAIN_ROADS.contains(way.tag("highway")) ? MAIN_ROAD_DEFAULT_KMH : LOCAL_ROAD_DEFAULT_KMH;
    }

    /** "40" -> 40; "40 km/h" -> 40; "20 mph" -> 32.19; anything else -> null (default is used). */
    static Double parseSpeed(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip().toLowerCase(Locale.ROOT);
        boolean mph = value.endsWith("mph");
        String number = value.replace("km/h", "").replace("kmh", "").replace("mph", "").strip();
        try {
            double kmh = Double.parseDouble(number) * (mph ? 1.609344 : 1);
            if (kmh <= 0 || Double.isNaN(kmh)) {
                return null;
            }
            return Math.min(kmh, MAX_SPEED_KMH);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isRoundabout(OsmExtract.Way way) {
        String junction = way.tag("junction");
        return "roundabout".equals(junction) || "circular".equals(junction);
    }

    private static Direction directionOf(OsmExtract.Way way) {
        String oneway = way.tag("oneway");
        if ("-1".equals(oneway)) {
            return Direction.BACKWARD;
        }
        if ("yes".equals(oneway) || "true".equals(oneway) || "1".equals(oneway)) {
            return Direction.FORWARD;
        }
        if (isRoundabout(way) && !"no".equals(oneway)) {
            // In OSM a roundabout is one-way unless it says oneway=no.
            return Direction.FORWARD;
        }
        return Direction.BOTH;
    }
}
