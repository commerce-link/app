package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The delivery details keys exist in both languages, never use plural forms and survive MessageFormat. */
class DeliveryDetailsMessagesTest {

    private static final List<String> PREFIXES = List.of("deliveries.details.", "deliveries.history.event.",
            "deliveries.receive.error.");
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");
    private static final Pattern KEY = Pattern.compile(
            "#\\{([a-zA-Z0-9_.]+)[(}]|[\"']((?:deliveries\\.details|deliveries\\.history\\.event|deliveries\\.receive\\.error)\\.[a-zA-Z0-9_.]+)[\"']");

    static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        properties.load(Files.newBufferedReader(Path.of("src/main/resources/" + file), StandardCharsets.UTF_8));
        return properties;
    }

    private static Set<String> keys(Properties properties) {
        Set<String> keys = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (PREFIXES.stream().anyMatch(key::startsWith)) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Test
    void everyDetailsKeyExistsInBothLanguages() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // when
        Set<String> plKeys = keys(pl);
        Set<String> enKeys = keys(en);

        // then
        assertThat(plKeys).hasSizeGreaterThan(250).isEqualTo(enKeys);
        for (String key : plKeys) {
            assertThat(pl.getProperty(key)).as(key + " pl").isNotBlank();
            assertThat(en.getProperty(key)).as(key + " en").isNotBlank();
        }
    }

    @Test
    void historyCoversEveryEventTheDeliveryRecords() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");

        // when
        String dropship = pl.getProperty("deliveries.history.event.DELIVERY_RECEIVED.dropship");

        // then
        for (String event : List.of("DELIVERY_CREATED", "DELIVERY_ORDERED_AUTOMATICALLY", "DELIVERY_ORDERED_MANUALLY",
                "DELIVERY_PURCHASE_APPROVED", "DELIVERY_PURCHASE_RETRIED", "DELIVERY_ORDER_RECONCILED",
                "DELIVERY_ORDER_ID_CONFIRMED", "DELIVERY_ORDER_ID_UNCONFIRMED", "DELIVERY_UPDATED", "DELIVERY_DELAYED",
                "DELIVERY_ITEM_QTY_UPDATED", "DELIVERY_RECEIVED", "DELIVERY_RECEIVED.dropship", "other")) {
            assertThat(pl.getProperty("deliveries.history.event." + event)).as(event).isNotBlank();
        }
        assertThat(dropship).isEqualTo("Wysłana do klienta");
    }

    @Test
    void noPluralFormsAndNoLoneApostropheNextToAnArgument() throws Exception {
        for (String file : List.of("messages_pl.properties", "messages_en.properties")) {
            // given
            Properties messages = load(file);

            // when
            Set<String> detailsKeys = keys(messages);

            // then
            for (String key : detailsKeys) {
                String value = messages.getProperty(key);
                assertThat(key).as(file).doesNotEndWith(".one").doesNotEndWith(".few").doesNotEndWith(".many");
                if (value.contains("{0}")) {
                    assertThat(LONE_APOSTROPHE.matcher(value).find()).as(file + ": " + key).isFalse();
                }
            }
        }
    }

    @Test
    void newKeysAreWrittenWithUnicodeEscapesLikeTheRestOfTheDeliveriesGroup() throws Exception {
        // given
        List<String> lines = Files.readAllLines(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);

        // when
        List<String> newLines = lines.stream().filter(line -> PREFIXES.stream().anyMatch(line::startsWith)).toList();

        // then
        assertThat(newLines).isNotEmpty();
        for (String line : newLines) {
            assertThat(line.chars().allMatch(c -> c < 128)).as(line).isTrue();
        }
    }

    @Test
    void everyKeyTheDetailsPageUsesExistsInBothLanguages() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");
        Set<String> used = new TreeSet<>();

        // when
        try (Stream<Path> files = Stream.concat(
                Files.walk(Path.of("src/main/resources/templates/deliveries/details")),
                Stream.of(Path.of("src/main/resources/templates/deliveries/details.html"),
                        Path.of("src/main/java/pl/commercelink/web/deliveries/details"),
                        Path.of("src/main/java/pl/commercelink/inventory/deliveries/DeliveryReceptionService.java")))) {
            for (Path file : files.flatMap(DeliveryDetailsMessagesTest::regularFiles).distinct().toList()) {
                Matcher m = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    used.add(m.group(1) != null ? m.group(1) : m.group(2));
                }
            }
        }
        // a prefix completed at runtime ("deliveries.history.event." + type) is not a key
        used.removeIf(key -> key.endsWith("."));

        // then
        assertThat(used).hasSizeGreaterThan(150);
        assertThat(used.stream().filter(key -> !pl.containsKey(key)).toList()).as("missing in PL").isEmpty();
        assertThat(used.stream().filter(key -> !en.containsKey(key)).toList()).as("missing in EN").isEmpty();
    }

    private static Stream<Path> regularFiles(Path path) {
        try {
            return Files.isDirectory(path) ? Files.walk(path).filter(Files::isRegularFile).toList().stream() : Stream.of(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
