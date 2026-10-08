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

/** Every message key the offers list template and Java use exists in both languages. */
class OffersListMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|[\"']((?:offers|general)\\.[a-zA-Z0-9_.]+)[\"']");

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
                Files.walk(Path.of("src/main/java/pl/commercelink/web/offers")),
                Stream.of(Path.of("src/main/resources/templates/offers.html"),
                        Path.of("src/main/resources/templates/fragments/offers-list.html")))) {
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
        assertThat(used.stream().filter(k -> !pl.containsKey(k)).toList()).isEmpty();
        assertThat(used.stream().filter(k -> !en.containsKey(k)).toList()).isEmpty();
    }

    @Test
    void keysBuiltFromEnumParamsExist() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // then — keys composed in Java ("offers.list.pill." + param) are not visible to the regex above
        for (String param : new String[]{"active", "expiring", "expired", "noExpiry"}) {
            for (String prefix : new String[]{"offers.list.pill.", "offers.list.validity."}) {
                assertThat(pl).containsKey(prefix + param);
                assertThat(en).containsKey(prefix + param);
            }
        }
        for (String segment : new String[]{"offers", "templates", "baskets"}) {
            for (String prefix : new String[]{"offers.list.segment.", "offers.list.results.", "offers.list.empty.",
                    "offers.list.empty.search.", "offers.list.empty.filtered."}) {
                assertThat(pl).containsKey(prefix + segment);
                assertThat(en).containsKey(prefix + segment);
            }
        }
        for (String segment : new String[]{"templates", "baskets"}) {
            assertThat(pl).containsKey("offers.list.desc." + segment);
            assertThat(en).containsKey("offers.list.desc." + segment);
        }
    }
}
