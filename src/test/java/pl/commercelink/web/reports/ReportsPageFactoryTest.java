package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import pl.commercelink.financials.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.warehouse.builtin.StockLedgerPeriodClosing;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportsPageFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock
    private FinancialReportGenerator generator;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private SupplierLabelMap labels;
    @Mock
    private StockLedgerPeriodClosing closing;
    @Mock
    private MessageSource messages;
    @InjectMocks
    private ReportsPageFactory factory;

    @Test
    void theDefaultPageIsThePreviousMonthWithFilesForThatRange() {
        // given
        FinancialReport empty = new FinancialReport(FROM, TO, 0, 0, 0, 0, Map.of(), 0, 0, 0);
        when(generator.generate("s1", FROM, TO)).thenReturn(new FinancialReports(empty, empty, List.of(), List.of()));
        when(supplierLabels.forStoreId("s1")).thenReturn(labels);
        when(closing.closedPeriods("s1")).thenReturn(List.of());

        // when
        ReportsPage page = factory.create("s1", null, null, TODAY, PL);

        // then
        assertThat(page.periodLabel()).isEqualTo("wrzesień 2026");
        assertThat(page.rangeText()).isEqualTo("1–30.09.2026");
        assertThat(page.previousHref()).isEqualTo("/dashboard/reports?dateFrom=2026-08-01&dateTo=2026-08-31");
        assertThat(page.nextHref()).isEqualTo("/dashboard/reports?dateFrom=2026-10-01&dateTo=2026-10-31");
        assertThat(page.presets()).filteredOn(ReportsPage.PresetLink::current).extracting(ReportsPage.PresetLink::labelKey)
                .containsExactly("reports.period.preset.previousMonth");
        assertThat(page.files()).hasSize(2);
        assertThat(page.files().get(0).files()).extracting(ReportsPage.FileLink::href)
                .contains("/dashboard/reports/ordersExport?dateFrom=2026-09-01&dateTo=2026-09-30");
        assertThat(page.charts()).extracting(DailyChart::metric)
                .containsExactly(DailyChart.Metric.SALES, DailyChart.Metric.PROFIT, DailyChart.Metric.ORDERS);
        assertThat(page.channels().empty()).isTrue();
    }

    @Test
    void thereIsNoNextPeriodOnceItWouldStartAfterToday() {
        // given
        LocalDate october = LocalDate.of(2026, 10, 1);
        FinancialReport empty = new FinancialReport(october, october.plusDays(30), 0, 0, 0, 0, Map.of(), 0, 0, 0);
        when(generator.generate(eq("s1"), any(), any())).thenReturn(new FinancialReports(empty, empty, List.of(), List.of()));
        when(supplierLabels.forStoreId("s1")).thenReturn(labels);
        when(closing.closedPeriods("s1")).thenReturn(List.of());

        // when
        ReportsPage page = factory.create("s1", october, october.plusDays(30), TODAY, PL);

        // then
        assertThat(page.nextHref()).isNull();
    }

    @Test
    void everyFileIsOfferedForThePeriodOfThePage() {
        // given
        FinancialReport empty = new FinancialReport(FROM, TO, 0, 0, 0, 0, Map.of(), 0, 0, 0);
        when(generator.generate("s1", FROM, TO)).thenReturn(new FinancialReports(empty, empty, List.of(), List.of()));
        when(supplierLabels.forStoreId("s1")).thenReturn(labels);
        when(closing.closedPeriods("s1")).thenReturn(List.of());

        // when
        ReportsPage page = factory.create("s1", FROM, TO, TODAY, PL);

        // then
        List<String> hrefs = page.files().stream().flatMap(group -> group.files().stream()).map(ReportsPage.FileLink::href).toList();
        assertThat(hrefs).hasSize(6).allMatch(href -> href.endsWith("?dateFrom=2026-09-01&dateTo=2026-09-30"));
        assertThat(hrefs).extracting(href -> href.substring(0, href.indexOf('?'))).doesNotHaveDuplicates();
        assertThat(page.files()).extracting(ReportsPage.FileGroup::titleKey)
                .containsExactly("reports.files.group.sales", "reports.files.group.purchases");
    }

    @Test
    void aMistypedYearIsShortenedToTheLongestReportAndThePageSaysSo() {
        // given
        LocalDate shortenedFrom = LocalDate.of(2021, 10, 1);
        FinancialReport empty = new FinancialReport(shortenedFrom, TO, 0, 0, 0, 0, Map.of(), 0, 0, 0);
        when(generator.generate("s1", shortenedFrom, TO)).thenReturn(new FinancialReports(empty, empty, List.of(), List.of()));
        when(supplierLabels.forStoreId("s1")).thenReturn(labels);
        when(closing.closedPeriods("s1")).thenReturn(List.of());

        // when
        ReportsPage page = factory.create("s1", LocalDate.of(202, 9, 1), TO, TODAY, PL);

        // then
        assertThat(page.shortened()).isTrue();
        assertThat(page.dateFrom()).isEqualTo("2021-10-01");
        assertThat(page.dateMin()).isEqualTo("2000-01-01");
        assertThat(page.dateMax()).isEqualTo("2027-12-31");
    }
}
