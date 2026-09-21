package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMessagesTest {

    /** A lone apostrophe in a MessageFormat pattern swallows the placeholder that follows it; a doubled one prints. */
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");

    private static Map<String, String> catalogMessages(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("catalog."))
                .collect(Collectors.toMap(key -> key, properties::getProperty));
    }

    @Test
    void polishAndEnglishDefineTheSameCatalogKeys() throws Exception {
        // when / then
        assertThat(catalogMessages("messages_pl.properties").keySet())
                .isEqualTo(catalogMessages("messages_en.properties").keySet());
    }

    @Test
    void noCatalogMessageWithAPlaceholderHidesItBehindAnApostrophe() throws Exception {
        // given
        Set<String> broken = java.util.stream.Stream.of("messages_pl.properties", "messages_en.properties")
                .flatMap(file -> {
                    try {
                        return catalogMessages(file).entrySet().stream();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .filter(entry -> entry.getValue().contains("{") && LONE_APOSTROPHE.matcher(entry.getValue()).find())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // when / then
        assertThat(broken).isEmpty();
    }
}
