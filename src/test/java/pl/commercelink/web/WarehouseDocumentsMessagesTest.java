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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The warehouse documents pages and their guide text have every message in both languages. */
class WarehouseDocumentsMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|[\"'](warehouse\\.documents\\.[a-zA-Z0-9_.]+)[\"']");

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static boolean ownKey(String key) {
        return key.startsWith("warehouse.documents.") || key.startsWith("intro.warehouse-documents.");
    }

    private static Set<String> ownKeys(Properties messages) {
        return messages.stringPropertyNames().stream().filter(WarehouseDocumentsMessagesTest::ownKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Stream.concat(
                Files.walk(Path.of("src/main/java/pl/commercelink/web/warehousedocuments")),
                Stream.of(Path.of("src/main/resources/templates/warehouse-documents.html"),
                        Path.of("src/main/resources/templates/warehouse-document-details.html"),
                        Path.of("src/main/resources/templates/warehouse-document-mfn-history.html"),
                        Path.of("src/main/resources/templates/fragments/warehouse-documents-list.html")))) {
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
    void everyWarehouseDocumentsKeyExistsInBothLanguagesWithText() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // when
        Set<String> plKeys = ownKeys(pl);
        Set<String> enKeys = ownKeys(en);

        // then
        assertThat(plKeys).isNotEmpty();
        assertThat(plKeys).as("PL and EN define the same keys").isEqualTo(enKeys);
        assertThat(plKeys.stream().filter(k -> pl.getProperty(k).isBlank()).toList()).as("blank in PL").isEmpty();
        assertThat(enKeys.stream().filter(k -> en.getProperty(k).isBlank()).toList()).as("blank in EN").isEmpty();
    }

    @Test
    void everyKeyTheWarehouseDocumentsPagesUseExistsInBothLanguages() throws Exception {
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

}
