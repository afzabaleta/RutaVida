package co.edu.unicesar.rutavida.osm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OsmParserTest {

    @Test
    void readsPointsWaysAndTheirTags() {
        OsmExtract extract = OsmSamples.extract();

        assertEquals(10, extract.points().size());
        assertEquals(6, extract.ways().size());
        assertEquals(10.0065, extract.points().get(9L).latitude(), 1e-9);

        OsmExtract.Way calle16 = extract.ways().get(1);
        assertEquals(200, calle16.id());
        assertEquals(List.of(6L, 5L, 2L, 4L), calle16.nodeIds());
        assertEquals("Calle 16", calle16.tag("name"));
        assertEquals("yes", calle16.tag("oneway"));
        assertNull(calle16.tag("junction"));
    }

    @Test
    void refusesFilesWithADoctypeSoNoOtherFileCanBeRead() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE osm [<!ENTITY secret SYSTEM "file:///C:/Windows/win.ini">]>
                <osm><way id="1"><tag k="name" v="&secret;"/></way></osm>
                """;

        assertThrows(IllegalArgumentException.class, () -> parse(xxe));
    }

    @Test
    void aFileThatIsNotOsmXmlGivesAClearError() {
        assertThrows(IllegalArgumentException.class, () -> parse("esto no es XML"));
        assertThrows(IllegalArgumentException.class, () -> parse("<osm><node id=\"x\" lat=\"1\" lon=\"2\"/></osm>"));
    }

    private static OsmExtract parse(String xml) {
        return new OsmParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}