package co.edu.unicesar.rutavida.osm;

import java.util.List;
import java.util.Map;

/**
 * What OsmParser read from the .osm file: the points (OSM nodes) by id and the
 * ways (streets) with their tags. Relations are ignored.
 */
public record OsmExtract(Map<Long, Point> points, List<Way> ways) {

    public OsmExtract {
        points = Map.copyOf(points);
        ways = List.copyOf(ways);
    }

    /** An OSM node: a point with coordinates. */
    public record Point(long id, double latitude, double longitude) {
    }

    /** An OSM way: an ordered list of node ids plus its tags (highway, name, oneway...). */
    public record Way(long id, List<Long> nodeIds, Map<String, String> tags) {

        public Way {
            nodeIds = List.copyOf(nodeIds);
            tags = Map.copyOf(tags);
        }

        /** Value of a tag, or null when the way does not have it. */
        public String tag(String key) {
            return tags.get(key);
        }
    }
}