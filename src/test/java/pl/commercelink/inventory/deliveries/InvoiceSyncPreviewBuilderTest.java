package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.Payment;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSyncPreviewBuilderTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private InvoicingProvider invoicingProvider;
    @Mock
    private CounterpartyShortcuts counterpartyShortcuts;

    @InjectMocks
    private InvoiceSyncPreviewBuilder builder;

    @Test
    void previewCarriesThePaymentsAndTheDueDateAsDaysFromTheOrder() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        Delivery delivery = new Delivery("store-1", "ZK/1", "Acme");
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderedAt(LocalDateTime.of(2026, 10, 6, 9, 30));
        delivery.addPayment(Payment.outgoingBankTransfer("FV/1", null, 100.0));
        delivery.setItems(List.of(item("MFN-1", 2, 50.0)));
        when(deliveriesQueryService.fetchDeliveryWithAllocations("store-1", "delivery-1")).thenReturn(delivery);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        Invoice invoice = new Invoice("inv-1", "FV/1", null, Price.fromNet(100.0), null, "PLN", 1.0, false,
                LocalDate.of(2026, 10, 22), List.of(new InvoicePosition("pos-1", "Line", 2, Price.fromNet(50.0))),
                BillingParty.company("seller-1", "Acme S.A.", null, null, null, null, null, "ACME"), null);
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenReturn(invoice);

        // when
        InvoiceSyncPreview preview = builder.build("store-1", "delivery-1", "inv-1");

        // then
        assertThat(preview.getPaymentTermDays()).isEqualTo(16);
        assertThat(preview.getDeliveryPaymentsCount()).isEqualTo(1);
        assertThat(preview.getPaymentSync()).isEqualTo(InvoicePaymentSync.REMOVE);
        assertThat(preview.getDeliveryOrderedAt()).isEqualTo("06.10.2026");
        assertThat(preview.getDeliverySupplier()).isEqualTo("Acme");
        assertThat(preview.getOptions()).singleElement().satisfies(o -> assertThat(o.getTotalNet()).isEqualTo(100.0));
        assertThat(preview.getMappings()).singleElement().satisfies(m -> assertThat(m.getSelectedPositionId()).isEqualTo("pos-1"));
    }

    @Test
    void unknownDeliveryGivesNoPreview() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(new Store());

        // when / then
        assertThat(builder.build("store-1", "missing", "inv-1")).isNull();
    }

    private static DeliveryItem item(String mfn, int qty, double unitCost) {
        DeliveryItem item = new DeliveryItem();
        item.setName("Product " + mfn);
        item.setMfn(mfn);
        item.setOrderedQty(qty);
        item.setUnitCost(unitCost);
        return item;
    }
}
