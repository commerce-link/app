package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipPurchaseServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String DELIVERY_ID = "delivery-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private DeliveryCreationService deliveryCreationService;
    @Mock
    private SupplierConnectionModeResolver supplierConnectionModeResolver;
    @Mock
    private SupplierPurchaseEventPublisher supplierPurchaseEventPublisher;
    @Mock
    private SupplierProviderResolver supplierProviderResolver;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private DropshipOrderCompletion dropshipOrderCompletion;
    @Mock
    private SupplierProvider supplierProvider;

    @InjectMocks
    private DropshipPurchaseService service;

    private final Store store = new Store();

    @BeforeEach
    void setUp() {
        store.setStoreId(STORE_ID);
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lenient().when(supplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(supplierProvider);
    }

    private void connectSupplier(ConnectionMode mode) {
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setSupplierConnections(List.of(new StoreSupplierConnection(PROVIDER, mode)));
        store.setFulfilmentConfiguration(fulfilment);
    }

    private static ShippingDetails shippingDetails() {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("ul. Polna 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setPhone("+48601234567");
        details.setEmail("jan.kowalski@example.com");
        return details;
    }

    private static Order directToConsumerOrder() {
        Order order = new Order();
        order.setOrderId(ORDER_ID);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        order.setShippingDetails(shippingDetails());
        return order;
    }

    private DeliveryCreationForm formWithItem(String ean, String mfn, int requestedQty, double unitCost) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        DeliveryItem item = new DeliveryItem();
        item.setName("Product " + ean);
        item.setEan(ean);
        item.setMfn(mfn);
        item.setRequestedQty(requestedQty);
        item.setUnitCost(unitCost);
        form.getItems().add(item);
        return form;
    }

    private Delivery pendingDropshipDelivery(String purchaseRef) {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        delivery.setDropshipOrderId(ORDER_ID);
        return delivery;
    }

    @Test
    void dropshipAvailableWhenProviderSupportsIt() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(true);

        // when / then
        assertTrue(service.isDropshipAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void dropshipUnavailableWhenProviderDoesNotSupportIt() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(false);

        // when / then
        assertFalse(service.isDropshipAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void submitDropshipCreatesAwaitingApprovalDeliveryForGlobalSupplier() {
        // given
        connectSupplier(ConnectionMode.GLOBAL);
        when(supplierProvider.supportsDropshipping()).thenReturn(true);
        when(supplierConnectionModeResolver.resolve(store, PROVIDER)).thenReturn(ConnectionMode.GLOBAL);
        when(deliveriesRepository.findByPurchaseRef(STORE_ID, "ref-1")).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, directToConsumerOrder(), form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(ORDER_ID, saved.getValue().getDropshipOrderId());
        assertTrue(saved.getValue().isDropship());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, saved.getValue().getOrderStatus());
        assertEquals(ConnectionMode.GLOBAL, saved.getValue().getConnectionMode());
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void submitDropshipPublishesImmediatelyForOwnSupplier() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(true);
        when(deliveriesRepository.findByPurchaseRef(STORE_ID, "ref-1")).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, directToConsumerOrder(), form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, saved.getValue().getOrderStatus());
        verify(supplierPurchaseEventPublisher).publish(any());
    }

    @Test
    void submitDropshipRejectsWarehouseFulfilmentOrder() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.fulfilmentType", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void submitDropshipRejectsOrderWithoutShippingDetails() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.setShippingDetails(null);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.address", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void submitDropshipRejectsProviderWithoutDropshipSupport() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(false);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, directToConsumerOrder(), formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.unsupported", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void submitDropshipRejectsAConsigneeTheSupplierContractWouldRefuse() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.getShippingDetails().setSurname(null);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.consignee", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void createManualDropshipCreatesASettledDeliveryAndMarksItemsSupplied() {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        form.setExternalDeliveryId("PHONE-123");
        when(supplierConnectionModeResolver.resolve(store, PROVIDER)).thenReturn(ConnectionMode.OWN);

        // when
        OperationResult<String> result = service.createManualDropship(STORE_ID, directToConsumerOrder(), form);

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        Delivery delivery = saved.getValue();
        assertEquals(ORDER_ID, delivery.getDropshipOrderId());
        assertEquals("PHONE-123", delivery.getExternalDeliveryId());
        assertNull(delivery.getOrderStatus());
        verify(deliveryCreationService).claimAllocations(eq(STORE_ID), same(delivery), same(form));
        verify(dropshipOrderCompletion).markSuppliedByDropship(STORE_ID, ORDER_ID, delivery.getDeliveryId());
        verify(supplierPurchaseEventPublisher, never()).publish(any());
        verify(supplierProvider, never()).placeDropshipOrder(any());
    }

    @Test
    void createManualDropshipRejectsWarehouseFulfilmentOrder() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when
        OperationResult<String> result = service.createManualDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0));

        // then
        assertFalse(result.isSuccess());
        verify(deliveriesRepository, never()).save(any());
        verify(dropshipOrderCompletion, never()).markSuppliedByDropship(any(), any(), any());
    }

    @Test
    void manualAndAutomaticDropshipCreateIdenticallyConfiguredDeliveries() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(true);
        when(supplierConnectionModeResolver.resolve(store, PROVIDER)).thenReturn(ConnectionMode.OWN);
        when(deliveriesRepository.findByPurchaseRef(STORE_ID, "ref-a")).thenReturn(Optional.empty());
        DeliveryCreationForm submitForm = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        submitForm.setEstimatedDeliveryAt(LocalDate.now().plusDays(3));
        submitForm.setShippingCost(9.99);
        submitForm.setPaymentCost(1.5);
        submitForm.setPaymentTerms(14);
        submitForm.setTax(23.0);
        DeliveryCreationForm manualForm = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        manualForm.setEstimatedDeliveryAt(submitForm.getEstimatedDeliveryAt());
        manualForm.setShippingCost(submitForm.getShippingCost());
        manualForm.setPaymentCost(submitForm.getPaymentCost());
        manualForm.setPaymentTerms(submitForm.getPaymentTerms());
        manualForm.setTax(submitForm.getTax());

        // when
        OperationResult<PurchaseSubmission> submitResult =
                service.submitDropship(STORE_ID, directToConsumerOrder(), submitForm, "ref-a");
        OperationResult<String> manualResult =
                service.createManualDropship(STORE_ID, directToConsumerOrder(), manualForm);

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository, times(2)).save(saved.capture());
        Delivery submitted = saved.getAllValues().get(0);
        Delivery manual = saved.getAllValues().get(1);
        assertEquals(submitted.getProvider(), manual.getProvider());
        assertEquals(submitted.getConnectionMode(), manual.getConnectionMode());
        assertEquals(submitted.getDropshipOrderId(), manual.getDropshipOrderId());
        assertEquals(submitted.getDeliveryAddress(), manual.getDeliveryAddress());
        assertEquals(submitted.getEstimatedDeliveryAt(), manual.getEstimatedDeliveryAt());
        assertEquals(submitted.getShippingCost(), manual.getShippingCost());
        assertEquals(submitted.getPaymentCost(), manual.getPaymentCost());
        assertEquals(submitted.getPaymentTerms(), manual.getPaymentTerms());
        assertEquals(submitted.getTax(), manual.getTax());
        assertTrue(submitResult.isSuccess());
        assertTrue(manualResult.isSuccess());
    }

    @Test
    void toConsigneeNormalizesTheCountryCase() {
        // given
        ShippingDetails details = shippingDetails();
        details.setCountry("pl");

        // when
        SupplierConsignee consignee = DropshipPurchaseService.toConsignee(details);

        // then
        assertEquals("PL", consignee.country());
    }

    @Test
    void toConsigneeMapsShippingDetails() {
        // when
        SupplierConsignee consignee = DropshipPurchaseService.toConsignee(shippingDetails());

        // then
        assertEquals("Jan", consignee.firstName());
        assertEquals("Kowalski", consignee.lastName());
        assertEquals("ul. Polna 1", consignee.streetAndNumber());
        assertEquals("00-001", consignee.postalCode());
        assertEquals("Warszawa", consignee.city());
        assertEquals("PL", consignee.country());
        assertEquals("+48601234567", consignee.phone());
        assertEquals("jan.kowalski@example.com", consignee.email());
    }

    @Test
    void placeDropshipOrderFailsWhenOrderIsGone() {
        // given
        Delivery delivery = pendingDropshipDelivery("ref-1");
        List<SupplierOrderLine> lines = List.of(new SupplierOrderLine("ACME-EAN-1", "EAN-1", "MFN-1", 2));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when / then
        assertThrows(SupplierOrderException.class, () -> service.placeDropshipOrder(STORE_ID, delivery, lines));
    }

    @Test
    void placeDropshipOrderFailsWhenTheConsigneeTurnedInvalid() {
        // given
        Delivery delivery = pendingDropshipDelivery("ref-1");
        List<SupplierOrderLine> lines = List.of(new SupplierOrderLine("ACME-EAN-1", "EAN-1", "MFN-1", 2));
        Order order = directToConsumerOrder();
        order.getShippingDetails().setSurname(null);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when / then
        assertThrows(SupplierOrderException.class, () -> service.placeDropshipOrder(STORE_ID, delivery, lines));
    }

    @Test
    void placeDropshipOrderBuildsRequestFromOrderShippingDetails() {
        // given
        Delivery delivery = pendingDropshipDelivery("ref-1");
        List<SupplierOrderLine> lines = List.of(new SupplierOrderLine("ACME-EAN-1", "EAN-1", "MFN-1", 2));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(directToConsumerOrder());
        when(supplierProvider.placeDropshipOrder(any())).thenReturn(new SupplierOrderResult(
                "ACME-DS-ref-1", 220.0, "PLN",
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN"))));

        // when
        service.placeDropshipOrder(STORE_ID, delivery, lines);

        // then
        ArgumentCaptor<SupplierDropshipRequest> request = ArgumentCaptor.forClass(SupplierDropshipRequest.class);
        verify(supplierProvider).placeDropshipOrder(request.capture());
        SupplierConsignee consignee = request.getValue().consignee();
        assertEquals("Jan", consignee.firstName());
        assertEquals("Kowalski", consignee.lastName());
        assertEquals("ul. Polna 1", consignee.streetAndNumber());
        assertEquals("+48601234567", consignee.phone());
        assertEquals("jan.kowalski@example.com", consignee.email());
        assertEquals("ref-1", request.getValue().clientOrderRef());
        assertEquals("CommerceLink ref-1", request.getValue().deliveryInstructions());
    }
}
