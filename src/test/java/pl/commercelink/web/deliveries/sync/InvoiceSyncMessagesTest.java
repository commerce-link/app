package pl.commercelink.web.deliveries.sync;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.dtos.InvoiceSyncPreview.MatchState;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSyncMessagesTest {

    @Test
    void polishAndEnglishDefineTheSameInvoiceSyncKeys() throws Exception {
        // when / then
        assertThat(keys("messages_pl.properties")).isEqualTo(keys("messages_en.properties"));
    }

    @Test
    void everyMatchStateHasALabel() throws Exception {
        // given
        Set<String> labels = Arrays.stream(MatchState.values()).map(MatchState::getLabelKey).collect(Collectors.toSet());

        // when / then
        assertThat(keys("messages_pl.properties")).containsAll(labels);
    }

    private static Set<String> keys(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames().stream().filter(key -> key.startsWith("invoiceSync.")).collect(Collectors.toSet());
    }
}
