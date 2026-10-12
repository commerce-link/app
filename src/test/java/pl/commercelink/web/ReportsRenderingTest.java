package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.financials.FinancialReport;
import pl.commercelink.financials.SupplierDeliveries;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.web.reports.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportsRenderingTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    private static ReportsPage page(boolean pending, int ownOrders) {
        FinancialReport own = new FinancialReport(FROM, TO, ownOrders, 254_162.49, 215_438.97, 38_723.66, Map.of(), 0, 0, 0);
        FinancialReport marketplace = new FinancialReport(FROM, TO, 324, 554_680.10, 467_641.91, 87_038.44, Map.of(), 0, 0, 0);
        List<SalesTimeline.Bucket> buckets = List.of(
                new SalesTimeline.Bucket(FROM, FROM, 1000, 2000, 100, 200, 1, 2),
                new SalesTimeline.Bucket(FROM.plusDays(1), FROM.plusDays(1), 0, 0, 0, 0, 0, 0));
        SupplierLabelMap labels = mock(SupplierLabelMap.class);
        when(labels.of("Action")).thenReturn("Action");
        return page(pending, own, marketplace, buckets,
                SupplierCosts.of(List.of(new SupplierDeliveries("Action", 21, 290.9, 46.9), new SupplierDeliveries(null, 1, 5, 0)), labels));
    }

    private static ReportsPage page(boolean pending, FinancialReport own, FinancialReport marketplace,
                                    List<SalesTimeline.Bucket> buckets, SupplierCosts suppliers) {
        ReportPeriod period = new ReportPeriod(FROM, TO);
        return new ReportsPage("wrzesień 2026", "1–30.09.2026",
                "/dashboard/reports?dateFrom=2026-08-01&dateTo=2026-08-31", "sierpień 2026", null, null,
                List.of(new ReportsPage.PresetLink("reports.period.preset.previousMonth", "/dashboard/reports?" + period.query(), true)),
                "2026-09-01", "2026-09-30", "/dashboard/reports/stock-ledger?from=2026-09-01&to=2026-09-30",
                List.of(new ReportsPage.FileGroup("reports.files.group.sales", List.of(
                        new ReportsPage.FileLink("reports.files.orders", "reports.files.orders.desc",
                                ReportFile.ORDERS.href(period))))),
                ChannelComparison.of(own, marketplace),
                List.of(DailyChart.of(DailyChart.Metric.SALES, buckets, SalesTimeline.Granularity.DAY, Locale.ENGLISH),
                        DailyChart.of(DailyChart.Metric.PROFIT, buckets, SalesTimeline.Granularity.DAY, Locale.ENGLISH),
                        DailyChart.of(DailyChart.Metric.ORDERS, buckets, SalesTimeline.Granularity.DAY, Locale.ENGLISH)),
                suppliers,
                new StockLedgerClosingShortcut(pending, pending ? "September" : null, "August 2026"));
    }

    private static String render(ReportsPage page) {
        return render(page, Locale.ENGLISH);
    }

    private static String render(ReportsPage page, Locale locale) {
        Context context = new Context(locale);
        context.setVariable("page", page);
        return EnglishFragmentTemplateEngine.create(locale).process("<div th:replace=\"~{reports :: body}\"></div>", context);
    }

    @Test
    void thePageHasOneHeadingTheActionsAndTheFilesOfItsRange() {
        // when
        String html = render(page(true, 141));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("href=\"/dashboard/reports/ordersExport?dateFrom=2026-09-01&amp;dateTo=2026-09-30\"")
                .containsPattern("<a[^>]*class=\"cl-menu-item\"[^>]*download");
        assertThat(html).contains("Close September").contains("cl-dot is-warn");
        assertThat(html).contains("href=\"/dashboard/reports/stock-ledger?from=2026-09-01&amp;to=2026-09-30\"");
    }

    @Test
    void withNothingToCloseTheButtonIsPlainAndThereIsNoNextPeriodLink() {
        // when
        String html = render(page(false, 141));

        // then
        assertThat(html).contains("Stock period closing").doesNotContain("cl-dot");
        assertThat(html).containsPattern("<a[^>]*aria-disabled=\"true\"[^>]*>\\s*<span[^>]*>\\s*<i class=\"fas fa-chevron-right\"");
    }

    @Test
    void chartsAreTextWithHeightsAsDataAndATableToRead() {
        // when
        String html = render(page(true, 141));

        // then
        assertThat(html).containsPattern("class=\"cl-chart-col[^\"]*\" tabindex=\"0\"");
        assertThat(html).containsPattern("style=\"height:[0-9.]+%\"");
        assertThat(html).contains("data-cl-chart-panel=\"sales\"").contains("data-cl-chart-panel=\"profit\"")
                .contains("data-cl-chart-panel=\"orders\"");
        assertThat(html).contains("Show as a table");
        assertThat(html).contains("No supplier");
        assertThat(html).doesNotContain("new Chart");
    }

    @Test
    void moneyRowsOfTheComparisonCarryTheCurrencyAndCountsDoNot() {
        // when
        String html = render(page(true, 141));

        // then
        assertThat(html).containsPattern("cl-compare-val is-own\">254[^<]162 PLN<");
        assertThat(html).containsPattern("cl-compare-val is-own\">141<");
    }

    @Test
    void aMissingChannelIsSaid() {
        // when
        String html = render(page(true, 0));

        // then
        assertThat(html).contains("No sales in the store in this period");
        assertThat(html).contains("cl-compare-val is-own\">—<");
    }

    @Test
    void anEmptyPeriodSaysSoInsteadOfDrawingEmptyCharts() {
        // given
        FinancialReport none = new FinancialReport(FROM, TO, 0, 0, 0, 0, Map.of(), 0, 0, 0);
        List<SalesTimeline.Bucket> zeros = List.of(
                new SalesTimeline.Bucket(FROM, FROM, 0, 0, 0, 0, 0, 0),
                new SalesTimeline.Bucket(FROM.plusDays(1), FROM.plusDays(1), 0, 0, 0, 0, 0, 0));
        ReportsPage empty = page(false, none, none, zeros, SupplierCosts.of(List.of(), mock(SupplierLabelMap.class)));

        // when
        String html = render(empty);

        // then
        assertThat(html).contains("No orders in this period.").contains("No deliveries ordered in this period.");
        assertThat(html).doesNotContain("??").doesNotContain("NaN");
    }

    @Test
    void theMarketplaceSeriesIsVioletWhileTheSupplierPaletteKeepsItsColours() throws Exception {
        // when
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains("--cl-chart-1: var(--cl-supplier-1);").contains("--cl-chart-2: #7c3aed;")
                .contains("--cl-supplier-2: #9a3f0f;");
    }

    @Test
    void layoutNoLongerLoadsChartJs() throws Exception {
        // then
        assertThat(Files.readString(Path.of("src/main/resources/templates/layout.html"))).doesNotContain("chart.js");
        try (var files = Files.walk(Path.of("src/main"))) {
            assertThat(files.filter(p -> p.toString().endsWith(".html") || p.toString().endsWith(".js"))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains("new Chart(");
                        } catch (Exception e) {
                            return false;
                        }
                    })).isFalse();
        }
    }

    @Test
    void anEmptyChannelHasNoSegmentInTheShareBars() {
        // given
        FinancialReport none = new FinancialReport(FROM, TO, 0, 0, 0, 0, Map.of(), 0, 0, 0);
        FinancialReport marketplace = new FinancialReport(FROM, TO, 324, 554_680.10, 467_641.91, 87_038.44, Map.of(), 0, 0, 0);
        List<SalesTimeline.Bucket> buckets = List.of(new SalesTimeline.Bucket(FROM, FROM, 0, 2000, 0, 200, 0, 2));

        // when
        String html = render(page(false, none, marketplace, buckets, SupplierCosts.of(List.of(), mock(SupplierLabelMap.class))));

        // then
        assertThat(html).contains("cl-hbar-seg is-c2").doesNotContain("cl-hbar-seg is-c1");
    }

    @Test
    void moneyInTheChartTooltipLabelAndTableCarriesTheCurrencyAndOrdersDoNot() {
        // when
        String html = render(page(true, 141));

        // then
        assertThat(html).containsPattern("<strong>1[^<]000,00 PLN</strong>");
        assertThat(html).containsPattern("aria-label=\"1\\.09: store 1[^\"]000,00 PLN, marketplace 2[^\"]000,00 PLN\"");
        assertThat(html).containsPattern("data-label=\"Store\">1[^<]000,00 PLN<");
        assertThat(html).containsPattern("aria-label=\"1\\.09: store 1, marketplace 2\"");
    }

    @Test
    void aPeriodWithOrdersButZeroNetValueStillDrawsTheChartPanels() {
        // given
        FinancialReport own = new FinancialReport(FROM, TO, 3, 0, 0, 0, Map.of(), 0, 0, 0);
        FinancialReport marketplace = new FinancialReport(FROM, TO, 0, 0, 0, 0, Map.of(), 0, 0, 0);
        List<SalesTimeline.Bucket> buckets = List.of(new SalesTimeline.Bucket(FROM, FROM, 0, 0, 0, 0, 3, 0));

        // when
        String html = render(page(false, own, marketplace, buckets, SupplierCosts.of(List.of(), mock(SupplierLabelMap.class))));

        // then
        assertThat(html).contains("data-cl-chart-panel=\"sales\"").contains("data-cl-chart-panel=\"orders\"");
        assertThat(html).doesNotContain("No orders in this period.");
    }

    @Test
    void thePolishPageResolvesEveryMessage() {
        // when
        String html = render(page(true, 141), Locale.forLanguageTag("pl"));

        // then
        assertThat(html).doesNotContain("??").contains("Raporty");
    }
}
