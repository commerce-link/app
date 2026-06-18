package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplierSecretCodecTest {

    private final SupplierSecretCodec codec = new SupplierSecretCodec();

    @Test
    void singleFieldMapEncodesToRawValue() {
        // when
        String secret = codec.toSecretString(Map.of("url", "https://feed.example/x.csv"));

        // then
        assertEquals("https://feed.example/x.csv", secret);
    }

    @Test
    void emptyMapEncodesToEmptyString() {
        // when / then
        assertEquals("", codec.toSecretString(Map.of()));
    }

    @Test
    void multiFieldMapEncodesToJsonWithFieldKeys() {
        // given
        Map<String, String> config = new LinkedHashMap<>();
        config.put("host", "ftp.example");
        config.put("port", "21");
        config.put("username", "u");
        config.put("password", "p");

        // when
        String secret = codec.toSecretString(config);

        // then
        assertEquals("{\"host\":\"ftp.example\",\"password\":\"p\",\"port\":\"21\",\"username\":\"u\"}", secret);
    }

    @Test
    void multiFieldOutputIsDeterministicRegardlessOfInsertionOrder() {
        // given
        Map<String, String> ascending = new LinkedHashMap<>();
        ascending.put("host", "ftp.example");
        ascending.put("password", "p");
        ascending.put("port", "21");
        ascending.put("username", "u");
        Map<String, String> descending = new LinkedHashMap<>();
        descending.put("username", "u");
        descending.put("port", "21");
        descending.put("password", "p");
        descending.put("host", "ftp.example");

        // when
        String ascendingSecret = codec.toSecretString(ascending);
        String descendingSecret = codec.toSecretString(descending);

        // then
        assertEquals(ascendingSecret, descendingSecret);
    }

    @Test
    void nullMapEncodesToEmptyString() {
        // when / then
        assertEquals("", codec.toSecretString(null));
    }
}
