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
}
