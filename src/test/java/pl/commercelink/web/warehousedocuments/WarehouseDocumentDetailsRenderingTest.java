package pl.commercelink.web.warehousedocuments;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.Address;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.ItemLine;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.Link;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.PrintAction;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.Printer;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the warehouse document details body with a hand-built page and the Polish bundle. */
class WarehouseDocumentDetailsRenderingTest {

    private static final String BODY = "<div th:replace=\"~{warehouse-document-details :: body}\"></div>";

    @Test
    void oneHeadingWithTheNumberItsCopyButtonTypeAndReason() {
        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", receiptPage(printers(1))));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).containsPattern("<h1 class=\"cl-page-title\">\\s*PZ/<wbr>MAG1/<wbr>2026/<wbr>000214\\s*</h1>")
                .contains("data-cl-copy=\"PZ/MAG1/2026/000214\"")
                .contains("class=\"cl-record-type\"").contains("Przyjęcie zewnętrzne").contains("· Dostawa od dostawcy")
                .contains("class=\"cl-back\" href=\"/dashboard/warehouse-documents\"")
                .doesNotContain("??").doesNotContain("class=\"box").doesNotContain("style=");
    }

    @Test
    void labelPrintScriptShowsACompactSpinnerWhileBusyAndFillsMessagesWithoutReplacePatterns() throws Exception {
        // given
        String js = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/static/js/label-print.js"));

        // then
        assertThat(js).contains("cl-spinner is-compact").contains("spinner.remove()")
                .contains("template.split('{' + key + '}').join(value)").doesNotContain("template.replace(")
                .contains("'count', root.getAttribute('data-count')");
    }

    @Test
    void onePrinterIsOneButtonSeveralAreAMenu() {
        // when
        String one = SettingsTemplateRenderer.render(BODY, Map.of("page", receiptPage(printers(1))));
        String two = SettingsTemplateRenderer.render(BODY, Map.of("page", receiptPage(printers(2))));

        // then
        assertThat(one).contains("data-cl-label-print").contains("data-printer=\"Zebra 1\"").doesNotContain("label-printers-menu")
                .contains("data-endpoint=\"/dashboard/warehouse-documents/print-labels\"")
                .contains("data-sent=\"Wysłano do drukarki {printer} · etykiety: {count}.\"")
                .contains("data-count=\"6\"");
        assertThat(two).contains("id=\"label-printers-menu\"").contains("data-printer=\"Zebra 2\"").contains("Etykiety: 6");
        assertThat(two).contains("<noscript>");
    }

    @Test
    void itemsTableHasValueColumnSummaryAndHistoryMenu() {
        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", receiptPage(printers(0))));

        // then
        assertThat(html).contains("class=\"cl-table is-compact is-wrap is-line-items\"")
                .contains(">Wartość netto<").contains("Razem: 6 szt.")
                .contains("Historia pozycji w dostawie").contains("aria-label=\"Akcje: Samsung 990 PRO\"");
    }

    @Test
    void documentWithoutItemsSaysSoInsteadOfAnEmptyCard() {
        // given
        WarehouseDocumentPage full = receiptPage(printers(0));
        WarehouseDocumentPage empty = new WarehouseDocumentPage(full.documentId(), full.backHref(), full.backLabel(), full.number(),
                full.typeName(), full.incoming(), full.reason(), full.createdText(), full.author(), "0,00 PLN", "Pozycje: 0",
                List.of(), false, "Razem: 0 szt.", "0,00 PLN netto", full.linksTitle(), full.links(), full.counterparty(),
                full.deliveryAddress(), full.issuer(), null);

        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", empty));

        // then
        assertThat(html).contains("<p class=\"cl-list-empty\">Dokument nie ma pozycji.</p>")
                .doesNotContain("is-line-items").doesNotContain("Razem: 0 szt.").doesNotContain("data-cl-label-print");
    }

    @Test
    void sideColumnShowsLinksAndAddressCards() {
        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", receiptPage(printers(0))));

        // then
        assertThat(html).contains(">Powiązania<").contains("href=\"/dashboard/deliveries/details?deliveryId=del-1\"")
                .contains(">Kontrahent<").contains("NIP 8951628108").contains(">Wystawca<")
                .doesNotContain(">Adres dostawy<");
    }

    private static List<Printer> printers(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new Printer("Zebra " + i, "uid-" + i)).toList();
    }

    private static WarehouseDocumentPage receiptPage(List<Printer> printers) {
        PrintAction print = printers.isEmpty() ? null
                : new PrintAction(WarehouseDocumentPageMapper.PRINT_ENDPOINT, 6, "Etykiety: 6", printers);
        return new WarehouseDocumentPage("doc-1", "/dashboard/warehouse-documents", "Dokumenty magazynowe",
                "PZ/MAG1/2026/000214", "Przyjęcie zewnętrzne", true, "Dostawa od dostawcy", "Utworzono 07.10.2026, 14:32",
                "Jan Kowalski", "3 494,00 PLN", "Pozycje: 2",
                List.of(new ItemLine("Samsung 990 PRO", "8806094215038", "MZ-V9P2T0BW", "5 szt.", "689,00 PLN", "3 445,00 PLN",
                                "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=del-1&mfn=MZ-V9P2T0BW", "Akcje: Samsung 990 PRO"),
                        new ItemLine("Kabel HDMI", null, "HDMI21-2M", "1 szt.", "49,00 PLN", "49,00 PLN", null, "Akcje: Kabel HDMI")),
                true, "Razem: 6 szt.", "3 494,00 PLN netto", "Powiązania",
                List.of(new Link("Dostawa", "3f2a9c1e", "/dashboard/deliveries/details?deliveryId=del-1", false),
                        new Link("Magazyn", "MAG1", null, false)),
                new Address("AB S.A.", List.of("Wrocław"), "NIP 8951628108"), null,
                new Address("Sklep Sp. z o.o.", List.of("Warszawa"), null), print);
    }

    private static int occurrences(String html, String needle) {
        int count = 0;
        for (int i = html.indexOf(needle); i >= 0; i = html.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
