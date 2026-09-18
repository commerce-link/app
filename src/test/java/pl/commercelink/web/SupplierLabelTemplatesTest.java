package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierChoice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    void addingANewInstanceDoesNotBorrowTheLegacyRowOfTheSameType() throws Exception {
        // when
        String html = template("store-fulfilment.html");

        // then -- while adding, activeIdentity() must be empty instead of falling back to the
        // selected type, which matched the legacy row of the same type (stored secret, warning)
        assertThat(html).contains("return identityInput.value;")
                .doesNotContain("identityInput.value || select.value");
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
        assertThat(template("orderDetails.html"))
                .contains("fragments/supplier-choice :: field('quickAssignSupplier', ${assignableSuppliers}, true)");
        assertThat(template("fragments/supplier-choice.html")).contains("name=\"supplier\"")
                .contains("th:each=\"option : ${options}\"")
                // "other supplier" reveals a text field for a supplier that is not connected to the store;
                // the option value is a literal (Thymeleaf 3.1 forbids T() here), so keep it in sync with the constant
                .contains("value=\"" + SupplierChoice.CUSTOM + "\"")
                .contains("data-custom=\"" + SupplierChoice.CUSTOM + "\"")
                .contains("name=\"customSupplier\"")
                .contains("order.item.supplier.custom.hint");
        assertThat(template("deliveries.html")).contains("<select name=\"provider\"")
                .contains("th:each=\"option : ${providerOptions}\"")
                .contains("value=\"" + SupplierChoice.CUSTOM + "\"")
                .contains("data-custom=\"" + SupplierChoice.CUSTOM + "\"")
                .contains("name=\"providerCustom\"")
                // a filter value outside the options (e.g. a disconnected instance) must stay
                // visible as the selected option instead of silently showing "all"
                .contains("!#lists.contains(providerOptions.![identity()], searchParams.provider)")
                .contains("supplierLabels.of(searchParams.provider)");
        // The RMA pages resolve the label in the controller (RmaCenterView.title), so the templates must not fall
        // back to the stored identity, which carries a connection token such as "Elko-k7f3a9c2".
        assertThat(template("rma-center-form.html")).contains("${providerOptions}");
        assertThat(template("rma-centers.html")).doesNotContain("${center.provider}").contains("center.title()");
        assertThat(template("warehouse.html"))
                .contains("fragments/supplier-choice :: field('quickAddSupplier', ${providerOptions}, false)");
    }

    @Test
    void theRecommendationTableShowsLabelsForItsAlternativeSuppliers() throws Exception {
        // when / then -- alternativeSuppliers carries connection identities, not display names
        assertThat(template("catalogDetails_categoryDefinition_productRecommendations.html"))
                .contains("supplierLabels.of(provider)")
                .doesNotContain("th:text=\"${provider}\"");
    }

    /**
     * Connection labels and product names are operator-supplied free text, and the fulfilment
     * screen builds several chips and rows as HTML strings assigned to innerHTML. Every such
     * interpolation has to go through esc(); this walks each innerHTML statement in the file and
     * fails on a raw one, so a future edit that drops the escape is caught here rather than in a
     * penetration test.
     */
    @Test
    void fulfilmentEscapesEveryLabelOrNameItWritesThroughInnerHtml() throws Exception {
        // given
        String html = template("fulfilment.html");

        // when
        List<String> statements = innerHtmlStatements(html);

        // then
        assertThat(statements).isNotEmpty();
        for (String statement : statements) {
            for (String token : LABEL_TOKENS) {
                int from = 0;
                int at;
                while ((at = statement.indexOf(token, from)) >= 0) {
                    assertThat(wrappedInEsc(statement, at))
                            .as("unescaped %s in: %s", token, statement)
                            .isTrue();
                    from = at + token.length();
                }
            }
        }
    }

    @Test
    void theVariantChipEscapesTheLabelItRenders() throws Exception {
        // when / then -- the chip is an HTML string that ends up inside a variant's innerHTML
        assertThat(template("fulfilment.html")).contains("const label = esc(providerLabels()[p] || p);");
    }

    /** Tokens that carry operator-supplied text into the fulfilment screen's HTML string builds. */
    private static final List<String> LABEL_TOKENS = List.of("labels[", "providerLabel", "winnerProv", "dataset.name");

    /** Whether the operand the token at this position belongs to is the argument of an esc(...) call. */
    private boolean wrappedInEsc(String statement, int tokenAt) {
        int start = tokenAt;
        while (start > 0 && (Character.isLetterOrDigit(statement.charAt(start - 1)) || ".[]_$".indexOf(statement.charAt(start - 1)) >= 0)) {
            start--;
        }
        return start >= 4 && statement.startsWith("esc(", start - 4);
    }

    /** Every `x.innerHTML = ...;` statement of the template, folded onto one line. */
    private List<String> innerHtmlStatements(String html) {
        List<String> statements = new ArrayList<>();
        String[] lines = html.replace("\r\n", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].contains("innerHTML")) {
                continue;
            }
            StringBuilder statement = new StringBuilder(lines[i].trim());
            int end = i;
            while (!lines[end].trim().endsWith(";") && end + 1 < lines.length) {
                end++;
                statement.append(' ').append(lines[end].trim());
            }
            statements.add(statement.toString());
        }
        return statements;
    }
}
