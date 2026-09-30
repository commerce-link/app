package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDeliveriesMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|\"(deliveries\\.(?:pending|list)\\.[a-zA-Z0-9_.]+)\"");

    private static Properties load(String name) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/" + name), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void everyKeyOfThePageExistsInBothLanguages() throws Exception {
        // given
        Set<String> keys = new TreeSet<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> java = Files.list(Path.of("src/main/java/pl/commercelink/web/deliveries/pending"))) {
            java.forEach(files::add);
        }
        files.add(Path.of("src/main/resources/templates/deliveries/pending.html"));
        files.add(Path.of("src/main/resources/templates/fragments/deliveries-pending.html"));
        for (Path file : files) {
            Matcher m = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (m.find()) {
                String key = m.group(1) != null ? m.group(1) : m.group(2);
                if (!key.endsWith(".")) keys.add(key);
            }
        }
        // keys built from enum params in PendingDeliveriesService
        for (String param : List.of("overdue", "today", "approval", "cost")) {
            keys.add("deliveries.pending.tile." + param);
            keys.add("deliveries.pending.tile." + param + ".hint");
        }
        for (String kind : List.of("warehouse", "dropship")) {
            keys.add("deliveries.pending.kind." + kind);
            keys.add("deliveries.pending.kind." + kind + ".desc");
            keys.add("deliveries.pending.empty." + kind);
        }

        // when
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // then
        assertThat(keys).hasSizeGreaterThan(40);
        assertThat(keys.stream().filter(k -> !pl.containsKey(k)).toList()).as("missing in PL").isEmpty();
        assertThat(keys.stream().filter(k -> !en.containsKey(k)).toList()).as("missing in EN").isEmpty();
    }
}
