package co.edu.unicesar.rutavida.osm;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an OpenStreetMap XML file (the "raw data" that Overpass returns) with the
 * StAX reader of the JDK: no external library.
 *
 * <p>Security: DTDs and external entities are disabled, so a malicious file
 * cannot make the parser read other files of the computer (XXE).
 */
public final class OsmParser {

    public OsmExtract parse(InputStream input) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        Map<Long, OsmExtract.Point> points = new HashMap<>();
        List<OsmExtract.Way> ways = new ArrayList<>();

        long wayId = 0;
        List<Long> nodeIds = null;
        Map<String, String> tags = null;

        try {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "node" -> {
                            String lat = reader.getAttributeValue(null, "lat");
                            String lon = reader.getAttributeValue(null, "lon");
                            if (lat != null && lon != null) {
                                long id = Long.parseLong(reader.getAttributeValue(null, "id"));
                                points.put(id, new OsmExtract.Point(
                                        id, Double.parseDouble(lat), Double.parseDouble(lon)));
                            }
                        }
                        case "way" -> {
                            wayId = Long.parseLong(reader.getAttributeValue(null, "id"));
                            nodeIds = new ArrayList<>();
                            tags = new HashMap<>();
                        }
                        case "nd" -> {
                            if (nodeIds != null) {
                                nodeIds.add(Long.parseLong(reader.getAttributeValue(null, "ref")));
                            }
                        }
                        case "tag" -> {
                            // Tags of nodes and relations are ignored: only ways matter here.
                            if (tags != null) {
                                tags.put(reader.getAttributeValue(null, "k"),
                                        reader.getAttributeValue(null, "v"));
                            }
                        }
                        default -> {
                            // osm, bounds, relation, member, meta...: not needed.
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT
                        && "way".equals(reader.getLocalName())) {
                    ways.add(new OsmExtract.Way(wayId, nodeIds, tags));
                    nodeIds = null;
                    tags = null;
                }
            }
            reader.close();
        } catch (XMLStreamException | RuntimeException e) {
            throw new IllegalArgumentException(
                    "El archivo no es un XML de OpenStreetMap válido: " + e.getMessage(), e);
        }
        return new OsmExtract(points, ways);
    }
}
