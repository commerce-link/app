package pl.commercelink.web.offers;

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

/** Every message key the offer details templates use exists in both languages, and both define the same page keys. */
class OfferDetailsMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|[\"']((?:offers\\.details)\\.[a-zA-Z0-9_.]+)[\"']");
    private static final List<Path> TEMPLATES = List.of(
            Path.of("src/main/resources/templates/offers/details.html"),
            Path.of("src/main/resources/templates/offers/details/items.html"),
            Path.of("src/main/resources/templates/offers/details/side.html"),
            Path.of("src/main/resources/templates/offers/item-edit.html"));

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        for (Path file : TEMPLATES) {
            Matcher m = KEY.matcher(Files.readString(file));
            while (m.find()) {
                keys.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }
        keys.removeIf(k -> k.endsWith("."));
        return keys;
    }

    private static Set<String> pageKeys(Properties properties) {
        Set<String> keys = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("offers.details.")) {
                keys.add(key);
            }
        }
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
        assertThat(used).anyMatch(k -> k.startsWith("offers.details."));
        assertThat(used.stream().filter(k -> !pl.containsKey(k)).toList()).isEmpty();
        assertThat(used.stream().filter(k -> !en.containsKey(k)).toList()).isEmpty();
    }

    @Test
    void pageKeysAreTheSameInBothLanguages() throws Exception {
        // given
        Set<String> pl = pageKeys(load("messages_pl.properties"));
        Set<String> en = pageKeys(load("messages_en.properties"));

        // then
        assertThat(pl).isNotEmpty().isEqualTo(en);
    }
}
