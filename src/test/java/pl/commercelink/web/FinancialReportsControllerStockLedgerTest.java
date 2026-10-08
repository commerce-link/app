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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Closed;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.NotAllowed;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;
import pl.commercelink.warehouse.builtin.StockLedgerPeriodClosing;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialReportsControllerStockLedgerTest {

    private static final String STORE_ID = "store-1";
    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);
    private static final StockLedgerPeriod MARCH = new StockLedgerPeriod(FROM, TO);
    private static final String CLOSING_SECTION = "redirect:/dashboard/reports#stock-ledger-closing";

    @Mock
    private StockLedgerPeriodClosing stockLedgerPeriodClosing;
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
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(i -> i.getArgument(0));
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(supplierLabelMap);
        when(supplierLabelMap.of("Manual-Hurt")).thenReturn("Manual Hurt");
    }

    @AfterEach
    void closeSecurity() {
        security.close();
    }

    @Test
    void closedPeriodIsConfirmedOnTheClosingSection() throws Exception {
        // given
        when(stockLedgerPeriodClosing.close(STORE_ID, MARCH)).thenReturn(new Closed(MARCH, false, List.of()));

        // when
        String view = controller.closeStockLedgerPeriod(FROM, TO, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION);
        verify(redirectAttributes).addFlashAttribute("successMessage", "reports.stockLedger.closing.closed");
    }

    @Test
    void closingAClosedPeriodAgainIsConfirmedAsRegenerated() throws Exception {
        // given
        when(stockLedgerPeriodClosing.close(STORE_ID, MARCH)).thenReturn(new Closed(MARCH, true, List.of()));

        // when
        controller.closeStockLedgerPeriod(FROM, TO, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("successMessage", "reports.stockLedger.closing.regenerated");
    }

    @Test
    void regeneratedPeriodWithAChangedBalanceWarnsAboutTheLaterPeriods() throws Exception {
        // given
        StockLedgerPeriod april = new StockLedgerPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        when(stockLedgerPeriodClosing.close(STORE_ID, MARCH)).thenReturn(new Closed(MARCH, true, List.of(april)));

        // when
        controller.closeStockLedgerPeriod(FROM, TO, redirectAttributes, PL);

        // then
        verify(messageSource).getMessage(eq("reports.stockLedger.closing.outdated"), aryEq(new Object[]{MARCH.label(), april.label()}), eq(PL));
        verify(redirectAttributes).addFlashAttribute("ledgerOutdated", "reports.stockLedger.closing.outdated");
    }

    @Test
    void heldClosingListsTheDeliveriesAndKeepsTheHeldRange() throws Exception {
        // given
        Delivery delivery = new Delivery(STORE_ID, "ZS/1/2026", "Manual-Hurt");
        delivery.setInvoiced(true);
        delivery.setReceivedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        when(stockLedgerPeriodClosing.close(STORE_ID, MARCH)).thenReturn(new Blocked(List.of(delivery)));

        // when
        String view = controller.closeStockLedgerPeriod(FROM, TO, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "reports.stockLedger.closing.blocked");
        verify(redirectAttributes).addFlashAttribute("ledgerWarning", new StockLedgerClosingWarning(FROM, TO, List.of(
                new StockLedgerClosingBlocker(delivery.getDeliveryId(), delivery.getShortenedDeliveryId(), "Manual Hurt",
                        "ZS/1/2026", LocalDate.of(2026, 3, 15), "reports.stockLedger.closing.missing.sync"))));
    }

    @Test
    void gapBeforeThePeriodNamesTheDayTheNextPeriodStarts() throws Exception {
        // given
        LocalDate nextFrom = LocalDate.of(2026, 2, 1);
        when(stockLedgerPeriodClosing.close(STORE_ID, MARCH))
                .thenReturn(new NotAllowed("reports.stockLedger.closing.error.gap", List.of(nextFrom)));

        // when
        controller.closeStockLedgerPeriod(FROM, TO, redirectAttributes, PL);

        // then
        verify(messageSource).getMessage(eq("reports.stockLedger.closing.error.gap"), aryEq(new Object[]{MARCH.label(), nextFrom}), eq(PL));
        verify(redirectAttributes).addFlashAttribute("errorMessage", "reports.stockLedger.closing.error.gap");
    }

    @Test
    void refusedClosingShowsItsReason() throws Exception {
        // given
        when(stockLedgerPeriodClosing.close(STORE_ID, MARCH))
                .thenReturn(new NotAllowed("reports.stockLedger.closing.error.notOver"));

        // when
        controller.closeStockLedgerPeriod(FROM, TO, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "reports.stockLedger.closing.error.notOver");
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
}
