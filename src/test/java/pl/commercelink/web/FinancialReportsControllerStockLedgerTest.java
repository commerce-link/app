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
import pl.commercelink.warehouse.builtin.StockLedgerMonthClosing;
import pl.commercelink.web.reports.StockLedgerClosingBlocker;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialReportsControllerStockLedgerTest {

    private static final String STORE_ID = "store-1";
    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final String CLOSING_SECTION = "redirect:/dashboard/reports#stock-ledger-closing";

    @Mock
    private StockLedgerMonthClosing stockLedgerMonthClosing;
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
    void closedMonthIsConfirmedOnTheClosingSection() throws Exception {
        // given
        when(stockLedgerMonthClosing.close(STORE_ID, SEPTEMBER)).thenReturn(new Closed(SEPTEMBER));

        // when
        String view = controller.closeStockLedgerMonth("2026-09", redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION);
        verify(redirectAttributes).addFlashAttribute("successMessage", "reports.stockLedger.closing.closed");
    }

    @Test
    void heldClosingListsTheDeliveriesToComplete() throws Exception {
        // given
        Delivery delivery = new Delivery(STORE_ID, "ZS/1/2026", "Manual-Hurt");
        delivery.setInvoiced(true);
        delivery.setReceivedAt(LocalDateTime.of(2026, 9, 15, 10, 0));
        when(stockLedgerMonthClosing.close(STORE_ID, SEPTEMBER)).thenReturn(new Blocked(List.of(delivery)));

        // when
        String view = controller.closeStockLedgerMonth("2026-09", redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "reports.stockLedger.closing.blocked");
        verify(redirectAttributes).addFlashAttribute("ledgerBlockers", List.of(new StockLedgerClosingBlocker(
                delivery.getDeliveryId(), delivery.getShortenedDeliveryId(), "Manual Hurt", "ZS/1/2026",
                delivery.getReceivedAt().toLocalDate(), "reports.stockLedger.closing.missing.sync")));
    }

    @Test
    void refusedClosingShowsItsReason() throws Exception {
        // given
        when(stockLedgerMonthClosing.close(STORE_ID, SEPTEMBER)).thenReturn(new NotAllowed("reports.stockLedger.closing.error.notNext"));

        // when
        controller.closeStockLedgerMonth("2026-09", redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "reports.stockLedger.closing.error.notNext");
    }

    @Test
    void monthThatIsNotAMonthIsNotFound() {
        // when / then
        assertThatThrownBy(() -> controller.closeStockLedgerMonth("september", redirectAttributes, PL))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void regeneratedMonthIsConfirmedOnTheClosingSection() throws Exception {
        // given
        YearMonth august = YearMonth.of(2026, 8);
        when(stockLedgerMonthClosing.regenerate(STORE_ID, august)).thenReturn(new Closed(august));

        // when
        String view = controller.regenerateStockLedgerMonth("2026-08", redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo(CLOSING_SECTION);
        verify(redirectAttributes).addFlashAttribute("successMessage", "reports.stockLedger.closing.regenerated");
    }

    @Test
    void heldRegenerationListsTheDeliveriesToComplete() throws Exception {
        // given
        Delivery delivery = new Delivery(STORE_ID, "ZS/1/2026", "Manual-Hurt");
        when(stockLedgerMonthClosing.regenerate(STORE_ID, SEPTEMBER)).thenReturn(new Blocked(List.of(delivery)));

        // when
        controller.regenerateStockLedgerMonth("2026-09", redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "reports.stockLedger.closing.regenerateBlocked");
        verify(redirectAttributes).addFlashAttribute(eq("ledgerBlockers"), any());
    }

    @Test
    void closedMonthReportIsDownloadedWithTheBomAndTheMonthInItsName() throws Exception {
        // given
        when(stockLedgerMonthClosing.closedReport(STORE_ID, SEPTEMBER)).thenReturn(Optional.of("x".getBytes()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.closedStockLedger("2026-09", response);

        // then
        assertThat(response.getHeader("Content-Disposition")).contains("stock-ledger-2026-09-01_2026-09-30.csv");
        assertThat(response.getContentAsByteArray()).containsExactly(0xEF, 0xBB, 0xBF, 'x');
    }
}
