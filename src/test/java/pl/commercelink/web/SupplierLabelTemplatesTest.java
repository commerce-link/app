package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierChoice;

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
        // the list resolves the label once per row in DeliveryRowMapper, so the template never sees an identity
        assertThat(template("deliveries.html")).contains("row.supplierLabel()").doesNotContain("delivery.provider");
        assertThat(template("deliveries/pending.html")).contains("row.providerLabel()").doesNotContain("row.provider()");
        assertThat(template("deliveries/approval.html")).contains("page.supplierLabel()");
        // the controller takes the name from SupplierLabels, the details page prints the model's value
        assertThat(Files.readString(Path.of("src/main/java/pl/commercelink/web/DeliveriesController.java")))
                .contains("supplierLabels.forStoreId(storeId).of(delivery.getProvider())");
        assertThat(template("deliveries/details/header.html")).contains("h.supplierName()").doesNotContain("delivery.provider");
        // the controller takes the name from SupplierLabels (DeliveryCreateControllerTest), the pages only print it
        assertThat(template("deliveries/create/parts.html")).contains("page.supplierName()");
        // the inventory page resolves the label once per offer in InventorySearch (OfferRow#supplierLabel),
        // because a global search spans stores and has no single label map to read in the template
        assertThat(template("fragments/inventory-results.html")).contains("offer.supplierLabel()")
                .doesNotContain("${offer.supplier}");
    }

    void fulfilmentRowsKeepTheIdentityAsDataAndShowTheLabel() throws Exception {
        // when
        String html = template("fulfilment.html");

        // then -- the label is resolved once per offer in FulfilmentSelectPageFactory (names.of(provider))
        assertThat(html).contains("th:data-provider=\"${e.source.provider}\"").contains("th:data-provider-label=\"${o.label()}\"");
        assertThat(Files.readString(Path.of("src/main/java/pl/commercelink/web/fulfilment/FulfilmentSelectPageFactory.java")))
                .contains("names.of(provider)");
        assertThat(html).doesNotContain("<script th:inline").doesNotContain("innerHTML");
    }

    @Test
    void orderAndRmaFiltersUseSelectsOfConnections() throws Exception {
        // when / then
        assertThat(template("fragments/supplier-choice.html")).contains("name=\"supplier\"")
                .contains("th:each=\"option : ${options}\"")
                // "other supplier" reveals a text field for a supplier that is not connected to the store;
                // the option value is a literal (Thymeleaf 3.1 forbids T() here), so keep it in sync with the constant
                .contains("value=\"" + SupplierChoice.CUSTOM + "\"")
                .contains("data-custom=\"" + SupplierChoice.CUSTOM + "\"")
                .contains("name=\"customSupplier\"")
                .contains("order.item.supplier.custom.hint");
        // The RMA pages resolve the label in the controller (RmaCenterView.title), so the templates must not fall
        // back to the stored identity, which carries a connection token such as "Elko-k7f3a9c2".
        assertThat(template("rma-center-form.html")).contains("${providerOptions}");
        assertThat(template("rma-centers.html")).doesNotContain("${center.provider}").contains("center.title()");
        assertThat(template("warehouse.html"))
                .contains("fragments/supplier-choice :: field('quickAddSupplier', ${providerOptions}, false)");
    }

    @Test
    void theProposalsTableShowsLabelsForItsAlternativeSuppliers() throws Exception {
        // when / then -- alternativeSuppliers carries connection identities; RecommendationRow maps them through the
        // label function in the controller, so the proposals table only joins the names it was handed
        assertThat(template("catalog/products-add.html"))
                .contains("${#strings.listJoin(row.suppliers(), ', ')}")
                .doesNotContain("alternativeSuppliers")
                .doesNotContain("${row.provider");
    }
}
