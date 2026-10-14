package pl.commercelink.web;

import pl.commercelink.web.reports.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;
import pl.commercelink.web.reports.GeneratedReportView;
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
        return render(closing, warning, error, null);
    }

    private static String render(StockLedgerClosingView closing, StockLedgerClosingWarning warning, String error, ReportPeriod report) {
        return render(closing, warning, error, report, List.of());
    }

    private static String render(StockLedgerClosingView closing, StockLedgerClosingWarning warning, String error, ReportPeriod report,
                                 List<GeneratedReportView> generated) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("generatedReports", generated);
        context.setVariable("ledgerExportRange", report != null ? report : new ReportPeriod(FROM, TO));
        context.setVariable("report", report);
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
        assertThat(html).contains("aria-label=\"Download CSV: August 2026\"").contains("<span>Download</span>");
    }

    @Test
    void aFixedStartStandsBesideTheEndAndTheHelpNamesThePeriodBeforeIt() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(AUGUST), FROM, TO, Locale.ENGLISH);

        // when
        String html = render(closing, null, null);

        // then
        assertThat(html).containsPattern("cl-inline-fields\">\\s*<div class=\"cl-field\">\\s*<span class=\"cl-label\">From</span>"
                        + "[\\s\\S]*?<span class=\"cl-field-static\">1.09.2026</span>\\s*</div>\\s*<div class=\"cl-field\">\\s*"
                        + "<label class=\"cl-label\" for=\"ledger-to\">To</label>")
                .contains("this one starts the day after the previous one (August 2026)")
                .doesNotContain("cl-period-facts");
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
        String html = render(closing, warning, "Closing March held by deliveries");

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).containsPattern("id=\"close-title\">\\s*Closing held\\s*<").doesNotContain("Next period to close");
        assertThat(html).contains("cl-alert is-warn").contains("/dashboard/deliveries/details?deliveryId=delivery-1")
                .contains("Close the period again");
        assertThat(html).contains("cl-table cl-ledger-blockers").contains(">15.09.2026<").doesNotContain("2026-09-15<");
    }

    @Test
    void theRangeUnderAClosedPeriodIsPrintedOnlyWhenItDiffersFromTheLabel() {
        // given
        StockLedgerPeriod firstHalf = new StockLedgerPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(AUGUST, firstHalf), FROM, TO, Locale.ENGLISH);
        String halfLabel = closing.closedPeriods().get(1).label();
        String augustLabel = closing.closedPeriods().get(0).label();

        // when
        String html = render(closing, null, null);

        // then
        assertThat(closing.closedPeriods().get(1).rangeText()).isEqualTo(halfLabel);
        assertThat(closing.closedPeriods().get(0).rangeText()).isNotEqualTo(augustLabel);
        assertThat(html.split("cl-table-sub", -1)).hasSize(2);
    }

    @Test
    void theFormsCarryTheReportPeriodSoTheBackLinkKeepsItAfterAnAction() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(AUGUST), FROM, TO, Locale.ENGLISH);

        // when
        String html = render(closing, null, null, new ReportPeriod(FROM, TO));

        // then
        assertThat(html).containsPattern("type=\"hidden\" name=\"from\" value=\"2026-09-01\"")
                .containsPattern("type=\"hidden\" name=\"to\" value=\"2026-09-30\"")
                .contains("href=\"/dashboard/reports/stock-ledger/close?dateFrom=2026-08-01&amp;dateTo=2026-08-31&amp;from=2026-09-01&amp;to=2026-09-30\"");
    }

    @Test
    void anEmptyListOfClosedPeriodsHasNoAdviceAboutRegenerating() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(), FROM, TO, Locale.ENGLISH);

        // when
        String html = render(closing, null, null);

        // then
        assertThat(html).contains("No period has been closed yet").doesNotContain("Newest first");
        // the page explains itself through the "?" guide only; the second, duplicate explanation at the bottom is gone
        assertThat(html).doesNotContain("cl-ledger-how").doesNotContain("How does closing periods work?");
    }

    @Test
    void onlyTheNewestClosedPeriodsAreShownAndTheOlderOnesWaitBehindADisclosure() {
        // given
        List<StockLedgerPeriod> eightMonths = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(month -> new StockLedgerPeriod(LocalDate.of(2026, month, 1), LocalDate.of(2026, month, 1).plusMonths(1).minusDays(1)))
                .toList();
        StockLedgerClosingView closing = StockLedgerClosingView.of(eightMonths, FROM, TO, Locale.ENGLISH);

        // when
        String html = render(closing, null, null);

        // then
        String recent = html.substring(0, html.indexOf("cl-ledger-older"));
        String older = html.substring(html.indexOf("cl-ledger-older"));
        assertThat(recent).contains("August 2026").contains("March 2026").doesNotContain("February 2026");
        assertThat(older).contains("Older periods: 2").contains("February 2026").contains("January 2026")
                .contains("dateFrom=2026-01-01&amp;dateTo=2026-01-31");
    }

    @Test
    void sixClosedPeriodsNeedNoDisclosure() {
        // given
        List<StockLedgerPeriod> sixMonths = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(month -> new StockLedgerPeriod(LocalDate.of(2026, month, 1), LocalDate.of(2026, month, 1).plusMonths(1).minusDays(1)))
                .toList();

        // when
        String html = render(StockLedgerClosingView.of(sixMonths, FROM, TO, Locale.ENGLISH), null, null);

        // then
        assertThat(html).doesNotContain("cl-ledger-older").contains("January 2026").contains("June 2026");
    }

    @Test
    void theReportIsOrderedByAFormAndNoJobsYetSaysSo() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(AUGUST), FROM, TO, Locale.ENGLISH);

        // when
        String html = render(closing, null, null);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("action=\"/dashboard/reports/stock-ledger/export\"")
                .contains("name=\"exportFrom\" value=\"2026-09-01\"")
                .contains("No report is ready yet");
    }

    @Test
    void aGeneratedReportOffersItsFile() {
        // given
        StockLedgerClosingView closing = StockLedgerClosingView.of(List.of(AUGUST), FROM, TO, Locale.ENGLISH);
        GeneratedReportView report = new GeneratedReportView("Stock movements and levels", "1–30.09.2026", "14.10.2026 10:05",
                "/dashboard/reports/stock-ledger/generated?dateFrom=2026-09-01&dateTo=2026-09-30");

        // when
        String html = render(closing, null, null, null, List.of(report));

        // then
        assertThat(html).doesNotContain("??").contains("Stock movements and levels").contains("14.10.2026 10:05")
                .contains("href=\"/dashboard/reports/stock-ledger/generated?dateFrom=2026-09-01&amp;dateTo=2026-09-30\"")
                .doesNotContain("No report is ready yet");
    }
}
