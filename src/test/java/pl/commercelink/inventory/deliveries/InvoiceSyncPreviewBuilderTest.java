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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSyncPreviewBuilderTest {

    private static final String EAN_A = "5901234123457";
    private static final String EAN_B = "5901234123464";
    private static final String EAN_C = "5901234123471";
    private static final String SHARED_MFN = "SHOE-1";

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
        delivery.setItems(List.of(item(EAN_A, 2, 50.0)));
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
        assertThat(preview.getDeliveryPayments()).containsExactly(new InvoiceSyncPreview.PaymentLine(100.0, "FV/1"));
        assertThat(preview.getPaymentSync()).isEqualTo(InvoicePaymentSync.REMOVE);
        assertThat(preview.getDeliveryOrderedAt()).isEqualTo("06.10.2026");
        assertThat(preview.getDeliverySupplier()).isEqualTo("Acme");
        assertThat(preview.getOptions()).singleElement().satisfies(o -> assertThat(o.getTotalNet()).isEqualTo(100.0));
        assertThat(preview.getMappings()).singleElement().satisfies(m -> assertThat(m.getSelectedPositionId()).isEqualTo("pos-1"));
    }

    @Test
    void rowsFollowTheOrderOfTheDeliveryNotTheGrouping() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        Delivery delivery = new Delivery("store-1", "ZK/1", "Acme");
        delivery.setDeliveryId("delivery-1");
        delivery.setAllocations(List.of(allocation(EAN_C), allocation(EAN_A), allocation(EAN_B), allocation(EAN_A)));
        delivery.setItems(List.of(item(EAN_A, 1, 10.0), item(EAN_B, 1, 20.0), item(EAN_C, 1, 30.0)));
        when(deliveriesQueryService.fetchDeliveryWithAllocations("store-1", "delivery-1")).thenReturn(delivery);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        Invoice invoice = new Invoice("inv-1", "FV/1", null, Price.fromNet(60.0), null, "PLN", 1.0, false, null, List.of(),
                BillingParty.company("seller-1", "Acme S.A.", null, null, null, null, null, "ACME"), null);
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenReturn(invoice);

        // when
        InvoiceSyncPreview preview = builder.build("store-1", "delivery-1", "inv-1");

        // then
        assertThat(preview.getMappings()).extracting(InvoiceSyncPreview.Mapping::getEan).containsExactly(EAN_C, EAN_A, EAN_B);
    }

    @Test
    void twoProductsUnderOneCodeGetTheirOwnRows() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        Delivery delivery = new Delivery("store-1", "ZK/1", "Acme");
        delivery.setDeliveryId("delivery-1");
        delivery.setAllocations(List.of(allocation(EAN_B), allocation(EAN_A)));
        delivery.setItems(List.of(item(EAN_A, 1, 100.0), item(EAN_B, 1, 120.0)));
        when(deliveriesQueryService.fetchDeliveryWithAllocations("store-1", "delivery-1")).thenReturn(delivery);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        Invoice invoice = new Invoice("inv-1", "FV/1", null, Price.fromNet(220.0), null, "PLN", 1.0, false, null, List.of(),
                BillingParty.company("seller-1", "Acme S.A.", null, null, null, null, null, "ACME"), null);
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenReturn(invoice);

        // when
        InvoiceSyncPreview preview = builder.build("store-1", "delivery-1", "inv-1");

        // then
        assertThat(preview.getMappings())
                .extracting(InvoiceSyncPreview.Mapping::getEan, InvoiceSyncPreview.Mapping::getMfn, InvoiceSyncPreview.Mapping::getUnitCost)
                .containsExactly(tuple(EAN_B, SHARED_MFN, 120.0), tuple(EAN_A, SHARED_MFN, 100.0));
    }

    @Test
    void anInvoiceInEuroKeepsItsTotalsInEuroAndMatchesTheLinesTheAdapterConvertedToZloty() {
        // given
        Store store = new Store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        Delivery delivery = new Delivery("store-1", "W6XDAEXETN", "Wave");
        delivery.setDeliveryId("delivery-1");
        delivery.setItems(List.of(item(EAN_A, 1, 1311.66)));
        when(deliveriesQueryService.fetchDeliveryWithAllocations("store-1", "delivery-1")).thenReturn(delivery);
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        // as Fakturownia returns it: totals in EUR, every line converted with the invoice's rate (299.09 EUR x 4.3855)
        Invoice invoice = new Invoice("inv-1", "1301004976944", null, new Price(299.09, 299.09, "EUR"), null, "EUR", 4.3855,
                false, null, List.of(new InvoicePosition("pos-1", "GIGABYTE X870E AORUS PRO X3D ICE", 1, Price.fromNet(1311.66))),
                BillingParty.company("seller-1", "Wave", null, null, null, null, null, "WAVE"), null);
        when(invoicingProvider.fetchInvoiceById("inv-1", InvoiceDirection.Purchase)).thenReturn(invoice);

        // when
        InvoiceSyncPreview preview = builder.build("store-1", "delivery-1", "inv-1");

        // then
        assertThat(preview.getCurrency()).isEqualTo("EUR");
        assertThat(preview.getPositionCurrency()).isEqualTo("PLN");
        assertThat(preview.getInvoicePriceNetConverted()).isEqualTo(1311.66);
        assertThat(preview.getMappings()).singleElement().satisfies(m -> assertThat(m.getSelectedPositionId()).isEqualTo("pos-1"));
    }

    @Test
    void unknownDeliveryGivesNoPreview() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(new Store());

        // when / then
        assertThat(builder.build("store-1", "missing", "inv-1")).isNull();
    }

    private static Allocation allocation(String ean) {
        Allocation allocation = new Allocation();
        allocation.setEan(ean);
        allocation.setMfn(SHARED_MFN);
        return allocation;
    }

    private static DeliveryItem item(String ean, int qty, double unitCost) {
        DeliveryItem item = new DeliveryItem();
        item.setName("Product " + ean);
        item.setEan(ean);
        item.setMfn(SHARED_MFN);
        item.setOrderedQty(qty);
        item.setUnitCost(unitCost);
        return item;
    }
}
