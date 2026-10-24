package co.edu.unicesar.rutavida.osm;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * A small .osm file written by hand for the tests (it is NOT real Valledupar data).
 *
 * <ul>
 *   <li>way 100 "Carrera 20": residential, two-way, nodes 1-2-3.</li>
 *   <li>way 200 "Calle 16": primary, oneway, maxspeed 40, nodes 6-5-2-4 (node 6 is outside).</li>
 *   <li>way 300: footway 1-4 (ignored).</li>
 *   <li>way 400: no name, tertiary, oneway=-1, maxspeed 20 mph, nodes 3-7.</li>
 *   <li>way 500 "Glorieta Test": closed roundabout 8-9-10-8, secondary.</li>
 *   <li>way 600: area=yes (ignored).</li>
 * </ul>
 */
final class OsmSamples {

    /** South 10.000, west -73.000, north 10.010, east -72.990: node 6 is outside. */
    static final BoundingBox AREA = new BoundingBox(10.000, -73.000, 10.010, -72.990);

    static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <osm version="0.6" generator="test">
              <bounds minlat="10.000" minlon="-73.000" maxlat="10.010" maxlon="-72.990"/>
              <node id="1" lat="10.001" lon="-72.999"/>
              <node id="2" lat="10.002" lon="-72.999"/>
              <node id="3" lat="10.003" lon="-72.999"/>
              <node id="4" lat="10.002" lon="-72.998"/>
              <node id="5" lat="10.002" lon="-73.000"/>
              <node id="6" lat="10.002" lon="-73.001"/>
              <node id="7" lat="10.004" lon="-72.999">
                <tag k="name" v="Tienda (etiqueta de un nodo, se ignora)"/>
              </node>
              <node id="8" lat="10.006" lon="-72.995"/>
              <node id="9" lat="10.0065" lon="-72.9945"/>
              <node id="10" lat="10.006" lon="-72.994"/>
              <way id="100">
                <nd ref="1"/><nd ref="2"/><nd ref="3"/>
                <tag k="highway" v="residential"/>
                <tag k="name" v="Carrera 20"/>
              </way>
              <way id="200">
                <nd ref="6"/><nd ref="5"/><nd ref="2"/><nd ref="4"/>
                <tag k="highway" v="primary"/>
                <tag k="name" v="Calle 16"/>
                <tag k="oneway" v="yes"/>
                <tag k="maxspeed" v="40"/>
              </way>
              <way id="300">
                <nd ref="1"/><nd ref="4"/>
                <tag k="highway" v="footway"/>
              </way>
              <way id="400">
                <nd ref="3"/><nd ref="7"/>
                <tag k="highway" v="tertiary"/>
                <tag k="oneway" v="-1"/>
                <tag k="maxspeed" v="20 mph"/>
              </way>
              <way id="500">
                <nd ref="8"/><nd ref="9"/><nd ref="10"/><nd ref="8"/>
                <tag k="highway" v="secondary"/>
                <tag k="junction" v="roundabout"/>
                <tag k="name" v="Glorieta Test"/>
              </way>
              <way id="600">
                <nd ref="1"/><nd ref="3"/>
                <tag k="highway" v="residential"/>
                <tag k="area" v="yes"/>
              </way>
              <relation id="900">
                <member type="way" ref="100" role=""/>
                <tag k="type" v="route"/>
              </relation>
            </osm>
            """;

    private OsmSamples() {
    }

    static OsmExtract extract() {
        return new OsmParser().parse(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
    }

    static RoadNetwork network() {
        return new RoadNetworkBuilder(AREA).build(extract());
    }
}