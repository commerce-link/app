package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every message key the RMA address page uses exists in both languages, and no rma.address key is left over. */
class RmaAddressMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|'(order\\.address\\.[a-zA-Z0-9_.]+)'|\"((?:rma\\.address|order\\.address|billing)\\.[a-zA-Z0-9_.]+)\"");
    private static final Path[] FILES = {
            Path.of("src/main/resources/templates/rma/address.html"),
            Path.of("src/main/java/pl/commercelink/web/rma/RmaAddressForm.java"),
            Path.of("src/main/java/pl/commercelink/web/rma/RmaAddressPage.java"),
            Path.of("src/main/java/pl/commercelink/orders/rma/RMAController.java")};

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        for (Path file : FILES) {
            Matcher m = KEY.matcher(Files.readString(file));
            while (m.find()) {
                keys.add(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3));
            }
        }
        keys.removeIf(key -> key.endsWith("."));
        return keys;
    }

    @Test
    void everyUsedKeyExistsInBothLanguages() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // when
        Set<String> used = keysUsed();

        // then
        assertThat(used).contains("rma.address.page.title", "order.address.name", "rma.address.saved", "billing.phone.required");
        assertThat(used).allSatisfy(key -> {
            assertThat(pl).containsKey(key);
            assertThat(en).containsKey(key);
        });
    }

    @Test
    void noAddressPageKeyIsLeftOver() throws Exception {
        // given
        Set<String> used = keysUsed();

        // when
        Set<String> defined = new TreeSet<>();
        load("messages_pl.properties").stringPropertyNames().stream().filter(k -> k.startsWith("rma.address.")).forEach(defined::add);

        // then
        assertThat(used).containsAll(defined);
    }
}
