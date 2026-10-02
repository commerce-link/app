package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.DropshipRejection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DropshipRejectionMessagesTest {

    @Test
    void enumConstantBecomesTheCamelCaseKey() {
        // when / then
        assertThat(DropshipRejectionMessages.keyFor(DropshipRejection.NO_SHIPPING_DETAILS))
                .isEqualTo("orders.dropship.rejected.noShippingDetails");
    }

    @Test
    void everyRejectionReasonHasAMessageKeyInBothLanguageFiles() throws Exception {
        // given
        String pl = Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
        String en = Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8);

        // when / then
        for (DropshipRejection rejection : DropshipRejection.values()) {
            String key = DropshipRejectionMessages.keyFor(rejection);
            assertThat(pl).contains("\n" + key + "=");
            assertThat(en).contains("\n" + key + "=");
        }
    }

    @Test
    void pickupPointMessageNamesTheButtonThatExistsOnTheStepOnePage() throws Exception {
        // given
        java.util.Properties pl = load("messages_pl.properties");
        java.util.Properties en = load("messages_en.properties");

        // when / then
        assertThat(pl.getProperty("orders.dropship.error.pickupPointUnsupported"))
                .contains(pl.getProperty("deliveries.create.action.manual")).doesNotContain("Zapisz");
        assertThat(en.getProperty("orders.dropship.error.pickupPointUnsupported"))
                .contains(en.getProperty("deliveries.create.action.manual")).doesNotContain("(Save)");
    }

    private static java.util.Properties load(String file) throws Exception {
        java.util.Properties properties = new java.util.Properties();
        properties.load(Files.newBufferedReader(Path.of("src/main/resources/" + file), StandardCharsets.UTF_8));
        return properties;
    }
}
