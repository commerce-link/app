package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;
import pl.commercelink.web.reports.StockLedgerClosingBlocker;
import pl.commercelink.web.reports.StockLedgerClosingView;
import pl.commercelink.web.reports.StockLedgerClosingWarning;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerPageRenderingTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final StockLedgerPeriod AUGUST = new StockLedgerPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

    private static String render(StockLedgerClosingView closing, StockLedgerClosingWarning warning, String error) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("ledgerClosing", closing);
        context.setVariable("ledgerWarning", warning);
        context.setVariable("ledgerError", error);
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{reports/stock-ledger :: body}\"></div>", context);
    }

    @Test
    void afterAClosedPeriodTheStartIsHiddenAndRegeneratingGoesThroughAConfirmation() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(AUGUST), FROM, TO, Locale.ENGLISH);

        // when
        String html = render(closing, null, null);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).doesNotContainPattern("type=\"date\"[^>]*name=\"dateFrom\"|name=\"dateFrom\"[^>]*type=\"date\"")
                .containsPattern("type=\"hidden\"[^>]*name=\"dateFrom\"[^>]*value=\"2026-09-01\"|name=\"dateFrom\"[^>]*type=\"hidden\"[^>]*value=\"2026-09-01\"")
                .contains("data-cl-confirm")
                .contains("href=\"/dashboard/reports/stock-ledger/close?dateFrom=2026-08-01&amp;dateTo=2026-08-31\"");
    }

    @Test
    void theFirstClosingLetsTheStartBeChosenAndExplainsTheEmptyList() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(), FROM, TO, Locale.ENGLISH);

        // when
        String html = render(closing, null, null);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("id=\"ledger-from\"").contains("No period has been closed yet");
    }

    @Test
    void aHeldClosingListsTheDeliveriesAndOffersToCloseAgain() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(AUGUST), FROM, TO, Locale.ENGLISH);
        StockLedgerClosingWarning warning = new StockLedgerClosingWarning(FROM, TO, List.of(
                new StockLedgerClosingBlocker("delivery-1", "ZS/1/2026", "Action", "ZS-77", LocalDate.of(2026, 9, 15),
                        "reports.stockLedger.closing.missing.sync")));

        // when
        String html = render(closing, warning, "Closing held");

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("cl-alert is-warn").contains("/dashboard/deliveries/details?deliveryId=delivery-1")
                .contains("Close the period again");
    }
}
