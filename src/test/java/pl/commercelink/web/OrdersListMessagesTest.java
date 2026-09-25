package pl.commercelink.web;

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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Every message key the orders list templates and Java use exists in both languages, and none is left over. */
class OrdersListMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|\"(orders\\.list\\.[a-zA-Z0-9_.]+|orders\\.filters\\.[a-zA-Z0-9_.]+|order\\.source\\.type\\.[A-Za-z]+|general\\.pagination\\.[a-z]+)\"");

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Stream.concat(
                Files.walk(Path.of("src/main/resources/templates/orders")),
                Stream.of(Path.of("src/main/resources/templates/fragments/pagination.html"),
                        Path.of("src/main/java/pl/commercelink/orders/OrderListService.java"),
                        Path.of("src/main/java/pl/commercelink/web/orders/OrderRowMapper.java")))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    keys.add(m.group(1) != null ? m.group(1) : m.group(2));
                }
            }
        }
        keys.removeIf(k -> k.startsWith("OrderStatus.") || k.startsWith("ShipmentType.") || k.startsWith("PaymentSource.") || k.startsWith("ShippingDue."));
        keys.removeIf(k -> k.endsWith("."));
        return keys;
    }

    @Test
    void everyKeyExistsInPolishAndEnglish() throws Exception {
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");
        Set<String> used = keysUsed();
        assertThat(used).isNotEmpty();
        assertThat(used.stream().filter(k -> !pl.containsKey(k)).toList()).as("missing in PL").isEmpty();
        assertThat(used.stream().filter(k -> !en.containsKey(k)).toList()).as("missing in EN").isEmpty();
    }

    @Test
    void enumSourceTypesAllHaveLabels() throws Exception {
        for (String file : new String[] {"messages_pl.properties", "messages_en.properties"}) {
            Properties messages = load(file);
            for (pl.commercelink.orders.OrderSourceType type : pl.commercelink.orders.OrderSourceType.values()) {
                assertThat(messages.containsKey("order.source.type." + type.name())).as(file + ": " + type.name()).isTrue();
            }
        }
    }

    @Test
    void oldListKeysAreGone() throws Exception {
        for (String file : new String[] {"messages_pl.properties", "messages_en.properties"}) {
            Properties messages = load(file);
            assertThat(messages.stringPropertyNames()).as(file)
                    .noneMatch(k -> k.startsWith("orders.filters.view.") || k.startsWith("orders.filters.mode."))
                    .doesNotContain("orders.status.filter.clear", "orders.filters.manage", "orders.filters.back",
                            "orders.status.filter.title", "orders.status.filter.apply");
        }
    }
}
