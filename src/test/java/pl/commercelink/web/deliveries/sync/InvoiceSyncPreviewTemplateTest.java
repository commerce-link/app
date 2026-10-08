package pl.commercelink.web.deliveries.sync;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.deliveries.create.DeliveryCreateTemplates;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.create.DeliveryCreateTemplates.occurrences;

/** Renders invoiceSyncPreview.html as the controller serves it, with Spring's request context and Polish messages. */
class InvoiceSyncPreviewTemplateTest {

    @Test
    void recordHeaderLeadsBackToTheDeliveryAndNamesTheInvoice() {
        // when
        String html = render(typical());

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Synchronizacja z fakturą FV/2026/10/0412")
                .contains("class=\"cl-back\" href=\"/dashboard/deliveries/details?deliveryId=d-1\"")
                .contains("Dostawa 3F2A9C").contains("<strong>AB S.A.</strong>").contains("Nr dostawy ZK/88123")
                .contains("Zamówiona 06.10.2026").contains("<span class=\"cl-status is-warn\">Do synchronizacji</span>")
                .contains("href=\"https://invoices.example/FV-412\"")
                .doesNotContain("??").doesNotContain("class=\"box").doesNotContain("class=\"tag").doesNotContain("style=")
                .doesNotContain("notification");
    }

    @Test
    void everyRowShowsItsStateAsAPillWithTextAndTheCountsAboveTheTable() {
        // when
        String html = render(typical());

        // then
        assertThat(html).contains("<span class=\"cl-status is-ok\">Zgodne</span>")
                .contains("<span class=\"cl-status is-info\">Różnica 1 gr</span>")
                .contains("<span class=\"cl-status is-bad\">Do przypisania</span>")
                .contains("<span class=\"cl-status is-neutral\">Brak kosztu</span>")
                .contains("Zgodne: 1").contains("Różnica 1 gr: 1").contains("Do przypisania: 1");
        assertThat(html).containsPattern("<li hidden=\"hidden\" data-state=\"DIFFERENT\">|<li data-state=\"DIFFERENT\" hidden=\"hidden\">");
    }

    @Test
    void theFormPostsTheChoicesUnderTheNamesTheSaveReads() {
        // when
        String html = render(typical());

        // then
        assertThat(html).contains("name=\"mappings[0].selectedPositionId\"").contains("name=\"mappings[0].mfn\"")
                .contains("name=\"shippingCostPositionId\"").contains("name=\"paymentCostPositionId\"")
                .contains("name=\"deliveryId\"").contains("name=\"invoiceShortcut\"")
                .containsPattern("<option value=\"p1\"[^>]* selected=\"selected\">3\u00a0249,00 PLN · 2 × Laptop</option>")
                .contains("action=\"/dashboard/deliveries/sync/apply\"");
    }

    @Test
    void invoiceLinesNoRowChoseAreListedAndTheTotalsSayHowMuchIsLeft() {
        // when
        String html = render(typical());

        // then
        assertThat(html).contains("Pozycje faktury bez przypisania: 2").contains("1 × Opłata BDO")
                .contains("data-cl-sync-left").contains("2\u00a0402,40 PLN");
    }

    @Test
    void effectsAnnounceTheAddedPaymentTheDueDateAndTheCounterparty() {
        // when
        String html = render(typical());

        // then
        assertThat(html).contains("Koszty produktów do zmiany: 1 z 3").contains("Bez zmian (zgodne albo bez przypisania): 2")
                .contains("Koszt dostawy: 35,00 PLN</strong><span class=\"cl-effects-was\" data-cl-effect-sub>bez zmian</span>")
                .contains("Płatność: dodamy przelew na kwotę brutto dostawy")
                .contains("Termin płatności: 22.10.2026").contains("dni od zamówienia: 16").contains("teraz: brak")
                .contains("Kontrahent: ABSA").contains("teraz: AB")
                .contains("Dostawa zostanie oznaczona jako zsynchronizowana");
    }

    @Test
    void anUnpaidInvoiceAnnouncesTheRemovedPaymentsInTheBadTone() {
        // given
        InvoiceSyncPreview preview = typical();
        preview.setInvoicePaid(false);
        preview.setDeliveryPaid(true);
        preview.setDeliveryPayments(List.of(new InvoiceSyncPreview.PaymentLine(100.0, "FV/2026/10/0412"),
                new InvoiceSyncPreview.PaymentLine(20.5, null)));

        // when
        String html = render(preview);

        // then
        assertThat(html).containsPattern("<li class=\"is-bad\">\\s*<i class=\"fas fa-exclamation-circle\"")
                .contains("Płatności dostawy do usunięcia: 2")
                .contains("<span class=\"cl-effects-was\">100,00 PLN · FV/2026/10/0412</span>")
                .contains("<span class=\"cl-effects-was\">20,50 PLN</span>")
                .contains("dostawa przestanie być opłacona").doesNotContain("dodamy przelew");
    }

    @Test
    void rowsMatchingTheInvoiceAreNotAnnouncedAsChangedCosts() {
        // given
        InvoiceSyncPreview preview = typical();
        preview.getMappings().get(1).setUnitCost(590.00);
        preview.getMappings().remove(2);

        // when
        String html = render(preview);

        // then
        assertThat(html).contains("<span class=\"cl-status is-ok\">Zgodne: 2</span>")
                .contains("<strong data-cl-effect-text>Koszty produktów bez zmian</strong>")
                .doesNotContain("cl-alert is-ok");
    }

    @Test
    void anInvoiceInAnotherCurrencyWarnsThatTheSaveDoesNotConvertIt() {
        // given
        InvoiceSyncPreview preview = typical();
        preview.setCurrency("EUR");
        preview.setExchangeRate(4.25);

        // when
        String html = render(preview);

        // then
        assertThat(html).contains("Faktura w EUR, kurs 4,2500")
                .contains("Faktura jest w EUR. Zapis przepisze kwoty faktury bez przeliczenia na PLN");
        assertThat(render(typical())).doesNotContain("bez przeliczenia na PLN");
    }

    @Test
    void aDeliveryAwaitingApprovalSaysSoBeforeTheButtonIsPressed() {
        // given
        InvoiceSyncPreview preview = typical();
        preview.setDeliveryAwaitingApproval(true);

        // when
        String html = render(preview);

        // then
        assertThat(html).contains("Czeka na akceptację").contains("Dostawa czeka na akceptację zakupu u dostawcy")
                .contains("<button type=\"submit\" class=\"cl-button is-primary\" aria-describedby=\"sync-locked-reason\" disabled=\"disabled\">");
    }

    @Test
    void scriptRedrawsWithTheServerRuleAndFillsMessagesWithoutReplacePatterns() throws Exception {
        // given
        String js = Files.readString(Path.of("src/main/resources/static/js/invoice-sync.js"));
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(js).contains("var EPS = 0.005;").contains("var CLOSE_DELTA = 0.01;")
                .contains("text.split('{' + i + '}').join(value)").doesNotContain(".replace('{'")
                .contains("NO_COST").contains("'is-neutral'")
                .contains("window.addEventListener('pageshow'").contains("r.choice && r.state !== 'EXACT'");
        assertThat(css).contains(".cl-page .cl-table.is-match {").contains(".cl-page .cl-status-row {")
                .contains(".cl-page .cl-effects {");
    }

    private static String render(InvoiceSyncPreview preview) {
        return DeliveryCreateTemplates.render("invoiceSyncPreview", Map.of("preview", preview)).replaceAll("[ \\t\\r\\n]+", " ")
                .replace("> <", "><");
    }

    private static InvoiceSyncPreview typical() {
        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setDeliveryId("d-1");
        preview.setDeliveryShortId("3F2A9C");
        preview.setExternalDeliveryId("ZK/88123");
        preview.setSupplierName("AB S.A.");
        preview.setDeliveryOrderedAt("06.10.2026");
        preview.setInvoiceId("inv-1");
        preview.setInvoiceNumber("FV/2026/10/0412");
        preview.setViewUrl("https://invoices.example/FV-412");
        preview.setCurrency("PLN");
        preview.setExchangeRate(1.0);
        preview.setInvoicePriceNet(9000.0);
        preview.setInvoicePriceGross(11070.0);
        preview.setDeliveryTotalCostNet(8960.0);
        preview.setInvoicePaid(true);
        preview.setInvoicePaymentToDate("2026-10-22");
        preview.setPaymentTermDays(16);
        preview.setInvoiceShortcut("ABSA");
        preview.setDeliveryProvider("AB");
        preview.setShippingCost(35.0);
        preview.setShippingCostPositionId("p4");
        preview.setOptions(List.of(option("p1", 2, "Laptop", 3249.00), option("p2", 4, "Monitor", 590.00),
                option("p3", 2, "Stacja", 1200.00), option("p4", 1, "Transport", 35.00), option("p5", 1, "Opłata BDO", 2.40)));
        preview.setMappings(new ArrayList<>(List.of(mapping("MFN-1", "Laptop E14", 2, 3249.00, "p1"),
                mapping("MFN-2", "Monitor P2425H", 4, 589.99, "p2"), mapping("MFN-3", "Dock Gen 2", 2, 719.00, null))));
        return preview;
    }

    private static InvoiceSyncPreview.Option option(String id, int qty, String name, double priceNet) {
        InvoiceSyncPreview.Option option = new InvoiceSyncPreview.Option();
        option.setId(id);
        option.setQty(qty);
        option.setName(name);
        option.setPriceNet(priceNet);
        option.setTotalNet(qty * priceNet);
        option.setCurrency("PLN");
        return option;
    }

    private static InvoiceSyncPreview.Mapping mapping(String mfn, String name, int qty, double unitCost, String positionId) {
        InvoiceSyncPreview.Mapping mapping = new InvoiceSyncPreview.Mapping();
        mapping.setMfn(mfn);
        mapping.setName(name);
        mapping.setQty(qty);
        mapping.setUnitCost(unitCost);
        mapping.setSelectedPositionId(positionId);
        return mapping;
    }
}
