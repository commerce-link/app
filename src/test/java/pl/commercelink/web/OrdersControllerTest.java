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

import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.OrderReferenceResolver;
import pl.commercelink.shipping.ShipmentCancelService;
import pl.commercelink.web.dtos.AssignSupplierForm;
import pl.commercelink.web.orders.MoveTargetView;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.orders.OrderPageModel;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.orders.OrderSettingsView;
import pl.commercelink.web.settings.ConfirmAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
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
    @Mock
    private OrderPageModelFactory pageModelFactory;
    @Mock
    private OrderReferenceResolver orderReferenceResolver;
    @Mock
    private ShipmentCancelService shipmentCancelService;

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
        String view = ordersController.addOrderItems(ORDER_ID, form, redirectAttributes, Locale.ENGLISH);

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
        assertThatThrownBy(() -> ordersController.addOrderItems(ORDER_ID, form, redirectAttributes, Locale.ENGLISH))
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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        String view = ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateShipments(ORDER_ID, updatedPayload, redirectAttributes, Locale.ENGLISH);

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
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
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
        String view = ordersController.assignSupplier(ORDER_ID, AssignSupplierForm.of(item.getItemId(), "MFN-2", "50", "net", "d-2", null), null,
                new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

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
        String view = ordersController.assignSupplier(ORDER_ID, AssignSupplierForm.of(item.getItemId(), "MFN-2", "50", "net", "Bravo", null), null,
                new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

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
        String view = ordersController.assignSupplier(ORDER_ID, AssignSupplierForm.of(item.getItemId(), "MFN-2", "50", "net", "Acme", null), null,
                new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

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
        ordersController.assignSupplier(ORDER_ID, AssignSupplierForm.of(item.getItemId(), "MFN-1", "10", "net", "Bravo-k7f3a9c2", null), null,
                new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

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
        ordersController.assignSupplier(ORDER_ID, AssignSupplierForm.of(item.getItemId(), "MFN-1", "10", "net", "Acme", null), null,
                new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

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
        String view = ordersController.assignSupplier(ORDER_ID,
                AssignSupplierForm.of(item.getItemId(), "MFN-2", "50", "net", SupplierChoice.CUSTOM, "  HURT-ABC "), null,
                new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

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
        ordersController.assignSupplier(ORDER_ID, AssignSupplierForm.of(item.getItemId(), "MFN-1", "10", "net", SupplierChoice.CUSTOM, "Stub"), null,
                new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

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
        String view = ordersController.updateOrderInfo(ORDER_ID, payload, null, new MockHttpServletResponse(), new ExtendedModelMap(),
                redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateOrderInfo(ORDER_ID, payload, null, new MockHttpServletResponse(), new ExtendedModelMap(),
                redirectAttributes, Locale.ENGLISH);

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
        ordersController.updateOrderInfo(ORDER_ID, payload, null, new MockHttpServletResponse(), new ExtendedModelMap(),
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getFulfilmentType())
                .isEqualTo(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
        verify(orderLifecycle).update(existingOrder);
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
            return new pl.commercelink.web.orders.OrdersPageModel(query, List.of(), List.of(), "", List.of(),
                    Optional.empty(), List.of(), "", java.util.Map.of(), List.of(),
                    pl.commercelink.web.orders.Pagination.of(1, 0, 50, n -> "/x"), null);
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
        void entryWithoutParametersOpensTheOpenList() {
            var saved = pl.commercelink.orders.filters.model.OrderFilter.of("Do wysłania", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "Assembled")));
            when(orderFilters.list(ACTOR)).thenReturn(new pl.commercelink.orders.filters.services.ListOrderFiltersView(List.of(), List.of(saved)));
            // no start filter any more: a saved filter never opens by itself
            ExtendedModelMap model = new ExtendedModelMap();
            assertThat(ordersController.orders(params(), Locale.forLanguageTag("pl"), model)).isEqualTo("orders/list");
            var query = ((pl.commercelink.web.orders.OrdersPageModel) model.get("page")).query();
            assertThat(query.isOpen()).isTrue();
            assertThat(query.hasFilter()).isFalse();
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
            String view = ordersController.ordersList(params("status", "Blocked"), Locale.forLanguageTag("pl"), model);
            assertThat(view).isEqualTo("orders/list :: results");
            assertThat(((pl.commercelink.web.orders.OrdersPageModel) model.get("page")).query().statuses()).containsExactly(OrderStatus.Blocked);
        }

        @Test
        void returnToOutsideTheListIsIgnored() {
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            assertThat(ordersController.deleteOrderFilter("f1", "https://evil.example/x", redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders");
            assertThat(ordersController.deleteOrderFilter("f1", "/dashboard/store/x", redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders");
        }

        @Test
        void creatingAFilterReturnsToTheManagementPage() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Nowy");
            form.setStatus("New");
            String manage = OrdersController.filtersPage("/dashboard/orders?q=x");
            form.setReturnTo(manage);

            assertThat(ordersController.createOrderFilter(form, new RedirectAttributesModelMap(), new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:" + manage);
            verify(orderFilters).create(eq(ACTOR), eq(false), eq("Nowy"), any());
        }

        @Test
        void deletingTheActiveFilterDropsItFromTheReturnAddress() {
            String view = ordersController.deleteOrderFilter("f1", "/dashboard/orders?status=New&filterId=f1", new RedirectAttributesModelMap(), new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());
            assertThat(view).isEqualTo("redirect:/dashboard/orders?status=New");
            verify(orderFilters).delete(ACTOR, "f1");
        }

        @Test
        void deletingFromTheManagementPageReturnsThereAndDropsTheFilterFromItsListAddress() {
            String manage = OrdersController.filtersPage("/dashboard/orders?status=New&filterId=f1");
            String view = ordersController.deleteOrderFilter("f1", manage, new RedirectAttributesModelMap(), new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());
            assertThat(view).isEqualTo("redirect:" + OrdersController.filtersPage("/dashboard/orders?status=New"));
        }

        @Test
        void managementPageBacksToTheListAndReturnsItsFormsToItself() {
            ExtendedModelMap model = new ExtendedModelMap();
            assertThat(ordersController.orderFiltersPage("/dashboard/orders?q=x", Locale.forLanguageTag("pl"), model)).isEqualTo("orders/filters");
            assertThat(model.get("listHref")).isEqualTo("/dashboard/orders?q=x");
            assertThat(model.get("returnTo")).isEqualTo("/dashboard/orders/filters?returnTo=%2Fdashboard%2Forders%3Fq%3Dx");
            // a foreign address never becomes the way back
            ExtendedModelMap foreign = new ExtendedModelMap();
            ordersController.orderFiltersPage("https://evil.example/x", Locale.forLanguageTag("pl"), foreign);
            assertThat(foreign.get("listHref")).isEqualTo("/dashboard/orders");
        }

        @Test
        void returnAddressesAcceptTheListAndTheManagementPageOnly() {
            String manage = OrdersController.filtersPage("/dashboard/orders?status=New");
            assertThat(OrdersController.safeReturnTo(manage)).isEqualTo(manage);
            assertThat(OrdersController.listOf(manage)).isEqualTo("/dashboard/orders?status=New");
            assertThat(OrdersController.safeReturnTo("/dashboard/orders/filters?returnTo=https%3A%2F%2Fevil.example"))
                    .isEqualTo(OrdersController.filtersPage("/dashboard/orders"));
            assertThat(OrdersController.safeReturnTo("/dashboard/orders/filters/f1/edit")).isEqualTo("/dashboard/orders");
            assertThat(OrdersController.safeReturnTo("/dashboard/store")).isEqualTo("/dashboard/orders");
        }

        @Test
        void editSubpageFillsTheFormFromTheFilterAndHidesOtherPeoplesFilters() {
            var own = pl.commercelink.orders.filters.model.OrderFilter.of("Allegro nowe", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "New"),
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.SourceName, "Allegro")));
            var shared = pl.commercelink.orders.filters.model.OrderFilter.of("Sklepowy", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "Blocked")));
            when(orderFilters.list(ACTOR)).thenReturn(new pl.commercelink.orders.filters.services.ListOrderFiltersView(List.of(shared), List.of(own)));
            when(messageSource.getMessage(eq("orders.filters.edit.title"), any(), any(Locale.class))).thenReturn("Edytuj filtr „Allegro nowe”");

            ExtendedModelMap model = new ExtendedModelMap();
            assertThat(ordersController.editOrderFilterPage(own.getId(), "/dashboard/orders", Locale.forLanguageTag("pl"), model)).isEqualTo("orders/filter-edit");
            var form = (pl.commercelink.web.dtos.OrderFilterForm) model.get("filterForm");
            assertThat(form.getLabel()).isEqualTo("Allegro nowe");
            assertThat(form.getStatus()).isEqualTo("New");
            assertThat(form.getSourceName()).isEqualTo("Allegro");
            assertThat(form.isSharedWithStore()).isFalse();
            assertThat(model.get("filterId")).isEqualTo(own.getId());
            assertThat(model.get("formAction")).isEqualTo("/dashboard/orders/filters/update");
            assertThat(model.get("returnTo")).isEqualTo(OrdersController.filtersPage("/dashboard/orders"));

            // a store filter is read-only for a non-admin, an unknown id is not there at all
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> ordersController.editOrderFilterPage(shared.getId(), null, Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> ordersController.editOrderFilterPage("nope", null, Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void addSubpageIsAnEmptyCreateForm() {
            ExtendedModelMap model = new ExtendedModelMap();
            assertThat(ordersController.addOrderFilterPage("/dashboard/orders", Locale.forLanguageTag("pl"), model)).isEqualTo("orders/filter-edit");
            assertThat(((pl.commercelink.web.dtos.OrderFilterForm) model.get("filterForm")).getLabel()).isNull();
            assertThat(model.get("filterId")).isNull();
            assertThat(model.get("formAction")).isEqualTo("/dashboard/orders/filters");
            // the list shows open orders only, so the filter's Status field offers the open statuses only
            assertThat(model.get("statuses")).isEqualTo(pl.commercelink.orders.OrderListService.OPEN);
        }

        @Test
        void rejectedCreateRendersTheSubpageAgainWith422() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("");
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders"));
            when(orderFilters.create(any(), anyBoolean(), any(), any()))
                    .thenThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.no.label"));
            when(messageSource.getMessage(eq("orders.filters.error.no.label"), any(), any(Locale.class))).thenReturn("Filtr musi mieć nazwę.");

            ExtendedModelMap model = new ExtendedModelMap();
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            assertThat(ordersController.createOrderFilter(form, new RedirectAttributesModelMap(), model, Locale.forLanguageTag("pl"), response))
                    .isEqualTo("orders/filter-edit");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(model.get("filterError")).isEqualTo("Filtr musi mieć nazwę.");
            assertThat(model.get("formAction")).isEqualTo("/dashboard/orders/filters");
        }

        @Test
        void rejectedDeleteBecomesAFlashForThePageItReturnsTo() {
            org.mockito.Mockito.doThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.not.found"))
                    .when(orderFilters).delete(any(), any());
            when(messageSource.getMessage(eq("orders.filters.error.not.found"), any(), any(Locale.class))).thenReturn("Nie ma takiego filtra.");

            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            assertThat(ordersController.deleteOrderFilter("f1", "/dashboard/orders?status=New", redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders?status=New");
            assertThat(redirect.getFlashAttributes().get("filterError")).isEqualTo("Nie ma takiego filtra.");
        }

        @Test
        void rejectedUpdateKeepsTheSubmittedFieldsAndTheEditedFilterId() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Do wysłania jutro");
            form.setStatus("New");
            form.setReturnTo("/dashboard/orders/filters?returnTo=%2Fdashboard%2Forders%3Fq%3Dx");
            org.mockito.Mockito.doThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.no.label"))
                    .when(orderFilters).update(any(), any(), anyBoolean(), any(), any());
            when(messageSource.getMessage(eq("orders.filters.error.no.label"), any(), any(Locale.class))).thenReturn("Filtr musi mieć nazwę.");

            ExtendedModelMap model = new ExtendedModelMap();
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            // the filter subpage posts plainly: a rejection renders that page again, with a 422
            String view = ordersController.updateOrderFilter("f1", form, new RedirectAttributesModelMap(), model, Locale.forLanguageTag("pl"), response);

            assertThat(view).isEqualTo("orders/filter-edit");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(model.get("filterError")).isEqualTo("Filtr musi mieć nazwę.");
            assertThat(model.get("returnTo")).isEqualTo("/dashboard/orders/filters?returnTo=%2Fdashboard%2Forders%3Fq%3Dx");
            assertThat(model.get("formAction")).isEqualTo("/dashboard/orders/filters/update");
            var filterForm = (pl.commercelink.web.dtos.OrderFilterForm) model.get("filterForm");
            assertThat(filterForm.getLabel()).isEqualTo("Do wysłania jutro");
            assertThat(filterForm.getStatus()).isEqualTo("New");
            assertThat(model.get("filterId")).isEqualTo("f1");
        }

        @Test
        void malformedReturnToFallsBackToTheBareList() {
            String view = ordersController.deleteOrderFilter("f1", "/dashboard/orders?x=%",
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

    /** The redesigned details page: page model, status and settings endpoints, guards, confirmations, bulk results. */
    @Nested
    class DetailsPage {

        private final Locale polish = Locale.forLanguageTag("polish");

        @BeforeEach
        void messagesEchoTheirKeys() {
            when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenAnswer(invocation -> {
                Object[] args = invocation.getArgument(1);
                return args == null || args.length == 0 ? invocation.getArgument(0)
                        : invocation.getArgument(0) + " " + java.util.Arrays.toString(args);
            });
        }

        private Order order(OrderStatus status) {
            Order order = orderBase();
            order.setStatus(status);
            return order;
        }

        private OrderItem item(String itemId, FulfilmentStatus status, String sku) {
            OrderItem item = new OrderItem(ORDER_ID, "CPU", "Ryzen", 1, 100, sku, false);
            item.setItemId(itemId);
            item.setStatus(status);
            return item;
        }

        private Document document(DocumentType type, String number) {
            return new Document(number, number, null, type);
        }

        private OrderItemsForm selected(String... itemIds) {
            List<OrderItem> items = new ArrayList<>();
            for (String itemId : itemIds) {
                OrderItem item = new OrderItem();
                item.setItemId(itemId);
                item.setSelected(true);
                items.add(item);
            }
            return new OrderItemsForm(items);
        }

        private Map<String, Object> flash(RedirectAttributesModelMap redirect) {
            return new java.util.HashMap<>(redirect.getFlashAttributes());
        }

        private OrderNotice notice(RedirectAttributesModelMap redirect) {
            return (OrderNotice) redirect.getFlashAttributes().get(OrderFlash.ATTRIBUTE);
        }

        @Test
        void getOrderDetailsBuildsThePageModelWithTheValidatedReturnTo() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
            securityStub.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
            OrderPageModel page = mock(OrderPageModel.class);
            OrderSettingsView settings = mock(OrderSettingsView.class);
            when(page.settings()).thenReturn(settings);
            when(pageModelFactory.build(eq(order), anyList(),
                    eq(new OrderPageModelFactory.Viewer(false, true, "/dashboard/orders?status=New")), any())).thenReturn(page);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.getOrderDetails(ORDER_ID, "/dashboard/orders?status=New", model, polish);

            // then
            assertThat(view).isEqualTo("orders/details");
            assertThat(model.getAttribute("page")).isSameAs(page);
            assertThat(model.getAttribute("settings")).isSameAs(settings);
            assertThat(model.getAttribute("orderId")).isEqualTo(ORDER_ID);
            assertThat(model.getAttribute("order")).isSameAs(order);
        }

        @Test
        void aReturnToOutsideTheListFallsBackToTheBareListBeforeTheFactorySeesIt() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(pageModelFactory.build(eq(order), anyList(), any(), any())).thenReturn(mock(OrderPageModel.class));

            // when
            ordersController.getOrderDetails(ORDER_ID, "https://evil.example/dashboard/orders", new ExtendedModelMap(), polish);

            // then
            ArgumentCaptor<OrderPageModelFactory.Viewer> viewer = ArgumentCaptor.forClass(OrderPageModelFactory.Viewer.class);
            verify(pageModelFactory).build(eq(order), anyList(), viewer.capture(), any());
            assertThat(viewer.getValue().back()).isEqualTo("/dashboard/orders");
        }

        @Test
        void getOrderDetailsForSuperAdminBuildsAReadOnlyViewer() {
            // given
            Order order = order(OrderStatus.New);
            order.setStoreId("store-9");
            when(ordersRepository.findById("store-9", ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
            when(pageModelFactory.build(eq(order), anyList(), eq(new OrderPageModelFactory.Viewer(true, false, null)), any()))
                    .thenReturn(mock(OrderPageModel.class));

            // when
            String view = ordersController.getOrderDetailsForSuperAdmin("store-9", ORDER_ID, new ExtendedModelMap(), polish);

            // then
            assertThat(view).isEqualTo("orders/details");
        }

        @Test
        void unknownOrderAnswers404() {
            // given
            when(ordersRepository.findById(STORE_ID, "nope")).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> ordersController.getOrderDetails("nope", null, new ExtendedModelMap(), Locale.ROOT))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.changeStatus("nope", "Assembly", new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void statusPageListsTheOptionsOfTheOrder() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.statusPage(ORDER_ID, model, polish);

            // then
            assertThat(view).isEqualTo("orders/status");
            assertThat(model).containsKeys("statusOptions", "orderId", "shortId");
        }

        @Test
        void changeStatusRefusesDeliveredWithoutShipmentDataCompletedAndUnknownValues() {
            // given
            Order order = order(OrderStatus.Assembled);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap delivered = new RedirectAttributesModelMap();
            RedirectAttributesModelMap completed = new RedirectAttributesModelMap();
            RedirectAttributesModelMap bogus = new RedirectAttributesModelMap();

            // when
            ordersController.changeStatus(ORDER_ID, "Delivered", delivered, polish);
            ordersController.changeStatus(ORDER_ID, "Completed", completed, polish);
            ordersController.changeStatus(ORDER_ID, "Bogus", bogus, polish);

            // then
            assertThat(flash(delivered)).containsEntry("errorMessage", "error.message.delivered.requires.shipment.data");
            assertThat(flash(completed)).containsEntry("errorMessage", "error.message.completed.cannot.be.set.manually");
            assertThat(flash(bogus)).containsEntry("errorMessage", "order.status.error.none");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.Assembled);
            verify(orderLifecycle, never()).update(any());
        }

        @Test
        void changeStatusRefusesAClosedOrder() {
            // given
            Order order = order(OrderStatus.Completed);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.changeStatus(ORDER_ID, "New", redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.status.error.closed");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.Completed);
        }

        @Test
        void changeStatusSavesAndReportsAnAutomaticFollowUp() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            doAnswer(inv -> {
                order.setStatus(OrderStatus.Assembled);
                return null;
            }).when(orderLifecycle).update(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.changeStatus(ORDER_ID, "Assembly", redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(notice(redirect).tone()).isEqualTo("is-warn");
            assertThat(notice(redirect).text()).startsWith("order.status.changed.auto");
        }

        @Test
        void changeStatusConfirmsAChoiceThatStuck() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.changeStatus(ORDER_ID, "Blocked", redirect, polish);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.Blocked);
            verify(orderLifecycle).update(order);
            assertThat(notice(redirect).tone()).isEqualTo("is-ok");
            assertThat(notice(redirect).text()).startsWith("order.status.changed");
        }

        @Test
        void settingsPageRendersTheSameFormAsTheDialog() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
            when(pageModelFactory.settings(eq(order), anyList(), eq(false))).thenReturn(mock(OrderSettingsView.class));
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.settingsPage(ORDER_ID, model, Locale.ROOT);

            // then
            assertThat(view).isEqualTo("orders/settings");
            assertThat(model).containsKeys("settings", "orderId", "shortId");
        }

        @Test
        void updateOrderInfoIgnoresAPostedStatusAndAnswersTheFragmentWhenAsync() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
            when(pageModelFactory.settings(eq(order), anyList(), eq(false))).thenReturn(mock(OrderSettingsView.class));
            Order posted = new Order(STORE_ID);
            posted.setStatus(OrderStatus.Delivered);
            posted.setComment("x");
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.updateOrderInfo(ORDER_ID, posted, "fetch", response, model,
                    new RedirectAttributesModelMap(), Locale.ROOT);

            // then
            assertThat(view).isEqualTo("orders/details/settings :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(model.getAttribute("settingsSaved")).isEqualTo("order.settings.saved");
            assertThat(model.getAttribute("settingsError")).isNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.New);
            assertThat(order.getComment()).isEqualTo("x");
            verify(orderLifecycle).update(order);
        }

        @Test
        void updateOrderInfoAnswers422WithTheReasonWhenAsyncAndTheFulfilmentTypeIsLocked() {
            // given
            Order order = order(OrderStatus.New);
            order.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.WarehouseFulfilment);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item("i1", FulfilmentStatus.Allocation, "MFN-1")));
            Order posted = new Order(STORE_ID);
            posted.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.updateOrderInfo(ORDER_ID, posted, "fetch", response, model,
                    new RedirectAttributesModelMap(), Locale.ROOT);

            // then
            assertThat(view).isEqualTo("orders/details/settings :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(model.getAttribute("settingsError")).isEqualTo("order.fulfilment.type.locked");
            verify(orderLifecycle, never()).update(any());
        }

        @Test
        void theConfirmationPagesPostBackToTheirOwnAddress() {
            // given
            Order order = order(OrderStatus.Shipping);
            order.addDocument(document(DocumentType.InvoiceVat, "FV/1"));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            String base = "/dashboard/orders/" + ORDER_ID;

            // when
            ExtendedModelMap delete = new ExtendedModelMap();
            ExtendedModelMap cancel = new ExtendedModelMap();
            ExtendedModelMap goodsOut = new ExtendedModelMap();
            ExtendedModelMap shipment = new ExtendedModelMap();
            ExtendedModelMap unpin = new ExtendedModelMap();
            List<String> views = List.of(
                    ordersController.confirmDeleteOrder(ORDER_ID, delete, polish),
                    ordersController.confirmCancelOrder(ORDER_ID, cancel, polish),
                    ordersController.confirmGoodsOut(ORDER_ID, goodsOut, polish),
                    ordersController.confirmCancelShipment(ORDER_ID, shipment, polish),
                    ordersController.confirmRemoveDocument(ORDER_ID, DocumentType.InvoiceVat, "FV/1", unpin, polish));

            // then
            assertThat(views).containsOnly("settings-confirm");
            assertThat(((ConfirmAction) delete.get("confirm")).actionPath()).isEqualTo(base + "/delete");
            assertThat(((ConfirmAction) cancel.get("confirm")).actionPath()).isEqualTo(base + "/cancel");
            assertThat(((ConfirmAction) goodsOut.get("confirm")).actionPath()).isEqualTo(base + "/goods-out");
            assertThat(((ConfirmAction) goodsOut.get("confirm")).destructive()).isFalse();
            assertThat(((ConfirmAction) shipment.get("confirm")).actionPath()).isEqualTo(base + "/cancelShipment");
            assertThat(((ConfirmAction) unpin.get("confirm")).actionPath()).startsWith(base + "/removeDocument");
            assertThat(((ConfirmAction) delete.get("confirm")).cancelPath()).isEqualTo(base);
            verifyNoInteractions(ordersManager, shipmentCancelService);
        }

        @Test
        void theRemoveDocumentConfirmationOfAMissingDocumentGoesBackToTheOrder() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));

            // when
            String view = ordersController.confirmRemoveDocument(ORDER_ID, DocumentType.InvoiceVat, "FV/9", new ExtendedModelMap(), polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        }

        @Test
        void theRemoveSupplierEntryIsConfirmedOnAPageFirst() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));
            when(orderItemsRepository.findById(ORDER_ID, "i1")).thenReturn(item("i1", FulfilmentStatus.Allocation, "MFN-1"));
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.confirmClearSupplier(ORDER_ID, "i1", model, polish);

            // then
            assertThat(view).isEqualTo("settings-confirm");
            assertThat(((ConfirmAction) model.get("confirm")).actionPath())
                    .isEqualTo("/dashboard/orders/" + ORDER_ID + "/clear-supplier?itemId=i1");
            verify(orderItemsRepository, never()).save(any());
        }

        @Test
        void theRemoveSupplierConfirmationIsNotFoundForAnUnknownItem() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));
            when(orderItemsRepository.findById(ORDER_ID, "missing")).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> ordersController.confirmClearSupplier(ORDER_ID, "missing", new ExtendedModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void addOrderItemsRefusesAClosedInvoicedOrWarehouseIssuedOrder() {
            // given
            Order closed = order(OrderStatus.Completed);
            Order invoiced = order(OrderStatus.Delivered);
            invoiced.addDocument(document(DocumentType.InvoiceVat, "FV/1"));
            Order issued = order(OrderStatus.Assembled);
            issued.addDocument(document(DocumentType.GoodsIssue, "WZ/1"));
            RedirectAttributesModelMap closedRedirect = new RedirectAttributesModelMap();
            RedirectAttributesModelMap invoicedRedirect = new RedirectAttributesModelMap();
            RedirectAttributesModelMap issuedRedirect = new RedirectAttributesModelMap();

            // when
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(closed);
            ordersController.addOrderItems(ORDER_ID, new AddItemsForm(), closedRedirect, polish);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(invoiced);
            ordersController.addOrderItems(ORDER_ID, new AddItemsForm(), invoicedRedirect, polish);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(issued);
            ordersController.addOrderItems(ORDER_ID, new AddItemsForm(), issuedRedirect, polish);

            // then
            assertThat(flash(closedRedirect)).containsEntry("errorMessage", "order.items.add.locked.closed");
            assertThat(flash(invoicedRedirect)).containsEntry("errorMessage", "order.items.add.locked.invoiced");
            assertThat(flash(issuedRedirect)).containsEntry("errorMessage", "order.items.add.locked.goods.issue");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void addOrderItemsRefusesAnOrderWithDropshipItems() {
            // given
            OrderItem shipped = item("i1", FulfilmentStatus.Ordered, "MFN-1");
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(shipped));
            when(dropshipItemLookup.itemIdsInDropshipDeliveries(STORE_ID, List.of(shipped))).thenReturn(Set.of("i1"));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.addOrderItems(ORDER_ID, new AddItemsForm(), redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.items.action.dropship.locked");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void assignSkuRefusesANonNewOrBundleItem() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
            OrderItem ordered = item("i1", FulfilmentStatus.Ordered, "A:B");
            OrderItem group = item("g1", FulfilmentStatus.New, "#1xA|1xB");
            when(orderItemsRepository.findById(ORDER_ID, "i1")).thenReturn(ordered);
            when(orderItemsRepository.findById(ORDER_ID, "g1")).thenReturn(group);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.assignSku(ORDER_ID, "i1", "X:Y", redirect, polish);
            ordersController.assignSku(ORDER_ID, "g1", "X:Y", redirect, polish);

            // then
            assertThat(ordered.getSku()).isEqualTo("A:B");
            assertThat(group.getSku()).isEqualTo("#1xA|1xB");
            verify(orderItemsRepository, never()).save(any());
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.item.assign.sku.locked");
        }

        @Test
        void assignSkuStoresTheCodeOfANewItem() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));
            OrderItem fresh = item("i1", FulfilmentStatus.New, "A:B");
            when(orderItemsRepository.findById(ORDER_ID, "i1")).thenReturn(fresh);

            // when
            ordersController.assignSku(ORDER_ID, "i1", "X:Y", new RedirectAttributesModelMap(), polish);

            // then
            assertThat(fresh.getSku()).isEqualTo("X:Y");
            verify(orderItemsRepository).save(fresh);
        }

        @Test
        void anItemOfAnotherStoresOrderIsNotFound() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> ordersController.assignSku(ORDER_ID, "i1", "X", new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.clearSupplier(ORDER_ID, "i1", new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.updateSerialNumbers(ORDER_ID, new OrderItemsForm()))
                    .isInstanceOf(ResponseStatusException.class);
            verify(orderItemsRepository, never()).save(any());
        }

        @Test
        void toggleConsolidationRefusesOnceInvoiced() {
            // given
            Order order = order(OrderStatus.Delivered);
            order.addDocument(document(DocumentType.InvoiceVat, "FV/1"));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.toggleConsolidation(ORDER_ID, "i1", redirect, polish);

            // then
            verify(orderItemsRepository, never()).save(any());
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.item.consolidation.locked");
        }

        @Test
        void addReceiptRefusesABlankNumber() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Delivered));
            Document blank = new Document();
            blank.setType(DocumentType.Receipt);
            blank.setNumber("  ");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.addReceipt(ORDER_ID, blank, redirect, polish);

            // then
            verify(ordersRepository, never()).save(any());
            verifyNoInteractions(orderLifecycle);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.documents.add.error.number");
        }

        @Test
        void addReceiptStoresTheTrimmedNumber() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Document receipt = new Document();
            receipt.setType(DocumentType.Receipt);
            receipt.setNumber(" PAR/1 ");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.addReceipt(ORDER_ID, receipt, redirect, polish);

            // then
            assertThat(order.getDocuments()).extracting(Document::getNumber).containsExactly("PAR/1");
            verify(orderLifecycle).update(order);
            assertThat(notice(redirect).text()).isEqualTo("order.documents.added");
        }

        @Test
        void updateSerialNumbersTouchesOnlyThePostedItems() {
            // given
            OrderItem posted = item("i1", FulfilmentStatus.Delivered, "MFN-1");
            posted.setSerialNo("OLD");
            OrderItem other = item("i2", FulfilmentStatus.Delivered, "MFN-2");
            other.setSerialNo("KEEP");
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Delivered));
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(posted, other));
            OrderItem draft = new OrderItem();
            draft.setItemId("i1");
            draft.setSerialNo(" NEW ");
            OrderItemsForm form = new OrderItemsForm();
            form.setOrderItems(List.of(draft));

            // when
            ordersController.updateSerialNumbers(ORDER_ID, form);

            // then
            assertThat(posted.getSerialNo()).isEqualTo("NEW");
            assertThat(other.getSerialNo()).isEqualTo("KEEP");
            verify(orderItemsRepository).save(posted);
            verify(orderItemsRepository, never()).save(other);
        }

        @Test
        void bulkActionReportsSkippedItems() {
            // given
            when(ordersManager.moveItemsToAllocation(STORE_ID, ORDER_ID, List.of("i1", "i2")))
                    .thenReturn(new OrdersManager.Result(orderBase(), List.of(), 0, 1, 2));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.moveSelectedItemsToAllocation(ORDER_ID, selected("i1", "i2"), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(notice(redirect).tone()).isEqualTo("is-warn");
            assertThat(notice(redirect).text()).isEqualTo("order.bulk.result [1, 2] order.bulk.skipped.allocation");
        }

        @Test
        void aCompleteBulkActionSaysHowManyItemsChanged() {
            // given
            when(ordersManager.removeFromOrder(STORE_ID, ORDER_ID, List.of("a", "b")))
                    .thenReturn(new OrdersManager.Result(orderBase(), List.of(), 0, 2, 2));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.removeSelectedItemsFromOrder(ORDER_ID, selected("a", "b"), redirect, polish);

            // then
            assertThat(notice(redirect).tone()).isEqualTo("is-ok");
            assertThat(notice(redirect).text()).isEqualTo("order.bulk.result [2, 2]");
        }

        @Test
        void movingDropshipItemsToTheWarehouseIsReportedAsSkipped() {
            // given
            when(ordersManager.moveOrderItemsToTheWarehouse(STORE_ID, ORDER_ID, List.of("item-1")))
                    .thenReturn(new OrdersManager.Result(orderBase(), List.of(), 1, 0, 1));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.moveSelectedItemsToTheWarehouse(ORDER_ID, selected("item-1"), redirect, polish);

            // then
            assertThat(notice(redirect).tone()).isEqualTo("is-warn");
            assertThat(notice(redirect).text()).endsWith("order.bulk.skipped.dropship");
        }

        @Test
        void aBulkActionWithoutSelectedItemsAsksForASelectionAndChangesNothing() {
            // given
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.moveSelectedItemsToTheWarehouseForRMA(ORDER_ID, new OrderItemsForm(List.of()), redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.bulk.none.selected");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void itemsMoveToAnOrderFoundByItsShortNumber() {
            // given
            Order target = new Order(STORE_ID);
            when(orderReferenceResolver.resolve(STORE_ID, "51aa")).thenReturn(OrderReferenceResolver.Resolution.found(target));
            when(ordersManager.moveOrderItemsToOrder(STORE_ID, ORDER_ID, target.getOrderId(), List.of("a"))).thenReturn(target);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.moveItemsToOrder(ORDER_ID, selected("a"), "51aa", redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + target.getOrderId());
            assertThat(notice(redirect).text()).startsWith("order.bulk.move.done");
        }

        @Test
        void anAmbiguousUnknownOrOwnNumberIsRefusedWithoutMovingAnything() {
            // given
            when(orderReferenceResolver.resolve(STORE_ID, "3e37")).thenReturn(OrderReferenceResolver.Resolution.ambiguous(2));
            when(orderReferenceResolver.resolve(STORE_ID, "zzzz")).thenReturn(OrderReferenceResolver.Resolution.notFound());
            when(orderReferenceResolver.resolve(STORE_ID, "self")).thenReturn(OrderReferenceResolver.Resolution.found(orderBase()));
            RedirectAttributesModelMap ambiguous = new RedirectAttributesModelMap();
            RedirectAttributesModelMap unknown = new RedirectAttributesModelMap();
            RedirectAttributesModelMap own = new RedirectAttributesModelMap();

            // when
            ordersController.moveItemsToOrder(ORDER_ID, selected("a"), "3e37", ambiguous, polish);
            ordersController.moveItemsToOrder(ORDER_ID, selected("a"), "zzzz", unknown, polish);
            ordersController.moveItemsToOrder(ORDER_ID, selected("a"), "self", own, polish);

            // then
            assertThat(flash(ambiguous)).containsEntry("errorMessage", "order.move.ambiguous [2]");
            assertThat(flash(unknown)).containsEntry("errorMessage", "order.move.not.found [0]");
            assertThat(flash(own)).containsEntry("errorMessage", "order.move.self [1]");
            verify(ordersManager, never()).moveOrderItemsToOrder(any(), any(), any(), any());
        }

        @Test
        void moveTargetAnswersThePreviewOr404() {
            // given
            Order target = order(OrderStatus.New);
            target.setOrderId("bbbbbbbb-0000-0000-0000-000000000000");
            when(orderReferenceResolver.resolve(STORE_ID, "bbbbbbbb")).thenReturn(OrderReferenceResolver.Resolution.found(target));
            when(orderReferenceResolver.resolve(STORE_ID, "zzz")).thenReturn(OrderReferenceResolver.Resolution.notFound());
            when(orderReferenceResolver.resolve(STORE_ID, "3e37")).thenReturn(OrderReferenceResolver.Resolution.ambiguous(2));
            when(orderReferenceResolver.resolve(STORE_ID, "self")).thenReturn(OrderReferenceResolver.Resolution.found(orderBase()));
            when(orderItemsRepository.findByOrderId(target.getOrderId())).thenReturn(List.of(new OrderItem(), new OrderItem()));

            // when
            ResponseEntity<MoveTargetView> found = ordersController.moveTarget(ORDER_ID, "bbbbbbbb", polish);
            ResponseEntity<MoveTargetView> missing = ordersController.moveTarget(ORDER_ID, "zzz", polish);
            ResponseEntity<MoveTargetView> ambiguous = ordersController.moveTarget(ORDER_ID, "3e37", polish);
            ResponseEntity<MoveTargetView> self = ordersController.moveTarget(ORDER_ID, "self", polish);

            // then
            assertThat(found.getStatusCode().value()).isEqualTo(200);
            assertThat(found.getBody().items()).isEqualTo(2);
            assertThat(found.getBody().canReceiveItems()).isTrue();
            assertThat(found.getBody().amount()).startsWith("general.currency.amount");
            assertThat(missing.getStatusCode().value()).isEqualTo(404);
            assertThat(ambiguous.getStatusCode().value()).isEqualTo(409);
            assertThat(self.getBody().canReceiveItems()).isFalse();
            assertThat(self.getBody().reason()).isEqualTo("order.move.self");
        }

        @Test
        void cancelShipmentRefusesWhenNoShipmentHasALabel() {
            // given
            Order noData = order(OrderStatus.Shipping);
            Order noPackage = order(OrderStatus.Shipping);
            Shipment typedByHand = new Shipment(ShipmentType.Courier);
            typedByHand.setCarrier("DPD");
            typedByHand.setTrackingNo("TRACK-1");
            typedByHand.setShippedAt(LocalDateTime.now());
            noPackage.setShipments(new ArrayList<>(List.of(typedByHand)));
            RedirectAttributesModelMap noDataRedirect = new RedirectAttributesModelMap();
            RedirectAttributesModelMap noPackageRedirect = new RedirectAttributesModelMap();

            // when
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(noData);
            ordersController.cancelShipment(ORDER_ID, noDataRedirect, polish);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(noPackage);
            ordersController.cancelShipment(ORDER_ID, noPackageRedirect, polish);

            // then
            verifyNoInteractions(shipmentCancelService);
            assertThat(flash(noDataRedirect)).containsEntry("errorMessage", "order.shipments.cancel.error.no.data");
            assertThat(flash(noPackageRedirect)).containsEntry("errorMessage", "order.shipments.cancel.error.no.package");
        }

        @Test
        void updatePaymentsRefusesAnEmptyPost() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.updatePayments(ORDER_ID, new Order(STORE_ID), redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.payments.edit.empty");
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void updateShipmentsIgnoresAPostWithoutShipments() {
            // given
            Order order = order(OrderStatus.Shipping);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order posted = new Order(STORE_ID);
            posted.setShipments(new ArrayList<>());

            // when
            String view = ordersController.updateShipments(ORDER_ID, posted, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            verifyNoInteractions(orderLifecycle, shipmentTrackingSubscriber);
        }

        @Test
        void theShippingAddressCannotChangeOnceTheOrderIsShipping() {
            // given
            Order order = order(OrderStatus.Shipping);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            ShippingDetails posted = new ShippingDetails();
            posted.setCity("Wroclaw");
            Order payload = new Order(STORE_ID);
            payload.setShippingDetails(posted);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.updateAddressDetails(ORDER_ID, "shipping", payload, redirect, polish);

            // then
            verify(ordersRepository, never()).save(any());
            assertThat(flash(redirect)).containsEntry("errorMessage", "error.message.shipping.details.locked");
        }

        @Test
        void aMissingEanKeepsTheOperatorInTheDialogWithTheReason() {
            // given
            supplierItem(1.23);
            when(taxonomyCache.findByMfn("MFN-1")).thenReturn(null);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.assignSupplier(ORDER_ID, supplierForm("100", "net"), "fetch",
                    new MockHttpServletRequest(), response, model, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("orders/details/item-dialogs :: supplierForm");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(model.get("supplierError")).isEqualTo("order.item.ean.not.found");
            assertThat(model).containsKeys("orderId", "supplierForm", "suppliers");
            verify(orderItemsRepository, never()).save(any());
        }

        @Test
        void aGrossPurchasePriceIsStoredNetWithTheItemsVat() {
            // given
            OrderItem item = supplierItem(1.23);
            when(taxonomyCache.findByMfn("MFN-1")).thenReturn(
                    new Taxonomy("5901234567890", "MFN-1", "Brand", "name", "CPU", 1, null, null, "raw"));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.assignSupplier(ORDER_ID, supplierForm("123,00", "gross"), null,
                    new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(item.getCost()).isCloseTo(100.0, org.assertj.core.api.Assertions.within(0.001));
            assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Allocation);
            assertThat(notice(redirect).text()).isEqualTo("order.item.supplier.assigned");
        }

        @Test
        void anUnreadableOrNegativePriceIsRefusedBeforeAnythingChanges() {
            // given
            supplierItem(1.23);
            RedirectAttributesModelMap unreadable = new RedirectAttributesModelMap();
            RedirectAttributesModelMap negative = new RedirectAttributesModelMap();

            // when
            ordersController.assignSupplier(ORDER_ID, supplierForm("abc", "net"), null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), unreadable, polish);
            ordersController.assignSupplier(ORDER_ID, supplierForm("-5", "net"), null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), negative, polish);

            // then
            assertThat(flash(unreadable)).containsEntry("errorMessage", "order.item.assign.cost.invalid");
            assertThat(flash(negative)).containsEntry("errorMessage", "order.item.assign.cost.invalid");
            verify(orderItemsRepository, never()).save(any());
        }

        @Test
        void anAsyncSupplierSaveAnswersWithTheFragmentAndQueuesTheNoticeForTheRedirect() {
            // given
            OrderItem item = supplierItem(1.0);
            when(taxonomyCache.findByMfn("MFN-1")).thenReturn(
                    new Taxonomy("5901234567890", "MFN-1", "Brand", "name", "CPU", 1, null, null, "raw"));
            MockHttpServletRequest request = new MockHttpServletRequest();
            FlashMap flashMap = new FlashMap();
            request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);
            request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.assignSupplier(ORDER_ID, supplierForm("100", "net"), "fetch",
                    request, response, model, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("orders/details/item-dialogs :: supplierForm");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(model.get("supplierError")).isNull();
            assertThat(model.get("supplierRedirect")).isEqualTo("/dashboard/orders/" + ORDER_ID);
            assertThat(((OrderNotice) flashMap.get(OrderFlash.ATTRIBUTE)).text()).isEqualTo("order.item.supplier.assigned");
            verify(orderItemsRepository).save(item);
        }

        // a name typed next to "Other supplier…" is accepted without a connection
        private AssignSupplierForm supplierForm(String cost, String priceType) {
            return AssignSupplierForm.of("i1", "MFN-1", cost, priceType, SupplierChoice.CUSTOM, "HURT-ABC");
        }

        private OrderItem supplierItem(double tax) {
            OrderItem item = new OrderItem(ORDER_ID, "CPU", "Ryzen", 1, 1000, "MFN-1", false);
            item.setItemId("i1");
            item.setTax(tax);
            when(orderItemsRepository.findById(ORDER_ID, "i1")).thenReturn(item);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
            when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
            return item;
        }
    }
}
