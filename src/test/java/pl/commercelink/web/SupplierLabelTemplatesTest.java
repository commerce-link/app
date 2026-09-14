package pl.commercelink.web;

import org.junit.jupiter.api.Test;

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
