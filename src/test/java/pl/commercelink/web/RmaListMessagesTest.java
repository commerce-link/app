package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Every message key the RMA list templates and Java use exists in both languages, and none is left over. */
class RmaListMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|\"(rma\\.list\\.[a-zA-Z0-9_.]+|general\\.[a-zA-Z0-9_.]+)\"");

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Stream.of(Path.of("src/main/resources/templates/rma.html"),
                Path.of("src/main/resources/templates/fragments/rma-list.html"),
                Path.of("src/main/java/pl/commercelink/orders/rma/RmaListService.java"),
                Path.of("src/main/java/pl/commercelink/web/rma/RmaRowMapper.java"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    keys.add(m.group(1) != null ? m.group(1) : m.group(2));
                }
            }
        }
        keys.removeIf(k -> k.startsWith("RMAStatus.") || k.startsWith("MarketplaceReturnStatus.") || k.startsWith("intro."));
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

    /** Tile and period keys are built by concatenation, so the scan above cannot see them. */
    @Test
    void theKeysBuiltFromTilesAndPeriodsExist() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");
        List<String> keys = new ArrayList<>();
        for (String tile : List.of("new", "waiting", "received", "processing")) {
            keys.add("rma.list.tile." + tile);
            keys.add("rma.list.tile." + tile + ".hint");
        }
        for (String period : List.of("30d", "90d", "year")) {
            keys.add("rma.list.dates." + period);
        }

        // when / then
        assertThat(keys).allMatch(pl::containsKey).allMatch(en::containsKey);
    }
}
