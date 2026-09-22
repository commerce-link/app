package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMessagesTest {

    private Set<String> inventoryKeys(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("inventory.") || key.startsWith("intro.inventory."))
                .collect(Collectors.toSet());
    }

    @Test
    void polishAndEnglishDefineTheSameInventoryKeys() throws Exception {
        // given / when / then
        assertThat(inventoryKeys("messages_pl.properties")).isEqualTo(inventoryKeys("messages_en.properties"));
    }

    @Test
    void everyMatchModeAndConnectionModeHasALabel() throws Exception {
        // given / when / then
        assertThat(inventoryKeys("messages_en.properties")).contains(
                "inventory.matchedBy.EAN", "inventory.matchedBy.MFN", "inventory.matchedBy.PIM_ID",
                "inventory.mode.GLOBAL", "inventory.mode.OWN", "inventory.mode.MANUAL");
    }
}
