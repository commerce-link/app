package pl.commercelink.web.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.*;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.payments.PaymentEntriesTest.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentsModelFactoryTest {

    @Mock DeliveriesRepository deliveries;
    @Mock OrdersRepository orders;
    @Mock StoresRepository stores;
    @Mock SupplierLabels supplierLabels;
    @Mock SupplierLabelMap labels;
    @Mock Store store;
    PaymentsModelFactory factory;

    final List<Delivery> unpaid = new ArrayList<>();
    final List<Order> open = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        factory = new PaymentsModelFactory(deliveries, orders, stores, supplierLabels, messages);
        when(deliveries.findUnpaidDeliveries("store-1")).thenReturn(unpaid);
        when(orders.findByStoreAndStatuses(eq("store-1"), any())).thenReturn(open);
        when(stores.findById("store-1")).thenReturn(store);
        when(supplierLabels.forStoreId("store-1")).thenReturn(labels);
        when(labels.of(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(labels.has(anyString(), anyString())).thenReturn(true);
    }

    private PaymentsPageModel page(String... pairs) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) params.add(pairs[i], pairs[i + 1]);
        return factory.page("store-1", PaymentsQuery.parse(params), TODAY, new Locale("pl"));
    }

    private static PaymentsPageModel.Tile overpaidTile(PaymentsPageModel page) {
        return page.tiles().get(PaymentFocus.REFUND.ordinal());
    }

    private static Delivery withProvider(Delivery d, String id, String provider) {
        d.setDeliveryId(id + "-0000-0000-0000-000000000000");
        d.setProvider(provider);
        return d;
    }

    @Test
    void tilesCountBothSidesAndNarrowBothTabs() {
        // given: one overdue delivery, one shipped unpaid order, one order shipping later
        unpaid.add(withProvider(delivery(100, TODAY.minusDays(20), 14), "aaaa0001", "Acme"));
        unpaid.add(withProvider(delivery(100, TODAY, 14), "aaaa0002", "Acme"));
        open.add(order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0));
        Order later = order(500, OrderStatus.New, PaymentSource.BankTransfer, 0);
        later.setOrderId("cccc0001-0000-0000-0000-000000000000");
        later.setEstimatedShippingAt(TODAY.plusDays(9));
        open.add(later);

        // when
        PaymentsPageModel all = page();
        PaymentsPageModel overdue = page("focus", "overdue");

        // then
        PaymentsPageModel.Tile tile = all.tiles().get(0);
        assertThat(tile.count()).isEqualTo(2);
        assertThat(tile.payablesHint()).isEqualTo("dostawy: 1");
        assertThat(tile.receivablesHint()).isEqualTo("zamówienia: 1");
        assertThat(overdue.tabs()).extracting(PaymentsPageModel.SideTab::count).containsExactly(1L, 1L);
        assertThat(overdue.payables()).extracting(PayableRow::number).containsExactly("aaaa0001");
        assertThat(all.tabs()).extracting(PaymentsPageModel.SideTab::count).containsExactly(2L, 2L);
    }

    @Test
    void defaultSideIsPayablesUnlessOnlyReceivablesHaveResults() {
        // given
        open.add(order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0));

        // when / then
        assertThat(page().side()).isEqualTo(PaymentSide.RECEIVABLES);
        unpaid.add(withProvider(delivery(100, TODAY, 14), "aaaa0001", "Acme"));
        assertThat(page().side()).isEqualTo(PaymentSide.PAYABLES);
        assertThat(page("side", "receivables").side()).isEqualTo(PaymentSide.RECEIVABLES);
    }

    @Test
    void payablesWithoutDueDateGoLastAndRefundsAfterThem() {
        // given
        Delivery refund = withProvider(delivery(100, TODAY.minusDays(40), 0), "aaaa0001", "Acme");
        refund.addPayment(paid(130));
        unpaid.add(refund);
        unpaid.add(withProvider(delivery(100, null, 0), "aaaa0002", "Acme"));
        unpaid.add(withProvider(delivery(100, TODAY.minusDays(3), 30), "aaaa0003", "Acme"));
        unpaid.add(withProvider(delivery(100, TODAY.minusDays(30), 14), "aaaa0004", "Acme"));

        // when
        PaymentsPageModel page = page();

        // then
        assertThat(page.payables()).extracting(PayableRow::number).containsExactly("aaaa0004", "aaaa0003", "aaaa0002", "aaaa0001");
    }

    @Test
    void resultsLineSumsGrossNetAndRefundsSeparately() {
        // given
        unpaid.add(withProvider(delivery(100, TODAY, 14), "aaaa0001", "Acme"));
        unpaid.add(withProvider(delivery(50, TODAY, 14), "aaaa0002", "Acme"));
        Delivery refund = withProvider(delivery(100, TODAY, 14), "aaaa0003", "Acme");
        refund.addPayment(paid(120));
        unpaid.add(refund);

        // when
        PaymentsPageModel page = page();

        // then
        assertThat(page.resultsCount()).isEqualTo("Dostawy: 3 · do zapłaty");
        assertThat(page.resultsAmount()).isEqualTo("150,00\u00A0PLN");
        assertThat(page.resultsTail()).isEqualTo("brutto (150,00\u00A0PLN netto) · nadpłaty u dostawców 20,00\u00A0PLN");
    }

    @Test
    void receivablesAreOrderedByUrgencyThenShipDateThenRefundsLast() {
        // given: one receivable per rank, added in a shuffled order
        open.add(receivable("e0000006", 100, OrderStatus.New, PaymentSource.BankTransfer, 150, null));
        open.add(receivable("e0000004", 100, OrderStatus.New, PaymentSource.BankTransfer, 0, null));
        open.add(receivable("e0000005", 100, OrderStatus.Realization, PaymentSource.CashOnDelivery, 0, null));
        open.add(receivable("e0000003", 100, OrderStatus.New, PaymentSource.BankTransfer, 0, TODAY.plusDays(9)));
        open.add(receivable("e0000001", 100, OrderStatus.Shipping, PaymentSource.BankTransfer, 0, null));
        open.add(receivable("e0000002", 100, OrderStatus.New, PaymentSource.BankTransfer, 0, TODAY));

        // when
        PaymentsPageModel page = page("side", "receivables");

        // then: shipped unpaid, ship today, dated, undated (last within its rank), COD in transit, refund
        assertThat(page.receivables()).extracting(ReceivableRow::number)
                .containsExactly("e0000001", "e0000002", "e0000003", "e0000004", "e0000005", "e0000006");
    }

    private static Order receivable(String id, double total, OrderStatus status, PaymentSource method, double paid, LocalDate ship) {
        Order o = order(total, status, method, paid);
        o.setOrderId(id + "-0000-0000-0000-000000000000");
        o.setEstimatedShippingAt(ship);
        return o;
    }

    @Test
    void overpaidTileIsRedOnlyWhenItCountsSomething() {
        // given
        PaymentsPageModel.Tile emptyTile = overpaidTile(page());
        Delivery overpaid = withProvider(delivery(100, TODAY, 14), "aaaa0001", "Acme");
        overpaid.addPayment(paid(120));
        unpaid.add(overpaid);

        // when
        PaymentsPageModel.Tile tile = overpaidTile(page());

        // then
        assertThat(tile.label()).isEqualTo("Nadpłaty");
        assertThat(tile.count()).isEqualTo(1);
        assertThat(tile.tone()).isEqualTo("is-bad");
        assertThat(emptyTile.tone()).as("a red zero would be a false alarm").isEmpty();
        assertThat(page().tiles()).filteredOn(t -> !t.label().equals("Nadpłaty"))
                .extracting(PaymentsPageModel.Tile::tone).containsOnly("");
    }

    @Test
    void switchingATileOffKeepsTheShownSide() {
        // given
        unpaid.add(withProvider(delivery(100, TODAY.minusDays(20), 14), "aaaa0001", "Acme"));
        open.add(order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0));

        // when
        PaymentsPageModel page = page("side", "receivables", "focus", "overdue");

        // then
        assertThat(page.tiles().stream().filter(PaymentsPageModel.Tile::active).findFirst().orElseThrow().href())
                .isEqualTo("/dashboard/payments?side=receivables");
    }

    @Test
    void providerFilterDoesNotNarrowReceivables() {
        // given
        unpaid.add(withProvider(delivery(100, TODAY, 14), "aaaa0001", "Acme"));
        unpaid.add(withProvider(delivery(100, TODAY, 14), "aaaa0002", "Elko"));
        open.add(order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0));

        // when
        PaymentsPageModel page = page("side", "payables", "provider", "Elko");

        // then
        assertThat(page.payables()).extracting(PayableRow::number).containsExactly("aaaa0002");
        assertThat(page.tabs()).extracting(PaymentsPageModel.SideTab::count).containsExactly(1L, 1L);
        assertThat(page.menuOptions()).extracting(PaymentsPageModel.Option::value).containsExactly("Acme", "Elko");
        assertThat(page.chips()).extracting(PaymentsPageModel.Chip::label).containsExactly("Dostawca: Elko");
        assertThat(page.tabs().get(1).href()).isEqualTo("/dashboard/payments?side=receivables");
    }

    @Test
    void searchFindsDeliveryNumbersAndCustomers() {
        // given
        Delivery d = withProvider(delivery(100, TODAY, 14), "aaaa0001", "Acme");
        unpaid.add(d);
        unpaid.add(withProvider(delivery(100, TODAY, 14), "bbbb0001", "Acme"));
        Order o = order(500, OrderStatus.New, PaymentSource.BankTransfer, 0);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("anna.w@gmail.com");
        o.setBillingDetails(billing);
        open.add(o);

        // when / then
        assertThat(page("q", "aaaa").payables()).extracting(PayableRow::number).containsExactly("aaaa0001");
        assertThat(page("q", "anna.w").side()).isEqualTo(PaymentSide.RECEIVABLES);
    }

    @Test
    void searchFindsThePaymentReferenceOnBothSides() {
        // given
        Delivery d = withProvider(delivery(1000, TODAY, 14), "cccc0001", "Acme");
        Payment transfer = paid(400);
        transfer.setReferenceNo("PRZELEW-ZS-77");
        d.addPayment(transfer);
        unpaid.add(d);
        Order o = order(500, OrderStatus.New, PaymentSource.BankTransfer, 0);
        o.getPayments().get(0).setReferenceNo("ZAM-2026-0042");
        open.add(o);

        // when / then
        assertThat(page("q", "zs-77").payables()).extracting(PayableRow::number).containsExactly("cccc0001");
        assertThat(page("q", "2026-0042").receivables()).hasSize(1);
        assertThat(page("q", "2026-0042").side()).isEqualTo(PaymentSide.RECEIVABLES);
    }

    @Test
    void emptyStatesTellWhatIsMissing() {
        // when / then
        assertThat(page().emptyState().text()).isEqualTo("Wszystko rozliczone: nie ma nieopłaconych dostaw ani zamówień.");
        open.add(order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0));
        assertThat(page("side", "payables").emptyState().text()).isEqualTo("Wszystkie dostawy są opłacone.");
        unpaid.add(withProvider(delivery(100, TODAY, 14), "aaaa0001", "Acme"));
        PaymentsPageModel filtered = page("side", "payables", "q", "zzz");
        assertThat(filtered.emptyState().text()).isEqualTo("Brak wyników.");
        assertThat(filtered.emptyState().actionHref()).isEqualTo("/dashboard/payments?side=payables");
    }

    @Test
    void syncIsAvailableOnlyWithAnInvoicingSystem() {
        // given
        when(store.hasIntegration(IntegrationType.INVOICING_PROVIDER)).thenReturn(false, true);

        // when / then
        assertThat(page().invoicingConnected()).isFalse();
        assertThat(page().invoicingConnected()).isTrue();
    }

    @Test
    void returnToKeepsTheShownSide() {
        // given
        open.add(order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0));

        // when / then
        assertThat(page("focus", "overdue").returnTo()).isEqualTo("/dashboard/payments?side=receivables&focus=overdue");
    }
}
