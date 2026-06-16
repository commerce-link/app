package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierSecretCodecTest {

    private final SupplierSecretCodec codec = new SupplierSecretCodec();

    @Test
    void singleFieldMapEncodesToRawValue() {
        String secret = codec.toSecretString(Map.of("url", "https://feed.example/x.csv"));
        assertEquals("https://feed.example/x.csv", secret);
    }

    @Test
    void emptyMapEncodesToEmptyString() {
        assertEquals("", codec.toSecretString(Map.of()));
    }

    @Test
    void multiFieldMapEncodesToJsonWithFieldKeys() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("host", "ftp.example");
        config.put("port", "21");
        config.put("username", "u");
        config.put("password", "p");

        String secret = codec.toSecretString(config);

        assertTrue(secret.contains("\"host\":\"ftp.example\""));
        assertTrue(secret.contains("\"port\":\"21\""));
        assertTrue(secret.contains("\"username\":\"u\""));
        assertTrue(secret.contains("\"password\":\"p\""));
    }
}
