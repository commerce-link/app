package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The RMA details page speaks both languages: every rma.detail.* key of one bundle exists in the other. */
class RmaDetailMessagesTest {

    private static Set<String> detailKeys(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = RmaDetailMessagesTest.class.getClassLoader().getResourceAsStream(file)) {
            assertThat(in).as(file).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("rma.detail."))
                .collect(Collectors.toSet());
    }

    @Test
    void everyKeyHasBothLanguages() throws IOException {
        // given
        Set<String> polish = detailKeys("messages_pl.properties");
        Set<String> english = detailKeys("messages_en.properties");

        // then
        assertThat(polish).contains("rma.detail.items.title", "rma.detail.action.accept", "rma.detail.dialog.accept.title.one");
        assertThat(english).containsExactlyInAnyOrderElementsOf(polish);
    }
}
