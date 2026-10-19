package pl.commercelink.web.offers;

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

/** Every message key the basket view template and Java use exists in both languages. */
class BasketViewMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|[\"']((?:offers\\.basket)\\.[a-zA-Z0-9_.]+)[\"']");

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
                Files.list(Path.of("src/main/java/pl/commercelink/web/offers"))
                        .filter(f -> f.getFileName().toString().startsWith("BasketView")),
                Stream.of(Path.of("src/main/resources/templates/offers/basket.html")))) {
            for (Path file : files.toList()) {
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
    void basketKeysAreTheSameInBothLanguages() throws Exception {
        // given
        Set<String> pl = basketKeys(load("messages_pl.properties"));
        Set<String> en = basketKeys(load("messages_en.properties"));

        // then
        assertThat(pl).isNotEmpty().isEqualTo(en);
    }

    private static Set<String> basketKeys(Properties properties) {
        Set<String> keys = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("offers.basket.")) {
                keys.add(key);
            }
        }
        return keys;
    }
}
