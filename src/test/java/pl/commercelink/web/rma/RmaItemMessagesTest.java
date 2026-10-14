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

/** Every message key the RMA item page uses exists in both languages, and no rma.item.page key is left over. */
class RmaItemMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|\"(rma\\.item\\.page\\.[a-zA-Z0-9_.]+)\"");
    private static final Path[] FILES = {
            Path.of("src/main/resources/templates/rma-item.html"),
            Path.of("src/main/java/pl/commercelink/web/rma/RmaItemPageFactory.java"),
            Path.of("src/main/java/pl/commercelink/web/rma/RmaItemController.java"),
            Path.of("src/main/java/pl/commercelink/web/dtos/RmaItemForm.java")};

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
            while (m.find()) keys.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        // a trailing dot is a prefix completed at runtime, covered by the explicit keys below
        keys.removeIf(key -> key.endsWith("."));
        // built from enum names in the factory and from the field loop in the template
        for (String status : new String[]{"Completed", "Rejected"}) keys.add("rma.item.page.closed." + status);
        for (String field : new String[]{"reason", "comment"}) {
            keys.add("rma.item.page.field." + field);
            keys.add("rma.item.page." + field + ".help");
        }
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
        assertThat(used).allSatisfy(key -> {
            assertThat(pl).containsKey(key);
            assertThat(en).containsKey(key);
        });
    }

    @Test
    void noPageKeyIsLeftOver() throws Exception {
        // given
        Set<String> used = keysUsed();

        // when
        Set<String> defined = new TreeSet<>();
        load("messages_pl.properties").stringPropertyNames().stream().filter(k -> k.startsWith("rma.item.page.")).forEach(defined::add);

        // then
        assertThat(used).containsAll(defined);
    }
}
