package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Every operator-facing supplier name goes through the label map; identities stay in values and URLs. */
class SupplierLabelTemplatesTest {

    private String template(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + name));
    }

    @Test
    void deliveryScreensShowLabelsNotIdentities() throws Exception {
        // when / then
        assertThat(template("deliveries.html")).contains("supplierLabels.of(delivery.storeId, delivery.provider)")
                .doesNotContain("th:text=\"${delivery.provider}\"");
        assertThat(template("deliveriesPreview.html")).contains("supplierLabels.of(candidate.provider)")
                .contains("supplierLabels.of(delivery.provider)");
        assertThat(template("deliveryApproval.html")).contains("supplierLabels.of(delivery.provider)");
        assertThat(template("deliveryDetails.html")).contains("supplierLabels.of(delivery.provider)");
        assertThat(template("deliveryCreate.html")).contains("supplierLabels.of(form.provider)");
        assertThat(template("deliveryPurchaseConfirmation.html")).contains("supplierLabels.of(form.provider)");
        assertThat(template("dropshipCreate.html")).contains("supplierLabels.of(form.provider)");
        assertThat(template("dropshipConfirmation.html")).contains("supplierLabels.of(form.provider)");
        assertThat(template("payments.html")).contains("supplierLabels.of(delivery.provider)");
        assertThat(template("inventory.html")).contains("supplierLabels.of(item.supplier)");
    }

    @Test
    void fulfilmentCardsKeepTheIdentityAsDataAndShowTheLabel() throws Exception {
        // when
        String html = template("fulfilment.html");

        // then
        assertThat(html).contains("th:data-provider=\"${entry.source.provider}\"");
        assertThat(html).contains("supplierLabels.of(entry.source.provider)");
    }

    @Test
    void orderRmaAndDeliveryFiltersUseSelectsOfConnections() throws Exception {
        // when / then
        assertThat(template("orderDetails.html")).contains("id=\"quickAssignSupplier\" name=\"supplier\"")
                .contains("th:each=\"option : ${assignableSuppliers}\"")
                .doesNotContain("type=\"text\" id=\"quickAssignSupplier\"");
        assertThat(template("deliveries.html")).contains("<select name=\"provider\"")
                .contains("th:each=\"option : ${providerOptions}\"");
        assertThat(template("rma-center-form.html")).contains("th:each=\"option : ${providerOptions}\"");
        assertThat(template("rma-centers.html")).contains("supplierLabels.of(center.provider)");
    }
}
