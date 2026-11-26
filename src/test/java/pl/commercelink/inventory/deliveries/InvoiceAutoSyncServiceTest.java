package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.Payment;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceAutoSyncServiceTest {

    private static final String STORE = "store-1";
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private InvoiceSyncPreviewBuilder previewBuilder;
    @Mock
    private InvoiceLinkingService invoiceLinkingService;
    @Mock
    private InvoiceSyncService invoiceSyncService;
    @Mock
    private Clock clock;
    @Mock
    private Store store;
    @Mock
    private InvoicingProvider provider;

    @InjectMocks
    private InvoiceAutoSyncService service;

    @Test
    void perfectMatchIsLinkedAndThenSynced() {
        // given
        Delivery delivery = delivery("11111111-aaaa", "ZK/1", DAY);
        backlogWithTimeToSpare(delivery);
        Invoice invoice = invoice("inv-1");
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenReturn(List.of(invoice));
        InvoiceSyncPreview preview = preview(50.0);
        when(previewBuilder.build(STORE, "11111111-aaaa", "inv-1")).thenReturn(preview);
        when(invoiceLinkingService.linkUnlessInvoiced(STORE, "11111111-aaaa", invoice)).thenReturn(true);
        when(invoiceSyncService.apply(STORE, preview)).thenReturn(true);

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.synced()).containsExactly(delivery.getShortenedDeliveryId());
        assertThat(result.invoiceNotApproved()).isEmpty();
        assertThat(result.unchanged()).isZero();
        assertThat(result.continueAfter()).isNull();
        InOrder order = inOrder(invoiceLinkingService, invoiceSyncService);
        order.verify(invoiceLinkingService).linkUnlessInvoiced(STORE, "11111111-aaaa", invoice);
        order.verify(invoiceSyncService).apply(STORE, preview);
    }

    @Test
    void syncedDeliveryWhoseInvoiceWasNotApprovedIsReported() {
        // given
        Delivery delivery = delivery("11111111-aaaa", "ZK/1", DAY);
        backlogWithTimeToSpare(delivery);
        Invoice invoice = invoice("inv-1");
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenReturn(List.of(invoice));
        InvoiceSyncPreview preview = preview(50.0);
        when(previewBuilder.build(STORE, "11111111-aaaa", "inv-1")).thenReturn(preview);
        when(invoiceLinkingService.linkUnlessInvoiced(STORE, "11111111-aaaa", invoice)).thenReturn(true);
        when(invoiceSyncService.apply(STORE, preview)).thenReturn(false);

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.synced()).containsExactly(delivery.getShortenedDeliveryId());
        assertThat(result.invoiceNotApproved()).containsExactly(delivery.getShortenedDeliveryId());
    }

    @Test
    void deliveryWhosePaymentDisagreesWithTheInvoiceIsNotEvenLinked() {
        // given
        Delivery paidDelivery = delivery("11111111-aaaa", "ZK/1", DAY);
        paidDelivery.addPayment(Payment.outgoingBankTransfer("FV/0", null, 50.0));
        Delivery unpaidDelivery = delivery("22222222-bbbb", "ZK/2", DAY.plusHours(1));
        backlogWithTimeToSpare(paidDelivery, unpaidDelivery);
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenReturn(List.of(invoice("inv-1")));
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/2")).thenReturn(List.of(paidInvoice("inv-2")));

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.needsReview()).isEqualTo(2);
        verifyNoInteractions(previewBuilder, invoiceLinkingService, invoiceSyncService);
    }

    @Test
    void invoiceThatNeedsALookIsNotEvenLinked() {
        // given
        backlogWithTimeToSpare(delivery("11111111-aaaa", "ZK/1", DAY));
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenReturn(List.of(invoice("inv-1")));
        when(previewBuilder.build(STORE, "11111111-aaaa", "inv-1")).thenReturn(preview(50.01));

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(result.synced()).isEmpty();
        verifyNoInteractions(invoiceLinkingService, invoiceSyncService);
    }

    @Test
    void deliveriesWithoutExactlyOneInvoiceAreLeftAlone() {
        // given
        Delivery none = delivery("11111111-aaaa", "ZK/1", DAY);
        Delivery several = delivery("22222222-bbbb", "ZK/2", DAY.plusHours(1));
        Delivery noNumber = delivery("33333333-cccc", " ", DAY.plusHours(2));
        Delivery provisional = delivery("44444444-dddd", "TMP-4", DAY.plusHours(3));
        provisional.setExternalDeliveryIdProvisional(true);
        backlogWithTimeToSpare(none, several, noNumber, provisional);
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenReturn(List.of());
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/2")).thenReturn(List.of(invoice("inv-1"), invoice("inv-2")));

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.noInvoice()).isEqualTo(3);
        assertThat(result.severalInvoices()).isEqualTo(1);
        verify(provider, never()).fetchPurchaseInvoicesAwaitingApprovalByOrderId("TMP-4");
        verifyNoInteractions(previewBuilder, invoiceLinkingService, invoiceSyncService);
    }

    @Test
    void invoiceLinkedMeanwhileIsNotSyncedByTheRun() {
        // given
        backlogWithTimeToSpare(delivery("11111111-aaaa", "ZK/1", DAY));
        Invoice invoice = invoice("inv-1");
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenReturn(List.of(invoice));
        when(previewBuilder.build(STORE, "11111111-aaaa", "inv-1")).thenReturn(preview(50.0));
        when(invoiceLinkingService.linkUnlessInvoiced(STORE, "11111111-aaaa", invoice)).thenReturn(false);

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.needsReview()).isEqualTo(1);
        verifyNoInteractions(invoiceSyncService);
    }

    @Test
    void failureOfOneDeliveryDoesNotStopTheOthers() {
        // given
        Delivery broken = delivery("11111111-aaaa", "ZK/1", DAY);
        Delivery fine = delivery("22222222-bbbb", "ZK/2", DAY.plusHours(1));
        backlogWithTimeToSpare(fine, broken);
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenThrow(new IllegalStateException("503"));
        Invoice invoice = invoice("inv-2");
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/2")).thenReturn(List.of(invoice));
        InvoiceSyncPreview preview = preview(50.0);
        when(previewBuilder.build(STORE, "22222222-bbbb", "inv-2")).thenReturn(preview);
        when(invoiceLinkingService.linkUnlessInvoiced(STORE, "22222222-bbbb", invoice)).thenReturn(true);
        when(invoiceSyncService.apply(STORE, preview)).thenReturn(true);

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.failed()).containsExactly(broken.getShortenedDeliveryId());
        assertThat(result.synced()).containsExactly(fine.getShortenedDeliveryId());
    }

    @Test
    void runOutOfTimeReportsWhatIsLeftAndWhereItStopped() {
        // given
        backlog(delivery("11111111-aaaa", "ZK/1", DAY), delivery("22222222-bbbb", "ZK/2", DAY.plusHours(1)),
                delivery("33333333-cccc", "ZK/3", DAY.plusHours(2)));
        when(clock.millis()).thenReturn(0L, 0L, InvoiceAutoSyncService.BUDGET.toMillis());
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1")).thenReturn(List.of());

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.noInvoice()).isEqualTo(1);
        assertThat(result.remaining()).isEqualTo(2);
        assertThat(result.continueAfter()).isEqualTo(DAY);
    }

    @Test
    void nextRunContinuesAfterTheDeliveriesAlreadyChecked() {
        // given
        backlogWithTimeToSpare(delivery("11111111-aaaa", "ZK/1", DAY), delivery("22222222-bbbb", "ZK/2", DAY.plusHours(1)));
        when(provider.fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/2")).thenReturn(List.of());

        // when
        InvoiceAutoSyncResult result = service.run(STORE, DAY);

        // then
        assertThat(result.checked()).isEqualTo(1);
        verify(provider, never()).fetchPurchaseInvoicesAwaitingApprovalByOrderId("ZK/1");
    }

    @Test
    void storeWithoutAnInvoicingSystemIsNotConfigured() {
        // given
        when(storesRepository.findById(STORE)).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(null);

        // when
        InvoiceAutoSyncResult result = service.run(STORE, null);

        // then
        assertThat(result.configured()).isFalse();
        verifyNoInteractions(deliveriesRepository);
    }

    private void backlogWithTimeToSpare(Delivery... deliveries) {
        backlog(deliveries);
        when(clock.millis()).thenReturn(0L);
    }

    private void backlog(Delivery... deliveries) {
        when(storesRepository.findById(STORE)).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(provider);
        when(deliveriesRepository.findToSettle(STORE)).thenReturn(List.of(deliveries));
    }

    private static Delivery delivery(String id, String orderNumber, LocalDateTime receivedAt) {
        Delivery delivery = new Delivery(STORE, orderNumber, "Acme");
        delivery.setDeliveryId(id);
        delivery.setReceivedAt(receivedAt);
        return delivery;
    }

    private static Invoice invoice(String id) {
        return new Invoice(id, "FV/" + id, null, Price.fromNet(100.0), null, "PLN", 1.0, false, null, List.of(), null, null);
    }

    private static Invoice paidInvoice(String id) {
        return new Invoice(id, "FV/" + id, null, Price.fromNet(100.0), null, "PLN", 1.0, true, null, List.of(), null, null);
    }

    private static InvoiceSyncPreview preview(double invoicePrice) {
        InvoiceSyncPreview.Mapping mapping = new InvoiceSyncPreview.Mapping();
        mapping.setEan("5901234123457");
        mapping.setUnitCost(50.0);
        mapping.setSelectedPositionId("pos-1");
        InvoiceSyncPreview.Option option = new InvoiceSyncPreview.Option();
        option.setId("pos-1");
        option.setQty(2);
        option.setPriceNet(invoicePrice);
        option.setTotalNet(invoicePrice * 2);
        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setMappings(new ArrayList<>(List.of(mapping)));
        preview.setOptions(new ArrayList<>(List.of(option)));
        return preview;
    }
}
