package co.edu.unicesar.rutavida.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebServerTest {

    @Test
    void renderPortIsUsedWhenThereIsNoLocalArgument() {
        assertEquals(10000, WebServer.port(new String[0], "10000"));
        assertEquals(8080, WebServer.port(new String[0], null));
    }

    @Test
    void aLocalArgumentHasPriorityOverTheEnvironment() {
        assertEquals(8081, WebServer.port(new String[]{"8081"}, "10000"));
    }

    @Test
    void invalidPortFailsBeforeStartingTheServer() {
        assertThrows(IllegalArgumentException.class, () -> WebServer.port(new String[0], "not-a-port"));
        assertThrows(IllegalArgumentException.class, () -> WebServer.port(new String[]{"0"}, null));
    }
}
