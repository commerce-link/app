package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;
import org.mockito.Spy;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemDraft;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.web.dtos.AddItemsForm;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.event.OrderEventsRepository;
import org.springframework.ui.ExtendedModelMap;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.web.dtos.OrderItemsForm;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentTrackingSubscriber;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private OrdersManager ordersManager;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private RedirectAttributes redirectAttributes;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OrderEventsRepository orderEventsRepository;
    @Mock
    private ShipmentCarrierOptions shipmentCarrierOptions;
    @Mock
    private DropshipItemLookup dropshipItemLookup;
    @Mock
    private ShipmentTrackingSubscriber shipmentTrackingSubscriber;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private StoreCategories storeCategories;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private PricelistFinder pricelistFinder;
    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private pl.commercelink.orders.OrderListService orderListService;
    @Mock
    private pl.commercelink.orders.filters.services.OrderFiltersService orderFilters;

    // Real resolver over the test classpath registry (`Stub` is a registered supplier type).
    @Spy
    private SupplierChoice supplierChoice = new SupplierChoice(new SupplierRegistry(
            new SupplierProviderFactory(new ProviderConfigurationManager(mock(SecretsManager.class)))));

    @InjectMocks
    private OrdersController ordersController;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setupStoreId() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @BeforeEach
    void setUpSupplierLabels() {
        when(supplierLabels.forStore(any())).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    @DisplayName("addOrderItems resolves pricelist and inventory entries in the posted sequence and hands them to the manager as one batch")
    void addOrderItemsResolvesEntriesAndAddsThemAsOneBatch() {
        // given
        Order order = orderBase();
        Store store = new Store();
        store.setStoreId(STORE_ID);
        AvailabilityAndPrice laptop = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "Label", "Laptop",
                "Laptops", 200L, 10L, 5, 0L, false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-1")).thenReturn(Optional.of(laptop));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByInventoryKey(any())).thenReturn(MatchedInventory.empty(new InventoryKey("EAN-X", "MFN-X")));
        AddItemsForm form = new AddItemsForm();
        form.setItems(List.of(
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 2),
                AddItemsForm.Entry.fromInventory("EAN-X", "MFN-X", 1)));

        // when
        String view = ordersController.addOrderItems(ORDER_ID, form);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItemDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(ordersManager).addOrderItems(eq(store), eq(order), drafts.capture());
        assertThat(drafts.getValue()).extracting(OrderItemDraft::sku).containsExactly("MFN-1", "MFN-X");
        assertThat(drafts.getValue()).extracting(OrderItemDraft::qty).containsExactly(2, 1);
    }

    @Test
    @DisplayName("addOrderItems rejects the whole batch when one pricelist entry is unknown")
    void addOrderItemsRejectsWholeBatchWhenPricelistEntryUnknown() {
        // given
        AvailabilityAndPrice laptop = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "Label", "Laptop",
                "Laptops", 200L, 10L, 5, 0L, false);
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-1")).thenReturn(Optional.of(laptop));
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-missing")).thenReturn(Optional.empty());
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        AddItemsForm form = new AddItemsForm();
        form.setItems(List.of(
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1),
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-missing", 1)));

        // when / then
        assertThatThrownBy(() -> ordersController.addOrderItems(ORDER_ID, form))
                .isInstanceOf(ResponseStatusException.class);
        verify(ordersManager, never()).addOrderItems(any(), any(), any());
    }

    @Test
    @DisplayName("updateAddressDetails persists new billing details on order that is not yet invoiced")
    void updateAddressDetailsSetsBillingDetailsOnNonInvoicedOrderAndSaves() {
        // given
        Order existingOrder = orderBase();
        BillingDetails newBilling = new BillingDetails();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getBillingDetails().getCity()).isEqualTo("Krakow");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("updateAddressDetails sets locked-billing flash message and does not save when order is already invoiced")
    void updateAddressDetailsSetsFlashMessageAndSkipsSaveWhenOrderAlreadyInvoiced() {
        // given
        Order existingOrder = invoicedOrder();
        BillingDetails newBilling = new BillingDetails();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("error.message.billing.details.locked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Billing locked");

        // when
        ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Billing locked"));
    }

    @Test
    @DisplayName("updateAddressDetails persists new shipping details when type is shipping")
    void updateAddressDetailsSetsShippingDetailsAndSavesWhenTypeIsShipping() {
        // given
        Order existingOrder = orderBase();
        ShippingDetails newShipping = new ShippingDetails();
        newShipping.setCity("Wroclaw");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShippingDetails(newShipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateAddressDetails(ORDER_ID, "shipping", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getShippingDetails().getCity()).isEqualTo("Wroclaw");
    }

    @Test
    @DisplayName("updateShipments publishes ShipmentCreated when the saved order has a shipment with shipping data")
    void updateShipmentsPublishesShipmentCreatedWhenShippingDataPresent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRACK-1");
        shipment.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher).publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
    }

    @Test
    @DisplayName("updateShipments keeps a shipment that only carries a manually entered collection point")
    void updateShipmentsKeepsShipmentWithCollectionPointOnly() {
        // given
        Order existingOrder = orderBase();
        Shipment dispatched = new Shipment(ShipmentType.Courier);
        dispatched.setCarrier("DPD");
        dispatched.setTrackingNo("TRACK-1");
        dispatched.setShippedAt(LocalDateTime.now());
        Shipment pointOnly = new Shipment(ShipmentType.PickupPoint);
        pointOnly.setCollectionPointCode("KRA01M");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(dispatched, pointOnly));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        assertThat(existingOrder.getShipments()).hasSize(2);
        assertThat(existingOrder.getShipments().get(1).getCollectionPointCode()).isEqualTo("KRA01M");
    }

    @Test
    @DisplayName("updateShipments lets the operator change a pickup-point shipment into a courier one and drop the point")
    void updateShipmentsAppliesOperatorTypeChangeFromPickupPointToCourier() {
        // given
        Order existingOrder = orderBase();
        Shipment locker = new Shipment(ShipmentType.PickupPoint);
        locker.setCarrier("InPost");
        locker.setCollectionPointCode("KRA01M");
        existingOrder.setShipments(new ArrayList<>(List.of(locker)));
        Shipment courier = new Shipment(ShipmentType.Courier);
        courier.setCarrier("DPD");
        courier.setTrackingNo("TRACK-9");
        courier.setShippedAt(LocalDateTime.now());
        courier.setCollectionPointCode(" ");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(courier));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        Shipment saved = existingOrder.getShipments().get(0);
        assertThat(saved.getType()).isEqualTo(ShipmentType.Courier);
        assertThat(saved.getCollectionPointCode()).isNull();
        assertThat(saved.getCarrier()).isEqualTo("DPD");
    }

    @Test
    @DisplayName("updateShipments does not publish ShipmentCreated when no shipment has shipping data")
    void updateShipmentsSkipsPublishWhenShippingDataAbsent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.PersonalCollection);
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("updateShipments publishes ShipmentCreated when the saved order has a personal-collection shipment")
    void updateShipmentsPublishesShipmentCreatedWhenCollectionDataPresent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.PersonalCollection);
        shipment.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher).publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
    }

    @Test
    @DisplayName("updateShipments leaves existing shipments untouched when the payload carries no shipments list")
    void updateShipmentsKeepsExistingShipmentsWhenPayloadShipmentsIsNull() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRACK-1");
        shipment.setShippedAt(LocalDateTime.now());
        existingOrder.setShipments(List.of(shipment));
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(null);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        assertThat(existingOrder.getShipments()).containsExactly(shipment);
        verifyNoInteractions(orderLifecycleEventPublisher);
    }

    @Test
    @DisplayName("updateShipments does not republish ShipmentCreated when resubmitted shipment data is unchanged")
    void updateShipmentsDoesNotRepublishWhenShipmentDataUnchanged() {
        // given
        LocalDateTime shippedAt = LocalDateTime.now();
        Order existingOrder = orderBase();
        Shipment existingShipment = new Shipment(ShipmentType.Courier);
        existingShipment.setCarrier("DPD");
        existingShipment.setTrackingNo("TRACK-1");
        existingShipment.setShippedAt(shippedAt);
        existingOrder.setShipments(List.of(existingShipment));
        Shipment resubmittedShipment = new Shipment(ShipmentType.Courier);
        resubmittedShipment.setCarrier("DPD");
        resubmittedShipment.setTrackingNo("TRACK-1");
        resubmittedShipment.setShippedAt(shippedAt);
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedShipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("updateShipments does not republish ShipmentCreated when only sub-minute shippedAt precision differs")
    void updateShipmentsDoesNotRepublishWhenShippedAtLosesSubMinutePrecision() {
        // given
        Order existingOrder = orderBase();
        Shipment existingShipment = new Shipment(ShipmentType.Courier);
        existingShipment.setCarrier("DPD");
        existingShipment.setTrackingNo("TRACK-1");
        existingShipment.setShippedAt(LocalDateTime.of(2026, 7, 2, 14, 31, 22));
        existingOrder.setShipments(List.of(existingShipment));
        Shipment resubmittedShipment = new Shipment(ShipmentType.Courier);
        resubmittedShipment.setCarrier("DPD");
        resubmittedShipment.setTrackingNo("TRACK-1");
        resubmittedShipment.setShippedAt(LocalDateTime.of(2026, 7, 2, 14, 31));
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedShipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("updateShipments does not publish ShipmentCreated when the order is cancelled")
    void updateShipmentsDoesNotPublishWhenOrderIsCancelled() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.Cancelled);
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRACK-1");
        shipment.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("updateShipments republishes ShipmentCreated when the resubmitted tracking number changes")
    void updateShipmentsPublishesWhenTrackingNumberChanges() {
        // given
        LocalDateTime shippedAt = LocalDateTime.now();
        Order existingOrder = orderBase();
        Shipment existingShipment = new Shipment(ShipmentType.Courier);
        existingShipment.setCarrier("DPD");
        existingShipment.setTrackingNo("TRACK-1");
        existingShipment.setShippedAt(shippedAt);
        existingOrder.setShipments(List.of(existingShipment));
        Shipment resubmittedShipment = new Shipment(ShipmentType.Courier);
        resubmittedShipment.setCarrier("DPD");
        resubmittedShipment.setTrackingNo("TRACK-2");
        resubmittedShipment.setShippedAt(shippedAt);
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedShipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher).publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
    }

    @Test
    @DisplayName("updateShipments keeps tracking subscription of an unchanged shipment and subscribes new ones")
    void updateShipmentsKeepsTrackingSubscriptionOfUnchangedShipmentAndSubscribesNewOnes() {
        // given
        Order existingOrder = orderBase();
        Shipment tracked = new Shipment(ShipmentType.Courier);
        tracked.setCarrier("DPD");
        tracked.setTrackingNo("PKG-1");
        tracked.setShippedAt(LocalDateTime.now());
        tracked.markTrackingActive("21037943");
        existingOrder.setShipments(new ArrayList<>(List.of(tracked)));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        Shipment resubmittedFirst = new Shipment(ShipmentType.Courier);
        resubmittedFirst.setCarrier("DPD");
        resubmittedFirst.setTrackingNo("PKG-1");
        resubmittedFirst.setShippedAt(LocalDateTime.now());
        Shipment resubmittedSecond = new Shipment(ShipmentType.Courier);
        resubmittedSecond.setCarrier("DPD");
        resubmittedSecond.setTrackingNo("PKG-2");
        resubmittedSecond.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedFirst, resubmittedSecond));

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        assertThat(existingOrder.getShipments()).hasSize(2);
        assertThat(existingOrder.getShipments().get(0).getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(existingOrder.getShipments().get(1).hasTrackingSubscription()).isFalse();
        verify(shipmentTrackingSubscriber).subscribe(STORE_ID, existingOrder);
        InOrder order = inOrder(shipmentTrackingSubscriber, orderLifecycle);
        order.verify(shipmentTrackingSubscriber).subscribe(STORE_ID, existingOrder);
        order.verify(orderLifecycle).update(existingOrder);
    }

    @Test
    void savingItemAppliesThePostedServiceFlag() {
        // given
        OrderItem item = existingOrderItem("Obudowy", false);
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isTrue();
        verify(orderItemsRepository).save(item);
    }

    @Test
    void savingItemCanClearTheServiceFlag() {
        // given
        OrderItem item = existingOrderItem("Obudowy", true);
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(false);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isFalse();
    }

    @Test
    void savingItemWithBlankCategoryNormalizesItToNull() {
        // given
        OrderItem item = existingOrderItem(null, true);
        OrderItem posted = postedOrderItem("");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getCategory()).isNull();
        assertThat(item.isService()).isTrue();
    }

    @Test
    void turningServiceFlagOnMarksItemDeliveredAndMovesItToServiceBand() {
        // given
        OrderItem item = existingOrderItem("Obudowy", false);
        item.setPosition(2);
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isTrue();
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(item.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        assertThat(item.getPosition()).isEqualTo(PositionGroup.SERVICE_GROUP_START + 2);
    }

    @Test
    void turningServiceFlagOffResetsWarehouseFulfilledItemToNewProduct() {
        // given
        OrderItem item = existingOrderItem("Usługi", true);
        item.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        item.setStatus(FulfilmentStatus.Delivered);
        item.setPosition(PositionGroup.SERVICE_GROUP_START + 2);
        OrderItem posted = postedOrderItem("Usługi");
        posted.setService(false);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isFalse();
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(item.getDeliveryId()).isNull();
        assertThat(item.getPosition()).isEqualTo(2);
    }

    @Test
    void postedServiceFlagIsIgnoredWhenItemHasSupplierAllocation() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("delivery-1");
        item.setStatus(FulfilmentStatus.Ordered);
        item.setPosition(2);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isFalse();
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getDeliveryId()).isEqualTo("delivery-1");
        assertThat(item.getPosition()).isEqualTo(2);
    }

    @Test
    void postedPriceIsAppliedToAllocatedItemWhenOrderHasNoDocuments() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("delivery-1");
        item.setStatus(FulfilmentStatus.Ordered);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setPrice(150.0);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getPrice()).isEqualTo(150.0);
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getDeliveryId()).isEqualTo("delivery-1");
    }

    @Test
    void postedPriceIsIgnoredWhenOrderHasDocuments() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(invoicedOrder());
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setPrice(150.0);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getPrice()).isEqualTo(100.0);
    }

    @Test
    void savingItemRefusesADeliveryIdTheMarketplaceDidNotChoose() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId("Bravo");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder("2"));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));
        when(messageSource.getMessage(eq("order.item.assign.supplier.routed"), any(), any(Locale.class))).thenReturn("routed");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, model);

        // then
        assertThat(item.getDeliveryId()).isNull();
        assertThat(model.getAttribute("errorMessage")).isEqualTo("routed");
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    void savingItemAcceptsTheDeliveryIdTheMarketplaceChose() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId("Acme");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder("2"));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getDeliveryId()).isEqualTo("Acme");
        verify(orderItemsRepository).save(item);
    }

    @Test
    @DisplayName("removeDocument removes the invoice and saves the order without triggering the lifecycle")
    void removeDocumentRemovesInvoiceAndSavesWithoutLifecycle() {
        // given
        Order existingOrder = invoicedOrder();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.removeDocument(ORDER_ID, pl.commercelink.documents.DocumentType.InvoiceVat, "FV/1/2026", redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getDocuments()).isEmpty();
        verify(ordersRepository).save(existingOrder);
        verifyNoInteractions(orderLifecycle);
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("removeDocument sets error flash and does not save when the order is completed")
    void removeDocumentRejectsCompletedOrder() {
        // given
        Order existingOrder = invoicedOrder();
        existingOrder.setStatus(OrderStatus.Completed);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("error.message.document.cannot.be.removed"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Cannot remove");

        // when
        ordersController.removeDocument(ORDER_ID, pl.commercelink.documents.DocumentType.InvoiceVat, "FV/1/2026", redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getDocuments()).hasSize(1);
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Cannot remove"));
    }

    @Test
    @DisplayName("removeDocument sets error flash and does not save when the document is a warehouse document")
    void removeDocumentRejectsWarehouseDocument() {
        // given
        Order existingOrder = orderBase();
        existingOrder.addDocument(new pl.commercelink.documents.Document(
                "wz-1", "WZ/1/2026", null, pl.commercelink.documents.DocumentType.GoodsIssue));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("error.message.document.cannot.be.removed"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Cannot remove");

        // when
        ordersController.removeDocument(ORDER_ID, pl.commercelink.documents.DocumentType.GoodsIssue, "WZ/1/2026", redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getDocuments()).hasSize(1);
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Cannot remove"));
    }

    @Test
    void clearSupplierReleasesAnAllocatedItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("Acme");
        item.setStatus(FulfilmentStatus.Allocation);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(item.getDeliveryId()).isNull();
        assertThat(item.getClaimedDeliveryId()).isNull();
        verify(orderItemsRepository).save(item);
        verifyNoInteractions(redirectAttributes);
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void clearSupplierKeepsWorkingForNewItems() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setDeliveryId("Acme");
        item.setStatus(FulfilmentStatus.New);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(item.getDeliveryId()).isNull();
        verify(orderItemsRepository).save(item);
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void clearSupplierRefusesAnOrderedItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("d-1");
        item.setClaimedDeliveryId("d-1");
        item.setStatus(FulfilmentStatus.Ordered);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(messageSource.getMessage(eq("order.item.clear.assign.blocked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("blocked");

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getDeliveryId()).isEqualTo("d-1");
        assertThat(item.getClaimedDeliveryId()).isEqualTo("d-1");
        verify(orderItemsRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void clearSupplierRefusesADeliveredItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("d-1");
        item.setClaimedDeliveryId("d-1");
        item.setStatus(FulfilmentStatus.Delivered);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(messageSource.getMessage(eq("order.item.clear.assign.blocked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("blocked");

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(item.getDeliveryId()).isEqualTo("d-1");
        assertThat(item.getClaimedDeliveryId()).isEqualTo("d-1");
        verify(orderItemsRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void assignSupplierRefusesAnOrderedItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("d-1");
        item.setClaimedDeliveryId("d-1");
        item.setStatus(FulfilmentStatus.Ordered);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(messageSource.getMessage(eq("order.item.assign.supplier.blocked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("blocked");

        // when
        String view = ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-2", 50.0, "d-2", null,
                new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getManufacturerCode()).isEqualTo("MFN-1");
        assertThat(item.getDeliveryId()).isEqualTo("d-1");
        assertThat(item.getClaimedDeliveryId()).isEqualTo("d-1");
        verify(orderItemsRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    private static Store storeRouting(String supplierName, String externalSupplierId) {
        StoreSupplierConnection connection = new StoreSupplierConnection(supplierName, ConnectionMode.OWN);
        connection.setExternalSupplierId(externalSupplierId);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connection)));
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private static Order routedOrder(String externalSupplierId) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setExternalSupplierId(externalSupplierId);
        return order;
    }

    @Test
    void assignSupplierRefusesASupplierTheMarketplaceDidNotChoose() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setManufacturerCode("MFN-1");
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder("2"));
        Store store = storeRouting("Acme", "2");
        StoreSupplierConnection bravo = new StoreSupplierConnection("Bravo", ConnectionMode.GLOBAL);
        bravo.setExternalSupplierId("3");
        store.getSupplierConnections().add(bravo);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(messageSource.getMessage(eq("order.item.assign.supplier.routed"), any(), eq(Locale.ENGLISH)))
                .thenReturn("routed");

        // when
        String view = ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-2", 50.0, "Bravo", null,
                new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getManufacturerCode()).isEqualTo("MFN-1");
        assertThat(item.getDeliveryId()).isNull();
        verify(orderItemsRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "routed");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void assignSupplierAcceptsTheSupplierTheMarketplaceChose() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder("2"));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));
        when(taxonomyCache.findByMfn("MFN-2")).thenReturn(
                new Taxonomy("5901234123457", "MFN-2", "Brand", "Name", "Laptopy", 5, null, null, "raw"));

        // when
        String view = ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-2", 50.0, "Acme", null,
                new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getDeliveryId()).isEqualTo("Acme");
        assertThat(item.getEan()).isEqualTo("5901234123457");
        verify(orderItemsRepository).save(item);
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void assignSupplierRefusesAnIdentityThatIsNotConnectedToTheStore() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));
        when(messageSource.getMessage(eq("order.item.assign.supplier.unknown"), any(), any(Locale.class))).thenReturn("unknown");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-1", 10.0, "Bravo-k7f3a9c2", null,
                new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("unknown");
        assertThat(item.getDeliveryId()).isNull();
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    void assignSupplierRefusesADisabledConnection() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        Store store = storeRouting("Acme", "2");
        store.getSupplierConnections().get(0).setEnabled(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(messageSource.getMessage(eq("order.item.assign.supplier.unknown"), any(), any(Locale.class))).thenReturn("unknown");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-1", 10.0, "Acme", null,
                new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("unknown");
        assertThat(item.getDeliveryId()).isNull();
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    void assignSupplierAcceptsATypedSupplierNameOutsideTheConnections() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));
        when(taxonomyCache.findByMfn("MFN-2")).thenReturn(
                new Taxonomy("5901234123457", "MFN-2", "Brand", "Name", "Laptopy", 5, null, null, "raw"));

        // when
        String view = ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-2", 50.0,
                SupplierChoice.CUSTOM, "  HURT-ABC ", new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getDeliveryId()).isEqualTo("HURT-ABC");
        verify(orderItemsRepository).save(item);
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void assignSupplierRefusesATypedNameOfAnIntegratedSupplier() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));
        when(messageSource.getMessage(eq("order.item.assign.supplier.integrated"), eq(new Object[]{"Stub"}), any(Locale.class)))
                .thenReturn("integrated");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-1", 10.0, SupplierChoice.CUSTOM, "Stub",
                new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("integrated");
        assertThat(item.getDeliveryId()).isNull();
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    void savingItemAcceptsATypedSupplierNameOutsideTheConnections() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId(" HURT-ABC ");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getDeliveryId()).isEqualTo("HURT-ABC");
        verify(orderItemsRepository).save(item);
    }

    @Test
    void savingItemRefusesATokenedIdentityThatIsNotConnected() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId("Bravo-k7f3a9c2");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));
        when(messageSource.getMessage(eq("order.item.assign.supplier.unknown"), any(), any(Locale.class))).thenReturn("unknown");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, model);

        // then
        assertThat(item.getDeliveryId()).isNull();
        assertThat(model.getAttribute("errorMessage")).isEqualTo("unknown");
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateOrderInfo rejects a fulfilment type change once a product item is allocated")
    void updateOrderInfoRejectsFulfilmentTypeChangeWhenAnItemIsAllocated() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.New);
        existingOrder.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.WarehouseFulfilment);
        OrderItem allocated = new OrderItem();
        allocated.setStatus(FulfilmentStatus.Allocation);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocated));
        when(messageSource.getMessage(eq("order.fulfilment.type.locked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Locked");
        Order payload = new Order(STORE_ID);
        payload.setStatus(OrderStatus.New);
        payload.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);

        // when
        String view = ordersController.updateOrderInfo(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Locked");
        verify(orderLifecycle, never()).update(any());
        assertThat(existingOrder.getFulfilmentType())
                .isEqualTo(pl.commercelink.orders.fulfilment.FulfilmentType.WarehouseFulfilment);
    }

    @Test
    @DisplayName("updateOrderInfo applies a fulfilment type change while every product item is New")
    void updateOrderInfoAppliesFulfilmentTypeChangeWhenAllProductsAreNew() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.New);
        existingOrder.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.WarehouseFulfilment);
        OrderItem fresh = new OrderItem();
        fresh.setStatus(FulfilmentStatus.New);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(fresh));
        Order payload = new Order(STORE_ID);
        payload.setStatus(OrderStatus.New);
        payload.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);

        // when
        ordersController.updateOrderInfo(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getFulfilmentType())
                .isEqualTo(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
        verify(orderLifecycle).update(existingOrder);
    }

    @Test
    @DisplayName("updateOrderInfo keeps the fulfilment type when the disabled field is absent from the form")
    void updateOrderInfoKeepsFulfilmentTypeWhenTheFieldIsAbsent() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.New);
        existingOrder.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        Order payload = new Order(STORE_ID);
        payload.setStatus(OrderStatus.New);
        payload.setFulfilmentType(null);

        // when
        ordersController.updateOrderInfo(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getFulfilmentType())
                .isEqualTo(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
        verifyNoInteractions(orderItemsRepository);
    }

    private OrderItem existingOrderItem(String category, boolean service) {
        OrderItem item = new OrderItem(ORDER_ID, category, "pozycja", 1, 100.0, null, false);
        item.setService(service);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        return item;
    }

    private OrderItem postedOrderItem(String category) {
        OrderItem posted = new OrderItem();
        posted.setCategory(category);
        posted.setName("pozycja");
        posted.setQty(1);
        return posted;
    }

    private Order orderBase() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        return order;
    }

    private Order invoicedOrder() {
        Order order = orderBase();
        order.addDocument(new pl.commercelink.documents.Document(
                "fv-1", "FV/1/2026", "https://example.com/fv/1",
                pl.commercelink.documents.DocumentType.InvoiceVat));
        return order;
    }

    @Test
    void movingDropshipItemsToTheWarehouseIsReportedAsSkipped() {
        // given
        OrderItem selected = new OrderItem();
        selected.setItemId("item-1");
        selected.setSelected(true);
        OrderItemsForm form = new OrderItemsForm(List.of(selected));
        when(ordersManager.moveOrderItemsToTheWarehouse(STORE_ID, ORDER_ID, List.of("item-1")))
                .thenReturn(new OrdersManager.Result(new Order(STORE_ID), List.of(), 1));
        when(messageSource.getMessage(eq("order.items.action.move.warehouse.dropship.error"), any(), any()))
                .thenReturn("skipped");

        // when
        String view = ordersController.moveSelectedItemsToTheWarehouse(ORDER_ID, form, redirectAttributes,
                Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "skipped");
    }

    @Test
    void orderDetailsModelKeepsItemActionsAvailableForAPlainOrder() {
        // given
        Order order = orderBase();
        OrderItem itemOne = new OrderItem(ORDER_ID, "Obudowy", "pozycja 1", 1, 100.0, null, false);
        OrderItem itemTwo = new OrderItem(ORDER_ID, "Obudowy", "pozycja 2", 1, 100.0, null, false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(itemOne, itemTwo));
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(eq(STORE_ID), any())).thenReturn(Set.of());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderDetails(ORDER_ID, model);

        // then
        assertThat(model.getAttribute("hasAvailableItemActions")).isEqualTo(true);
    }

    @Nested
    @DisplayName("orders list page")
    class ListPage {

        private static final pl.commercelink.orders.filters.FilterActor ACTOR =
                new pl.commercelink.orders.filters.FilterActor(STORE_ID, "user-1", false);

        private org.springframework.util.MultiValueMap<String, String> params(String... keyValues) {
            var map = new org.springframework.util.LinkedMultiValueMap<String, String>();
            for (int i = 0; i < keyValues.length; i += 2) {
                map.add(keyValues[i], keyValues[i + 1]);
            }
            return map;
        }

        private pl.commercelink.web.orders.OrdersPageModel emptyPage(pl.commercelink.web.orders.OrderListQuery query) {
            return new pl.commercelink.web.orders.OrdersPageModel(query, List.of(), List.of(), List.of(), "", List.of(),
                    Optional.empty(), false, List.of(), "", java.util.Map.of(), List.of(),
                    pl.commercelink.web.orders.Pagination.of(1, 0, 50, n -> "/x"), null, List.of());
        }

        @BeforeEach
        void user() {
            var user = mock(pl.commercelink.starter.security.model.CustomUser.class);
            when(user.getAttributes()).thenReturn(java.util.Map.of("sub", "user-1"));
            securityStub.when(CustomSecurityContext::getLoggedInUser).thenReturn(Optional.of(user));
            securityStub.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(false);
            when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
            when(orderFilters.list(ACTOR)).thenReturn(new pl.commercelink.orders.filters.services.ListOrderFiltersView(List.of(), List.of()));
            when(orderListService.page(eq(ACTOR), any(), any(), any())).thenAnswer(inv -> emptyPage(inv.getArgument(1)));
        }

        @Test
        void rendersTheListWithThePageModel() {
            ExtendedModelMap model = new ExtendedModelMap();
            String view = ordersController.orders(params("status", "New"), Locale.forLanguageTag("pl"), model);
            assertThat(view).isEqualTo("orders/list");
            var page = (pl.commercelink.web.orders.OrdersPageModel) model.get("page");
            assertThat(page.query().statuses()).containsExactly(OrderStatus.New);
            assertThat(model.get("filters")).isNotNull();
            assertThat(model.get("canManageStoreFilters")).isEqualTo(false);
        }

        @Test
        void entryWithoutParametersOpensOnTheStarredFilterAndItsStatus() {
            var starred = pl.commercelink.orders.filters.model.OrderFilter.of("Do wysłania", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "Assembled")));
            when(orderFilters.list(ACTOR)).thenReturn(new pl.commercelink.orders.filters.services.ListOrderFiltersView(List.of(), List.of(starred), Optional.of(starred)));

            String view = ordersController.orders(params(), Locale.forLanguageTag("pl"), new ExtendedModelMap());

            assertThat(view).isEqualTo("redirect:/dashboard/orders?status=Assembled&filterId=" + starred.getId());
        }

        @Test
        void explicitEmptyFilterIdAndAnyOtherParameterSkipTheStarRedirect() {
            var starred = pl.commercelink.orders.filters.model.OrderFilter.of("X", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.ShipmentType, "Courier")));
            when(orderFilters.list(ACTOR)).thenReturn(new pl.commercelink.orders.filters.services.ListOrderFiltersView(List.of(), List.of(starred), Optional.of(starred)));

            assertThat(ordersController.orders(params("filterId", ""), Locale.forLanguageTag("pl"), new ExtendedModelMap())).isEqualTo("orders/list");
            assertThat(ordersController.orders(params("status", "New"), Locale.forLanguageTag("pl"), new ExtendedModelMap())).isEqualTo("orders/list");
            assertThat(ordersController.orders(params("q", "x"), Locale.forLanguageTag("pl"), new ExtendedModelMap())).isEqualTo("orders/list");
            // a starred filter without a status condition redirects to the filter alone
            assertThat(ordersController.orders(params(), Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isEqualTo("redirect:/dashboard/orders?filterId=" + starred.getId());
        }

        @Test
        void legacyParametersRedirect() {
            assertThat(ordersController.orders(params("statuses", "Blocked", "showAll", "false"), Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isEqualTo("redirect:/dashboard/orders?status=Blocked");
            assertThat(ordersController.orders(params("showAll", "true"), Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isEqualTo("redirect:/dashboard/orders");
        }

        @Test
        void fragmentEndpointRendersOnlyTheResults() {
            ExtendedModelMap model = new ExtendedModelMap();
            String view = ordersController.ordersList(params("focus", "overdue"), Locale.forLanguageTag("pl"), model);
            assertThat(view).isEqualTo("orders/list :: results");
            assertThat(((pl.commercelink.web.orders.OrdersPageModel) model.get("page")).query().focus()).isEqualTo(pl.commercelink.orders.OrderAttention.Overdue);
        }

        @Test
        void starEndpointsReturnToTheGivenAddressAndCallTheService() {
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            assertThat(ordersController.setDefaultFilter("f1", "/dashboard/orders?status=New", null, redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders?status=New");
            verify(orderFilters).setDefault(ACTOR, "f1");
            assertThat(ordersController.clearDefaultFilter("/dashboard/orders", null, redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders");
            verify(orderFilters).clearDefault(ACTOR);
        }

        @Test
        void returnToOutsideTheListIsIgnored() {
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            assertThat(ordersController.setDefaultFilter("f1", "https://evil.example/x", null, redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders");
            assertThat(ordersController.setDefaultFilter("f1", "/dashboard/store/x", null, redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders");
        }

        @Test
        void createWithMakeDefaultOpensTheNewFilter() {
            var created = pl.commercelink.orders.filters.model.OrderFilter.of("Nowy", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "New")));
            when(orderFilters.create(eq(ACTOR), eq(false), eq("Nowy"), any(), eq(true))).thenReturn(created);
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Nowy");
            form.setStatus("New");
            form.setMakeDefault(true);
            form.setReturnTo("/dashboard/orders?q=x");

            String view = ordersController.createOrderFilter(form, null, new RedirectAttributesModelMap(), new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            assertThat(view).isEqualTo("redirect:/dashboard/orders?status=New&filterId=" + created.getId() + "&q=x");
        }

        @Test
        void deletingTheActiveFilterDropsItFromTheReturnAddress() {
            String view = ordersController.deleteOrderFilter("f1", "/dashboard/orders?status=New&filterId=f1", null, new RedirectAttributesModelMap(), new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());
            assertThat(view).isEqualTo("redirect:/dashboard/orders?status=New");
            verify(orderFilters).delete(ACTOR, "f1");
        }

        @Test
        void rejectedFilterBecomesAFlashOnRedirectAnd422OnFetch() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("");
            form.setReturnTo("/dashboard/orders");
            when(orderFilters.create(any(), anyBoolean(), any(), any(), anyBoolean()))
                    .thenThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.no.label"));
            when(messageSource.getMessage(eq("orders.filters.error.no.label"), any(), any(Locale.class))).thenReturn("Filtr musi mieć nazwę.");

            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            assertThat(ordersController.createOrderFilter(form, null, redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse())).isEqualTo("redirect:/dashboard/orders");
            assertThat(redirect.getFlashAttributes().get("filterError")).isEqualTo("Filtr musi mieć nazwę.");

            ExtendedModelMap model = new ExtendedModelMap();
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            String fragment = ordersController.createOrderFilter(form, "fetch", redirect, model, Locale.forLanguageTag("pl"), response);
            assertThat(fragment).isEqualTo("orders/filters :: dialogBody");
            assertThat(model.get("filterError")).isEqualTo("Filtr musi mieć nazwę.");
            assertThat(response.getStatus()).isEqualTo(422);
        }

        @Test
        void rejectedUpdateKeepsTheSubmittedFieldsAndTheEditedFilterId() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Do wysłania jutro");
            form.setStatus("New");
            form.setReturnTo("/dashboard/orders");
            org.mockito.Mockito.doThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.no.label"))
                    .when(orderFilters).update(any(), any(), anyBoolean(), any(), any());
            when(messageSource.getMessage(eq("orders.filters.error.no.label"), any(), any(Locale.class))).thenReturn("Filtr musi mieć nazwę.");

            ExtendedModelMap model = new ExtendedModelMap();
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            String fragment = ordersController.updateOrderFilter("f1", form, "fetch", new RedirectAttributesModelMap(), model, Locale.forLanguageTag("pl"), response);

            assertThat(fragment).isEqualTo("orders/filters :: dialogBody");
            assertThat(response.getStatus()).isEqualTo(422);
            var filterForm = (pl.commercelink.web.dtos.OrderFilterForm) model.get("filterForm");
            assertThat(filterForm.getLabel()).isEqualTo("Do wysłania jutro");
            assertThat(filterForm.getStatus()).isEqualTo("New");
            assertThat(model.get("filterId")).isEqualTo("f1");
        }

        @Test
        void successfulFetchReturnsTheDialogBodyWithTheRedirectAddress() {
            var created = pl.commercelink.orders.filters.model.OrderFilter.of("Nowy", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "New")));
            when(orderFilters.create(eq(ACTOR), eq(false), eq("Nowy"), any(), eq(false))).thenReturn(created);
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Nowy");
            form.setReturnTo("/dashboard/orders?q=x");

            ExtendedModelMap model = new ExtendedModelMap();
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            String fragment = ordersController.createOrderFilter(form, "fetch", new RedirectAttributesModelMap(), model, Locale.forLanguageTag("pl"), response);

            assertThat(fragment).isEqualTo("orders/filters :: dialogBody");
            assertThat(model.get("redirectTo")).isEqualTo("/dashboard/orders?q=x");
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        void rejectedSaveViewDialogRerendersTheSaveViewBodyWithThePage() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("");
            form.setDialog("save-view");
            form.setReturnTo("/dashboard/orders");
            when(orderFilters.create(any(), anyBoolean(), any(), any(), anyBoolean()))
                    .thenThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.no.label"));
            when(messageSource.getMessage(eq("orders.filters.error.no.label"), any(), any(Locale.class))).thenReturn("Filtr musi mieć nazwę.");

            ExtendedModelMap model = new ExtendedModelMap();
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            String fragment = ordersController.createOrderFilter(form, "fetch", new RedirectAttributesModelMap(), model, Locale.forLanguageTag("pl"), response);

            assertThat(fragment).isEqualTo("orders/filters :: saveViewBody");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(model.get("page")).isNotNull();
            assertThat(((pl.commercelink.web.dtos.OrderFilterForm) model.get("filterForm")).getLabel()).isEqualTo("");
        }

        @Test
        void malformedReturnToFallsBackToTheBareList() {
            String view = ordersController.deleteOrderFilter("f1", "/dashboard/orders?x=%", null,
                    new RedirectAttributesModelMap(), new ExtendedModelMap(), Locale.forLanguageTag("pl"),
                    new org.springframework.mock.web.MockHttpServletResponse());
            assertThat(view).isEqualTo("redirect:/dashboard/orders");
            verify(orderFilters).delete(ACTOR, "f1");
        }

        @Test
        void deleteConfirmationPageShowsTheFilterLabelAndPostsBackToDelete() {
            var filter = pl.commercelink.orders.filters.model.OrderFilter.of("Do wysłania", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "New")));
            when(orderFilters.list(ACTOR)).thenReturn(new pl.commercelink.orders.filters.services.ListOrderFiltersView(List.of(), List.of(filter)));
            when(messageSource.getMessage(eq("orders.filters.delete.title"), any(), any(Locale.class))).thenReturn("Usunąć filtr „Do wysłania”?");
            when(messageSource.getMessage(eq("orders.filters.delete.message"), any(), any(Locale.class))).thenReturn("...");
            when(messageSource.getMessage(eq("orders.filters.delete.action"), any(), any(Locale.class))).thenReturn("Usuń filtr");
            when(messageSource.getMessage(eq("orders.filters.page.back"), any(), any(Locale.class))).thenReturn("‹ Zamówienia");

            ExtendedModelMap model = new ExtendedModelMap();
            String view = ordersController.confirmDeleteOrderFilter(filter.getId(), "/dashboard/orders", Locale.forLanguageTag("pl"), model);

            assertThat(view).isEqualTo("settings-confirm");
            var confirm = (pl.commercelink.web.settings.ConfirmAction) model.get("confirm");
            assertThat(confirm.actionPath()).contains(filter.getId());
        }

        @Test
        void deleteConfirmationPageIs404ForAnUnknownFilter() {
            assertThatThrownBy(() -> ordersController.confirmDeleteOrderFilter("unknown", "/dashboard/orders",
                    Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }
}
