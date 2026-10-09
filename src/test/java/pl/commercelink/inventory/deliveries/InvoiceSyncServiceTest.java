package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.InvoiceSyncHandler;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSyncServiceTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private InvoicingProvider invoicingProvider;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private RMAItemsRepository rmaItemsRepository;
    @Mock
    private Warehouse warehouse;
    @Mock
    private InvoiceSyncHandler invoiceSyncHandler;
    @Mock
    private DeliveryCostSync deliveryCostSync;

    @InjectMocks
    private InvoiceSyncService invoiceSyncService;

    @Test
    void applyAddsFacadeDeltaToDeliveryTotalCost() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        Invoice invoice = new Invoice("inv-1", "FV/1", null, Price.fromNet(120.0), null, "PLN",
                1.0, false, null, List.of(new InvoicePosition("pos-1", "Line", 1, Price.fromNet(120.0))), null, null);
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenReturn(invoice);
        when(warehouse.invoiceSyncHandler("store-1")).thenReturn(invoiceSyncHandler);
        when(deliveryCostSync.apply(eq("store-1"), eq("delivery-1"), eq(Map.of("MFN-1", 120.0)))).thenReturn(15.0);

        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setDeliveryId("delivery-1");
        delivery.increaseTotalCost(100.0);
        when(deliveriesRepository.findById("store-1", "delivery-1")).thenReturn(delivery);

        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setDeliveryId("delivery-1");
        preview.setInvoiceId("inv-1");
        InvoiceSyncPreview.Mapping mapping = new InvoiceSyncPreview.Mapping();
        mapping.setMfn("MFN-1");
        mapping.setSelectedPositionId("pos-1");
        preview.setMappings(List.of(mapping));

        // when
        invoiceSyncService.apply("store-1", preview);

        // then
        assertThat(delivery.getTotalCost()).isEqualTo(115.0);
        verify(invoiceSyncHandler).sync(any());
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void applyResolvesCounterpartyShortcutWithoutOverwritingTheDeliveryProvider() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        Invoice invoice = new Invoice("inv-1", "FV/1", null, Price.fromNet(120.0), null, "PLN",
                1.0, false, null, List.of(new InvoicePosition("pos-1", "Line", 1, Price.fromNet(120.0))), null, null);
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenReturn(invoice);
        when(warehouse.invoiceSyncHandler("store-1")).thenReturn(invoiceSyncHandler);
        when(deliveryCostSync.apply(eq("store-1"), eq("delivery-1"), eq(Map.of("MFN-1", 120.0)))).thenReturn(0.0);

        Delivery delivery = new Delivery("store-1", null, "Kosatec-k7f3a9c2");
        delivery.setDeliveryId("delivery-1");
        when(deliveriesRepository.findById("store-1", "delivery-1")).thenReturn(delivery);

        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setDeliveryId("delivery-1");
        preview.setInvoiceId("inv-1");
        preview.setInvoiceShortcut("KOS-INV");
        InvoiceSyncPreview.Mapping mapping = new InvoiceSyncPreview.Mapping();
        mapping.setMfn("MFN-1");
        mapping.setSelectedPositionId("pos-1");
        preview.setMappings(List.of(mapping));

        // when
        invoiceSyncService.apply("store-1", preview);

        // then
        assertThat(delivery.getProvider()).isEqualTo("Kosatec-k7f3a9c2");
        assertThat(delivery.getCounterpartyShortcut()).isEqualTo("KOS-INV");
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void applyWithAPaidInvoiceAddsATransferForTheDeliveryGross() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setDeliveryId("delivery-1");
        delivery.setTax(1.23);
        delivery.increaseTotalCost(100.0);

        // when
        applyInvoice(delivery, invoice("inv-1", true));

        // then
        assertThat(delivery.getPayments()).singleElement().satisfies(payment -> {
            assertThat(payment.getAmount()).isEqualTo(123.0);
            assertThat(payment.getReferenceNo()).isEqualTo("FV/inv-1");
        });
        assertThat(delivery.isSynced()).isTrue();
    }

    @Test
    void applyWithAnUnpaidInvoiceRemovesEveryPaymentOfTheDelivery() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setDeliveryId("delivery-1");
        delivery.addPayment(Payment.outgoingBankTransfer("FV/0", null, 50.0));
        delivery.addPayment(Payment.outgoingBankTransfer("FV/00", null, 20.0));

        // when
        applyInvoice(delivery, invoice("inv-1", false));

        // then
        assertThat(delivery.getPayments()).isEmpty();
    }

    @Test
    void applyLeavesThePaymentTermsOfADeliveryWithoutAnOrderDate() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderedAt(null);
        delivery.setPaymentTerms(14);
        Invoice invoice = new Invoice("inv-1", "FV/1", null, Price.fromNet(100.0), null, "PLN", 1.0, false,
                LocalDate.of(2026, 10, 22), List.of(), null, null);

        // when
        applyInvoice(delivery, invoice);

        // then
        assertThat(delivery.getPaymentTerms()).isEqualTo(14);
        assertThat(delivery.isSynced()).isTrue();
        verify(deliveriesRepository).save(delivery);
    }

    private void applyInvoice(Delivery delivery, Invoice invoice) {
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        when(invoicingProvider.fetchInvoiceById(invoice.id(), InvoiceDirection.Purchase)).thenReturn(invoice);
        when(warehouse.invoiceSyncHandler("store-1")).thenReturn(invoiceSyncHandler);
        when(deliveriesRepository.findById("store-1", delivery.getDeliveryId())).thenReturn(delivery);
        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setDeliveryId(delivery.getDeliveryId());
        preview.setInvoiceId(invoice.id());
        invoiceSyncService.apply("store-1", preview);
    }

    private static Delivery invoicedDelivery(String id, String invoiceId) {
        Delivery d = new Delivery("store-1", null, "Acme");
        d.setDeliveryId(id + "-0000-0000-0000-000000000000");
        d.increaseTotalCost(100.0);
        d.setInvoiced(true);
        d.getDocuments().add(new Document(invoiceId, null, null, DocumentType.InvoiceVat));
        return d;
    }

    private static Invoice invoice(String id, boolean paid) {
        return new Invoice(id, "FV/" + id, null, Price.fromNet(100.0), null, "PLN", 1.0, paid, null, List.of(), null, null);
    }

    @Test
    void syncReportsWhatItCheckedAndPaid() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        Delivery paid = invoicedDelivery("aaaa0001", "inv-1");
        Delivery open = invoicedDelivery("aaaa0002", "inv-2");
        when(deliveriesRepository.findUnpaidDeliveries("store-1")).thenReturn(List.of(paid, open));
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenReturn(invoice("inv-1", true));
        when(invoicingProvider.fetchInvoiceById("inv-2", InvoiceDirection.Purchase)).thenReturn(invoice("inv-2", false));

        // when
        InvoiceSyncResult result = invoiceSyncService.sync("store-1");

        // then
        assertThat(result.checked()).isEqualTo(2);
        assertThat(result.paidDeliveries()).containsExactly("aaaa0001");
        assertThat(result.unpaid()).isEqualTo(1);
        verify(deliveriesRepository).save(paid);
    }

    @Test
    void syncWithoutInvoicingSystemSaysSoInsteadOfFailing() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(null);

        // when
        InvoiceSyncResult result = invoiceSyncService.sync("store-1");

        // then
        assertThat(result.configured()).isFalse();
    }

    @Test
    void oneFailingInvoiceDoesNotStopTheRest() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        Delivery broken = invoicedDelivery("aaaa0001", "inv-1");
        Delivery fine = invoicedDelivery("aaaa0002", "inv-2");
        when(deliveriesRepository.findUnpaidDeliveries("store-1")).thenReturn(List.of(broken, fine));
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenThrow(new IllegalStateException("502"));
        when(invoicingProvider.fetchInvoiceById("inv-2", InvoiceDirection.Purchase)).thenReturn(invoice("inv-2", true));

        // when
        InvoiceSyncResult result = invoiceSyncService.sync("store-1");

        // then
        assertThat(result.failedInvoices()).containsExactly("inv-1");
        assertThat(result.paidDeliveries()).containsExactly("aaaa0002");
    }
}
