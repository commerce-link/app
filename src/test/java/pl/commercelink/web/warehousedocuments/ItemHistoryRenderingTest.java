package pl.commercelink.web.warehousedocuments;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.builtin.MfnHistory;
import pl.commercelink.warehouse.builtin.MfnHistoryRow;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemHistoryRenderingTest {

    private static final String BODY = "<div th:replace=\"~{warehouse-document-mfn-history :: body}\"></div>";

    @Test
    void movesShowSignedChangesSummaryAndBackToTheDelivery() {
        // given
        MfnHistory history = new MfnHistory("Samsung 990 PRO", List.of(
                new MfnHistoryRow("d1", "PZ/MAG1/2026/000210", DocumentType.GoodsReceipt, LocalDateTime.of(2026, 10, 2, 8, 0), 5, 5, 5, null, null),
                new MfnHistoryRow("d2", "WZ/MAG1/2026/001179", DocumentType.GoodsIssue, LocalDateTime.of(2026, 10, 3, 11, 0), 2, -2, 3, null, null)));
        ItemHistoryPage page = page(history);

        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", page));

        // then
        assertThat(html).contains("class=\"cl-qty-change is-in\">+5<").contains("class=\"cl-qty-change is-out\">−2<")
                .contains("Przyjęto 5 szt. · wydano 2 szt. · zostało 3 szt.")
                .contains("class=\"cl-back\" href=\"/dashboard/deliveries/details?deliveryId=del-1\"")
                .contains("MFN MZ-V9P2T0BW").contains("3 szt.")
                .contains(">PZ/<wbr>MAG1/<wbr>2026/<wbr>000210</a>")
                .doesNotContain("has-text-danger").doesNotContain("style=").doesNotContain("??");
        assertThat(html.split("<h1", -1).length - 1).isEqualTo(1);
    }

    @Test
    void aDocumentsReasonAndNoteAreShownAndAbsentOnesLeaveNothingBehind() {
        // given
        MfnHistory history = new MfnHistory("X", List.of(
                new MfnHistoryRow("d1", "PZ/1", DocumentType.GoodsReceipt, LocalDateTime.of(2026, 10, 2, 8, 0), 5, 5, 5, null, null),
                new MfnHistoryRow("d2", "RW/1", DocumentType.InternalIssue, LocalDateTime.of(2026, 10, 3, 8, 0), 1, -1, 4,
                        DocumentReason.Destruction, "  Zalany w magazynie B ")));

        // when
        ItemHistoryPage page = page(history);
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", page));

        // then
        assertThat(page.moves().get(0).reason()).isNull();
        assertThat(page.moves().get(0).note()).isNull();
        assertThat(page.moves().get(1).reason()).isEqualTo("Zniszczenie");
        assertThat(page.moves().get(1).note()).isEqualTo("Zalany w magazynie B");
        assertThat(html).contains("· Zniszczenie").contains("Zalany w magazynie B");
        assertThat(html.split("· Zniszczenie", -1).length - 1).isEqualTo(1);
        String visible = html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ");
        assertThat(visible).contains(page.moves().get(1).typeName() + " · Zniszczenie");
    }

    @Test
    void backToTheDocumentBreaksItsNumberAfterSlashes() {
        // given
        ItemHistoryPage page = ItemHistoryPage.of(new MfnHistory("X", List.of()), "del-1", "M",
                "/dashboard/warehouse-documents/details?documentId=d1", "PZ/MAG1/2026/000210",
                TestMessages.polish(), java.util.Locale.forLanguageTag("pl"));

        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", page));

        // then
        assertThat(html).containsPattern("class=\"cl-back\"[^>]*>(?s).*<span>PZ/<wbr>MAG1/<wbr>2026/<wbr>000210</span>");
    }

    @Test
    void anEmptyHistoryShowsTheMessageWithoutATable() {
        // given
        ItemHistoryPage page = page(new MfnHistory(null, List.of()));

        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", page));

        // then
        assertThat(html).contains("Ta pozycja nie ma jeszcze dokumentów w tej dostawie.").doesNotContain("<table");
    }

    @Test
    void anItemWithoutADocumentTypeRendersADash() {
        // given
        MfnHistory history = new MfnHistory("X", List.of(
                new MfnHistoryRow("d1", "X/1", null, null, 1, 0, 0, null, null)));

        // when
        ItemHistoryPage page = page(history);

        // then
        assertThat(page.moves().get(0).typeName()).isEqualTo("—");
        assertThat(page.moves().get(0).change()).isEqualTo("0");
        assertThat(page.moves().get(0).date()).isEqualTo("—");
    }

    @Test
    void deliveryLinkEncodesTheDeliveryId() {
        // given
        MfnHistory history = new MfnHistory("X", List.of());

        // when
        ItemHistoryPage page = ItemHistoryPage.of(history, "del 1&x=2", "M", "/back", "Back",
                TestMessages.polish(), java.util.Locale.forLanguageTag("pl"));

        // then
        assertThat(page.deliveryHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=del+1%26x%3D2");
    }

    @Test
    void anItemWithoutADeliveryNamesItsIdWithoutALink() {
        // given
        ItemHistoryPage page = ItemHistoryPage.of(new MfnHistory("X", List.of()), "Acme", "M", "/dashboard/warehouse/items/w-1", "X",
                TestMessages.polish(), java.util.Locale.forLanguageTag("pl")).withDeliveryHref(null);

        // when
        String html = SettingsTemplateRenderer.render(BODY, Map.of("page", page));

        // then
        assertThat(html).doesNotContain("/dashboard/deliveries/details").contains(">Acme</span>");
    }

    private static ItemHistoryPage page(MfnHistory history) {
        return ItemHistoryPage.of(history, "del-1", "MZ-V9P2T0BW", "/dashboard/deliveries/details?deliveryId=del-1",
                "Dostawa del-1", TestMessages.polish(), java.util.Locale.forLanguageTag("pl"));
    }
}
