package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.reports.GeneratedReports;
import pl.commercelink.reports.ReportGenerationPublisher;
import pl.commercelink.reports.ReportType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.NotAllowed;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;
import pl.commercelink.warehouse.builtin.StockLedgerPeriodClosing;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.reports.ReportPeriod;
import pl.commercelink.web.reports.StockLedgerClosingBlocker;
import pl.commercelink.web.reports.StockLedgerClosingWarning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialReportsControllerStockLedgerTest {

    private static final String STORE_ID = "store-1";
    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);
    private static final StockLedgerPeriod MARCH = new StockLedgerPeriod(FROM, TO);
    private static final String LABEL_MARCH = "marzec 2026";
    private static final String CLOSING_SECTION = "redirect:/dashboard/reports/stock-ledger";

    @Mock
    private StockLedgerPeriodClosing stockLedgerPeriodClosing;
    @Mock
    private ReportGenerationPublisher reportGenerationPublisher;
    @Mock
    private GeneratedReports generatedReports;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private SupplierLabelMap supplierLabelMap;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;
    @InjectMocks
    private FinancialReportsController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void stubSecurityAndMessages() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        when(stockLedgerPeriodClosing.check(any(), any())).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(i -> i.getArgument(0));
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(supplierLabelMap);
        when(supplierLabelMap.of("Manual-Hurt")).thenReturn("Manual Hurt");
    }

    @AfterEach
    void closeSecurity() {
        security.close();
    }

    @Test
    void theBackLinkOfTheClosingPageReturnsToTheReportWithItsPeriod() {
        // given
        Model model = new ExtendedModelMap();

        // when
        controller.stockLedger(model, PL, "2026-03-01", "2026-03-31");

        // then
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/reports?dateFrom=2026-03-01&dateTo=2026-03-31");
    }

    @Test
    void theBackLinkFallsBackToTheReportWhenThePeriodIsMissingOrUnreadable() {
        // given
        Model withoutParams = new ExtendedModelMap();
        Model unreadable = new ExtendedModelMap();

        // when
        controller.stockLedger(withoutParams, PL, null, null);
        controller.stockLedger(unreadable, PL, "yesterday", "2026-03-31");

        // then
        assertThat(withoutParams.getAttribute("backHref")).isEqualTo("/dashboard/reports");
        assertThat(unreadable.getAttribute("backHref")).isEqualTo("/dashboard/reports");
    }

    @Test
    void heldClosingListsTheDeliveriesAndKeepsTheHeldRange() throws Exception {
        // given
        Delivery delivery = new Delivery(STORE_ID, "ZS/1/2026", "Manual-Hurt");
        delivery.setInvoiced(true);
        delivery.setReceivedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        when(stockLedgerPeriodClosing.check(STORE_ID, MARCH)).thenReturn(Optional.of(new Blocked(List.of(delivery))));

        // when
        String view = controller.closeStockLedgerPeriod(FROM, TO, null, null, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION);
        verify(redirectAttributes).addFlashAttribute("ledgerError", "reports.stockLedger.closing.blocked");
        verify(redirectAttributes).addFlashAttribute("ledgerWarning", new StockLedgerClosingWarning(FROM, TO, List.of(
                new StockLedgerClosingBlocker(delivery.getDeliveryId(), delivery.getShortenedDeliveryId(), "Manual Hurt",
                        "ZS/1/2026", LocalDate.of(2026, 3, 15), "reports.stockLedger.closing.missing.sync"))));
    }

    @Test
    void gapBeforeThePeriodNamesTheDayTheNextPeriodStartsAsThePageWritesDays() throws Exception {
        // given
        LocalDate nextFrom = LocalDate.of(2026, 2, 1);
        when(stockLedgerPeriodClosing.check(STORE_ID, MARCH))
                .thenReturn(Optional.of(new NotAllowed("reports.stockLedger.closing.error.gap", List.of(nextFrom))));

        // when
        controller.closeStockLedgerPeriod(FROM, TO, null, null, redirectAttributes, PL);

        // then
        verify(messageSource).getMessage(eq("reports.stockLedger.closing.error.gap"), aryEq(new Object[]{LABEL_MARCH, "1.02.2026"}), eq(PL));
        verify(redirectAttributes).addFlashAttribute("ledgerError", "reports.stockLedger.closing.error.gap");
    }

    @Test
    void refusedClosingShowsItsReason() throws Exception {
        // given
        when(stockLedgerPeriodClosing.check(STORE_ID, MARCH))
                .thenReturn(Optional.of(new NotAllowed("reports.stockLedger.closing.error.notOver")));

        // when
        controller.closeStockLedgerPeriod(FROM, TO, null, null, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("ledgerError", "reports.stockLedger.closing.error.notOver");
    }

    @Test
    void closedPeriodReportIsDownloadedWithTheBomAndThePeriodInItsName() throws Exception {
        // given
        when(stockLedgerPeriodClosing.closedReport(STORE_ID, MARCH)).thenReturn(Optional.of("x".getBytes()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.closedStockLedger(FROM, TO, response);

        // then
        assertThat(response.getHeader("Content-Disposition")).contains("stock-ledger-2026-03-01_2026-03-31.csv");
        assertThat(response.getContentAsByteArray()).containsExactly(0xEF, 0xBB, 0xBF, 'x');
    }

    @Test
    void periodThatIsNotClosedHasNoReportToDownload() {
        // given
        when(stockLedgerPeriodClosing.closedReport(STORE_ID, MARCH)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> controller.closedStockLedger(FROM, TO, new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theConfirmationPageClosesNothing() throws Exception {
        // given
        Model model = new ExtendedModelMap();
        when(stockLedgerPeriodClosing.closedPeriods(STORE_ID)).thenReturn(List.of(MARCH));

        // when
        String view = controller.confirmStockLedgerRegeneration(FROM, TO, null, null, model, PL);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/reports/stock-ledger/close?dateFrom=" + FROM + "&dateTo=" + TO);
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/reports/stock-ledger");
        assertThat(confirm.destructive()).isFalse();
        verify(stockLedgerPeriodClosing, never()).close(any(), any());
    }

    @Test
    void theConfirmationPageCarriesTheReportPeriodToTheActionAndBack() throws Exception {
        // given
        Model model = new ExtendedModelMap();
        when(stockLedgerPeriodClosing.closedPeriods(STORE_ID)).thenReturn(List.of(MARCH));

        // when
        controller.confirmStockLedgerRegeneration(FROM, TO, "2026-09-01", "2026-09-30", model, PL);

        // then
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/reports/stock-ledger/close?dateFrom=2026-03-01&dateTo=2026-03-31"
                + "&from=2026-09-01&to=2026-09-30");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/reports/stock-ledger?from=2026-09-01&to=2026-09-30");
    }

    @Test
    void aRangeThatIsNotAClosedPeriodHasNothingToGenerateAgain() {
        // given
        Model model = new ExtendedModelMap();
        when(stockLedgerPeriodClosing.closedPeriods(STORE_ID)).thenReturn(List.of());

        // when
        String view = controller.confirmStockLedgerRegeneration(FROM, TO, "2026-09-01", "2026-09-30", model, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/reports/stock-ledger?from=2026-09-01&to=2026-09-30");
        assertThat(model.getAttribute("confirm")).isNull();
    }

    @Test
    void afterClosingThePageStillReturnsToTheReportItWasOpenedFrom() throws Exception {
        // given

        // when
        String view = controller.closeStockLedgerPeriod(FROM, TO, "2026-09-01", "2026-09-30", redirectAttributes, PL);
        String unreadable = controller.closeStockLedgerPeriod(FROM, TO, "//evil.example", "x", redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION + "?from=2026-09-01&to=2026-09-30");
        assertThat(unreadable).isEqualTo(CLOSING_SECTION);
    }

    @Test
    void theClosingPageKeepsTheReportPeriodForItsForms() {
        // given
        Model model = new ExtendedModelMap();

        // when
        controller.stockLedger(model, PL, "2026-09-01", "2026-09-30");

        // then
        assertThat(model.getAttribute("report")).isEqualTo(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void anAllowedClosingIsSentToTheQueueAndAcceptedAtOnce() {
        // given

        // when
        String view = controller.closeStockLedgerPeriod(FROM, TO, null, null, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION);
        verify(reportGenerationPublisher).order(STORE_ID, ReportType.STOCK_LEDGER_CLOSING, FROM, TO);
        verify(redirectAttributes).addFlashAttribute("ledgerSuccess", "reports.stockLedger.closing.accepted");
    }

    @Test
    void aRefusedClosingSendsNothingToTheQueue() {
        // given
        when(stockLedgerPeriodClosing.check(STORE_ID, MARCH))
                .thenReturn(Optional.of(new NotAllowed("reports.stockLedger.closing.error.notOver")));

        // when
        controller.closeStockLedgerPeriod(FROM, TO, null, null, redirectAttributes, PL);

        // then
        verify(reportGenerationPublisher, never()).order(any(), any(), any(), any());
    }

    @Test
    void theStockLedgerReportIsOrderedFromTheQueueForTheChosenRange() {
        // given

        // when
        String view = controller.orderStockLedgerExport(FROM, TO, "2026-09-01", "2026-09-30", redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION + "?from=2026-09-01&to=2026-09-30");
        verify(reportGenerationPublisher).order(STORE_ID, ReportType.STOCK_LEDGER_EXPORT, FROM, TO);
        verify(redirectAttributes).addFlashAttribute("ledgerSuccess", "reports.generated.accepted");
    }

    @Test
    void aReportOfAnInvertedRangeIsNotOrdered() {
        // given

        // when
        controller.orderStockLedgerExport(TO, FROM, null, null, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("ledgerError", "reports.stockLedger.closing.error.range");
        verify(reportGenerationPublisher, never()).order(any(), any(), any(), any());
    }

    @Test
    void aGeneratedReportIsDownloadedWithTheBomAndThePeriodInItsName() throws Exception {
        // given
        when(generatedReports.find(STORE_ID, ReportType.STOCK_LEDGER_EXPORT, FROM, TO)).thenReturn(Optional.of("x".getBytes()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.generatedStockLedger(FROM, TO, response);

        // then
        assertThat(response.getHeader("Content-Disposition")).contains("stock-ledger-2026-03-01_2026-03-31.csv");
        assertThat(response.getContentAsByteArray()).containsExactly(0xEF, 0xBB, 0xBF, 'x');
    }

    @Test
    void aReportThatIsNotGeneratedHasNothingToDownload() {
        // given
        when(generatedReports.find(STORE_ID, ReportType.STOCK_LEDGER_EXPORT, FROM, TO)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> controller.generatedStockLedger(FROM, TO, new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void thePageListsTheGeneratedReportsOfTheStore() {
        // given
        Model model = new ExtendedModelMap();
        when(generatedReports.recent(STORE_ID)).thenReturn(List.of(
                new GeneratedReports.Entry(ReportType.STOCK_LEDGER_EXPORT, FROM, TO, java.time.Instant.parse("2026-10-14T08:00:00Z"))));

        // when
        controller.stockLedger(model, PL, null, null);

        // then
        assertThat((List<?>) model.getAttribute("generatedReports")).hasSize(1);
    }
}
