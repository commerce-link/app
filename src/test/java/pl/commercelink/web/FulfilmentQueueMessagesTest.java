package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every message key the fulfilment queue uses exists in both languages, and the old page's keys are gone. */
class FulfilmentQueueMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|\"((?:fulfilment\\.queue|order\\.fulfilment\\.short)\\.[a-zA-Z0-9_.]+)\"");

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        for (Path file : List.of(Path.of("src/main/resources/templates/fulfilment-queue.html"),
                Path.of("src/main/java/pl/commercelink/web/fulfilment/FulfilmentQueuePage.java"))) {
            Matcher m = KEY.matcher(Files.readString(file));
            while (m.find()) {
                keys.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }
        keys.removeIf(k -> k.endsWith("."));
        return keys;
    }

    @Test
    void everyKeyExistsInPolishAndEnglish() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // when
        Set<String> used = keysUsed();

        // then
        assertThat(used).contains("fulfilment.queue.pick.default", "fulfilment.queue.group.warehouse.title");
        assertThat(used).allSatisfy(key -> {
            assertThat(pl.getProperty(key)).as("pl " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        });
    }

    @Test
    void theOldPageKeysAreRemoved() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // then
        for (String key : List.of("orders.apply", "orders.choose.suppliers", "orders.suggest.suppliers",
                "orders.suggest.suppliers.exact", "fulfilment.queue.next.group", "fulfilment.queue.reset",
                "fulfilment.queue.only.with.profit", "fulfilment.queue.only.multi.order",
                "fulfilment.queue.only.local.suppliers", "fulfilment.queue.order.by.order", "fulfilment.queue.items.to.order")) {
            assertThat(pl.getProperty(key)).as("pl " + key).isNull();
            assertThat(en.getProperty(key)).as("en " + key).isNull();
        }
    }
}
