package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.DeliveryListState;
import pl.commercelink.web.deliveries.DeliveryAttention;

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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Every message key the deliveries list template and Java use exists in both languages. */
class DeliveriesListMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|[\"'](deliveries\\.list\\.[a-zA-Z0-9_.]+)[\"']");

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
                Files.walk(Path.of("src/main/java/pl/commercelink/web/deliveries")),
                Stream.of(Path.of("src/main/resources/templates/deliveries.html"),
                        Path.of("src/main/resources/templates/fragments/pagination.html"),
                        Path.of("src/main/java/pl/commercelink/inventory/deliveries/DeliveryListState.java")))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    keys.add(m.group(1) != null ? m.group(1) : m.group(2));
                }
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
        assertThat(used).isNotEmpty();
        assertThat(used.stream().filter(k -> !pl.containsKey(k)).toList()).as("missing in PL").isEmpty();
        assertThat(used.stream().filter(k -> !en.containsKey(k)).toList()).as("missing in EN").isEmpty();
    }

    @Test
    void everyStateHasALabel() throws Exception {
        for (String file : new String[] {"messages_pl.properties", "messages_en.properties"}) {
            Properties messages = load(file);
            for (DeliveryListState state : DeliveryListState.values()) {
                assertThat(messages.containsKey(state.messageKey())).as(file + ": " + state.messageKey()).isTrue();
            }
        }
    }

    /** The tile keys are built from DeliveryAttention.param(), so the scan above cannot see them. */
    @Test
    void everyTileHasALabelAndAHint() throws Exception {
        for (String file : new String[] {"messages_pl.properties", "messages_en.properties"}) {
            Properties messages = load(file);
            for (DeliveryAttention kind : DeliveryAttention.values()) {
                String key = "deliveries.list.attention." + kind.param();
                assertThat(messages.containsKey(key) && messages.containsKey(key + ".hint")).as(file + ": " + key).isTrue();
            }
        }
    }

    @Test
    void everyEmptyStateHasItsText() throws Exception {
        for (String file : new String[] {"messages_pl.properties", "messages_en.properties"}) {
            Properties messages = load(file);
            for (String key : List.of("transit", "history", "searchTransit", "filtered")) {
                assertThat(messages.containsKey("deliveries.list.empty." + key)).as(file + ": " + key).isTrue();
            }
        }
    }

}
