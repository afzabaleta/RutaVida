package co.edu.unicesar.rutavida.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Production CORS origins are explicit; a permissive wildcard would expose bearer-token APIs. */
class ApiServerCorsTest {

    @Test
    void usesOnlyTheLocalOriginsWhenTheEnvironmentIsNotConfigured() {
        assertEquals(List.of("http://localhost:8080", "http://127.0.0.1:8080"), ApiServer.corsOrigins(null));
    }

    @Test
    void normalizesAndDeduplicatesConfiguredOrigins() {
        assertEquals(List.of("https://rutavida.vercel.app", "https://preview.example.org:8443"),
                ApiServer.corsOrigins("https://RUTAVIDA.vercel.app, https://preview.example.org:8443,"
                        + "https://rutavida.vercel.app"));
    }

    @Test
    void rejectsWildcardAndPathOrigins() {
        assertThrows(IllegalArgumentException.class, () -> ApiServer.corsOrigins("*"));
        assertThrows(IllegalArgumentException.class,
                () -> ApiServer.corsOrigins("https://rutavida.vercel.app/app"));
    }
}
