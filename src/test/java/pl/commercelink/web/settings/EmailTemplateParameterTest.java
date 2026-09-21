package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.notifications.EmailNotificationType;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateParameterTest {

    @Test
    void aValueIsPastedInDoubleBraces() {
        // when / then
        assertThat(EmailTemplateParameter.parse("orderId").snippet()).isEqualTo("{{orderId}}");
    }

    @Test
    void aListIsASectionRepeatingItsFields() {
        // when / then
        assertThat(EmailTemplateParameter.parse("products (category, name, quantity, price)").snippet())
                .isEqualTo("{{#products}}{{category}} {{name}} {{quantity}} {{price}}{{/products}}");
    }

    @Test
    void theOrderConfirmationNamesTheDocumentTypeItActuallyHas() {
        // when / then
        assertThat(EmailTemplateParameter.of(EmailNotificationType.ORDER_CONFIRMATION))
                .extracting(EmailTemplateParameter::name).contains("documentType").doesNotContain("receiptType");
    }
}
