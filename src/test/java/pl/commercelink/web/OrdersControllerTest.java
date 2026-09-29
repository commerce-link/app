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
import pl.commercelink.orders.OrderReviewStatus;
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
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.web.dtos.OrderItemsForm;
import pl.commercelink.web.dtos.SplitGroupForm;
import pl.commercelink.web.orders.ItemSaleLock;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
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

import org.springframework.http.HttpStatus;
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
import pl.commercelink.web.orders.BulkAction;
import pl.commercelink.web.orders.MoveTargetView;
import pl.commercelink.web.orders.OrderAddressForm;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.orders.OrderPageModel;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.orders.OrderSettingsView;
import pl.commercelink.web.orders.OrderPaymentForm;
import pl.commercelink.web.orders.OrderShipmentForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.time.LocalDate;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private pl.commercelink.invoicing.InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    @Mock
    private ReceiptAttemptService receiptAttemptService;

    // Real resolver over the test classpath registry (`Stub` is a registered supplier type).
    @Spy
    private SupplierChoice supplierChoice = new SupplierChoice(new SupplierRegistry(
            new SupplierProviderFactory(new ProviderConfigurationManager(mock(SecretsManager.class)))));

    @Mock
    private pl.commercelink.inventory.deliveries.DeliveriesRepository deliveriesRepository;
    @Spy
    private pl.commercelink.inventory.deliveries.DeliveryRedirectResolver deliveryRedirectResolver =
            new pl.commercelink.inventory.deliveries.DeliveryRedirectResolver();

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
        BillingDetails newBilling = validBilling();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

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
        BillingDetails newBilling = validBilling();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("order.customer.billing.locked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Billing locked");

        // when
        ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Billing locked"));
    }

    @Test
    @DisplayName("updateAddressDetails persists new shipping details when type is shipping")
    void updateAddressDetailsSetsShippingDetailsAndSavesWhenTypeIsShipping() {
        // given
        Order existingOrder = orderBase();
        ShippingDetails newShipping = validShipping();
        newShipping.setCity("Wroclaw");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShippingDetails(newShipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateAddressDetails(ORDER_ID, "shipping", updatedPayload, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getShippingDetails().getCity()).isEqualTo("Wroclaw");
    }

    /** One shipment saved or removed at a time from its own dialog of the shipments card. */
    @Nested
    class Shipments {

        private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        @BeforeEach
        void messagesEchoTheirKeys() {
            when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        private Shipment courier(String trackingNo, LocalDateTime shippedAt) {
            Shipment shipment = new Shipment(ShipmentType.Courier);
            shipment.setCarrier("DPD");
            shipment.setTrackingNo(trackingNo);
            shipment.setShippedAt(shippedAt);
            return shipment;
        }

        private Order orderWith(Shipment... shipments) {
            Order order = orderBase();
            order.setShipments(new ArrayList<>(List.of(shipments)));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            return order;
        }

        /** Posts the fields of shipment as its form would: index null adds it, otherwise it replaces the one at index. */
        private String save(Integer index, String version, Shipment shipment, String requestedWith,
                            MockHttpServletResponse response, ExtendedModelMap model) {
            return ordersController.saveShipment(ORDER_ID, index, version, shipment.getType(), shipment.getCarrier(),
                    shipment.getTrackingNo(), shipment.getCollectionPointCode(), shipment.getTrackingUrl(),
                    date(shipment.getShippedAt()), date(shipment.getDeliveredAt()), requestedWith,
                    new MockHttpServletRequest(), response, model, redirect, Locale.ENGLISH);
        }

        private String save(Integer index, String version, Shipment shipment) {
            return save(index, version, shipment, null, new MockHttpServletResponse(), new ExtendedModelMap());
        }

        private String date(LocalDateTime value) {
            return value == null ? null : value.toLocalDate().toString();
        }

        private Object errorMessage() {
            return redirect.getFlashAttributes().get("errorMessage");
        }

        @Test
        void aNewShipmentWithShippingDataIsAddedAndAnnounced() {
            // given
            Order order = orderWith(courier("TRACK-0", LocalDateTime.of(2026, 9, 1, 9, 0)));

            // when
            save(null, null, courier("TRACK-1", LocalDateTime.of(2026, 9, 2, 10, 30)));

            // then
            assertThat(order.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("TRACK-0", "TRACK-1");
            // the form posts the date alone: a new date starts at midnight
            assertThat(order.getShipments().get(1).getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 0, 0));
            verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.ShipmentCreated);
        }

        @Test
        void anIncompleteShipmentIsStoredAsTypedNextToACompleteOne() {
            // given: the whole-list save dropped a courier shipment without carrier or date when another qualified
            Order order = orderWith(courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0)));
            Shipment trackingOnly = new Shipment(ShipmentType.Courier);
            trackingOnly.setTrackingNo("TRACK-2");

            // when
            save(null, null, trackingOnly);

            // then
            assertThat(order.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("TRACK-1", "TRACK-2");
        }

        @Test
        void aShipmentWithOnlyACollectionPointIsStored() {
            // given
            Order order = orderWith(courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0)));
            Shipment pointOnly = new Shipment(ShipmentType.PickupPoint);
            pointOnly.setCollectionPointCode(" KRA01M ");

            // when
            save(null, null, pointOnly);

            // then
            assertThat(order.getShipments()).hasSize(2);
            assertThat(order.getShipments().get(1).getCollectionPointCode()).isEqualTo("KRA01M");
        }

        @Test
        void thePickupPointOfAShipmentTurnedIntoACourierOneIsDropped() {
            // given
            Shipment locker = new Shipment(ShipmentType.PickupPoint);
            locker.setCarrier("InPost");
            locker.setCollectionPointCode("KRA01M");
            Order order = orderWith(locker);
            Shipment courier = courier("TRACK-9", LocalDateTime.of(2026, 9, 2, 10, 30));
            courier.setCollectionPointCode(" ");

            // when
            save(0, OrderShipmentForm.version(locker), courier);

            // then
            Shipment saved = order.getShipments().get(0);
            assertThat(saved.getType()).isEqualTo(ShipmentType.Courier);
            assertThat(saved.getCollectionPointCode()).isNull();
            assertThat(saved.getCarrier()).isEqualTo("DPD");
        }

        @Test
        void editingOneShipmentLeavesTheOthersAsTheyWere() {
            // given
            Shipment first = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            first.setExternalId("PKG-1");
            Shipment second = courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0));
            Order order = orderWith(first, second);
            Shipment edited = courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0));
            edited.setCarrier("GLS");

            // when
            save(1, OrderShipmentForm.version(second), edited);

            // then
            assertThat(order.getShipments().get(0)).isSameAs(first);
            assertThat(order.getShipments().get(1).getCarrier()).isEqualTo("GLS");
        }

        @Test
        void aNewEmptyShipmentIsRefusedInTheDialog() {
            // given: it would only hold the order back from Delivered, which needs a delivery date on every shipment
            orderWith();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            save(null, null, new Shipment(ShipmentType.PersonalCollection), "fetch", response, model);

            // then
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(((OrderShipmentForm) model.getAttribute("shipment")).errors())
                    .containsEntry("shipment-new-trackingNo", "order.shipments.error.empty");
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void addingAShipmentWithoutShippingDataDoesNotAnnounceTheOneAlreadySent() {
            // given
            Order order = orderWith(courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0)));
            Shipment carrierOnly = new Shipment(ShipmentType.Courier);
            carrierOnly.setCarrier("GLS");

            // when
            save(null, null, carrierOnly);

            // then
            assertThat(order.getShipments()).hasSize(2);
            verify(orderLifecycleEventPublisher, never()).publish(any(), any());
        }

        @Test
        void clearingADateClearsTheSavedMoment() {
            // given
            Shipment existing = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 14, 30));
            Order order = orderWith(existing);

            // when
            ordersController.saveShipment(ORDER_ID, 0, OrderShipmentForm.version(existing), ShipmentType.Courier, "DPD",
                    "TRACK-1", null, null, "", null, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

            // then
            assertThat(order.getShipments().get(0).getShippedAt()).isNull();
        }

        @Test
        void aPersonalCollectionWithADateIsAnnounced() {
            // given
            Order order = orderWith();
            Shipment collection = new Shipment(ShipmentType.PersonalCollection);
            collection.setShippedAt(LocalDateTime.of(2026, 9, 2, 12, 0));

            // when
            save(null, null, collection);

            // then
            verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.ShipmentCreated);
        }

        @Test
        void reSavingAnUntouchedShipmentKeepsItsSecondsAndIsNotAnnouncedAgain() {
            // given: the form shows the date only; the tracking stored the moment to the second
            Shipment existing = courier("TRACK-1", LocalDateTime.of(2026, 7, 2, 14, 31, 22));
            Order order = orderWith(existing);

            // when
            save(0, OrderShipmentForm.version(existing), courier("TRACK-1", LocalDateTime.of(2026, 7, 2, 0, 0)));

            // then
            assertThat(order.getShipments().get(0).getShippedAt()).isEqualTo(LocalDateTime.of(2026, 7, 2, 14, 31, 22));
            verify(orderLifecycleEventPublisher, never()).publish(any(), any());
        }

        @Test
        void aChangedTrackingNumberIsAnnounced() {
            // given
            Shipment existing = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            Order order = orderWith(existing);

            // when
            save(0, OrderShipmentForm.version(existing), courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0)));

            // then
            verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.ShipmentCreated);
        }

        @Test
        void anEditedShipmentKeepsItsTrackingAndCourierOrderWhileTheNumberIsTheSame() {
            // given
            Shipment tracked = courier("PKG-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            tracked.setExternalId("EXT-1");
            tracked.markTrackingActive("21037943");
            Order order = orderWith(tracked);
            Shipment edited = courier("PKG-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            edited.setTrackingUrl("https://tracking.example/PKG-1");

            // when
            save(0, OrderShipmentForm.version(tracked), edited);

            // then
            Shipment saved = order.getShipments().get(0);
            assertThat(saved.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
            assertThat(saved.getExternalId()).isEqualTo("EXT-1");
            InOrder inOrder = inOrder(shipmentTrackingSubscriber, orderLifecycle);
            inOrder.verify(shipmentTrackingSubscriber).subscribe(STORE_ID, order);
            inOrder.verify(orderLifecycle).update(order);
        }

        @Test
        void aShipmentChangedSinceTheFormWasShownIsNotOverwritten() {
            // given: the tracking filled the delivery date after the dialog was rendered
            Shipment rendered = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            String version = OrderShipmentForm.version(rendered);
            rendered.setDeliveredAt(LocalDateTime.of(2026, 9, 2, 11, 0));
            orderWith(rendered);

            // when
            String view = save(0, version, courier("TRACK-9", LocalDateTime.of(2026, 9, 1, 9, 0)));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(errorMessage()).isEqualTo("order.shipments.error.stale");
            verifyNoInteractions(orderLifecycle, orderLifecycleEventPublisher, shipmentTrackingSubscriber);
        }

        @Test
        void aCancelledOrderRefusesTheSaveAndAnnouncesNothing() {
            // given
            Order order = orderWith();
            order.setStatus(OrderStatus.Cancelled);

            // when
            String view = save(null, null, courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0)));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(errorMessage()).isEqualTo("order.shipments.error.closed");
            verifyNoInteractions(orderLifecycle, orderLifecycleEventPublisher);
        }

        @Test
        void aWrongDateComesBackToTheDialogWith422() {
            // given
            orderWith();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.saveShipment(ORDER_ID, null, null, ShipmentType.Courier, "DPD", "TRACK-1",
                    null, null, "02.09.2026", null, "fetch", new MockHttpServletRequest(), response,
                    model, redirect, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("orders/details/shipments :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(((OrderShipmentForm) model.getAttribute("shipment")).errors())
                    .containsEntry("shipment-new-shippedDate", "order.shipments.error.date");
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void theSavedDateKeepsItsTimeAndANewOneStartsAtMidnight() {
            // given
            Shipment existing = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 14, 31, 22));
            Order order = orderWith(existing);

            // when
            ordersController.saveShipment(ORDER_ID, 0, OrderShipmentForm.version(existing), ShipmentType.Courier, "DPD",
                    "TRACK-1", null, null, "2026-09-01", "2026-09-04", null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

            // then
            assertThat(order.getShipments().get(0).getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 14, 31, 22));
            assertThat(order.getShipments().get(0).getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 0, 0));
        }

        @Test
        void aChangedDateStartsAtMidnight() {
            // given
            Shipment existing = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 14, 31, 22));
            Order order = orderWith(existing);

            // when
            ordersController.saveShipment(ORDER_ID, 0, OrderShipmentForm.version(existing), ShipmentType.Courier, "DPD",
                    "TRACK-1", null, null, "2026-09-03", null, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

            // then
            assertThat(order.getShipments().get(0).getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 3, 0, 0));
        }

        @Test
        void removingAShipmentKeepsTheOthersAndAnnouncesNothing() {
            // given
            Shipment first = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            Shipment second = courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0));
            Order order = orderWith(first, second);

            // when
            String view = ordersController.removeShipment(ORDER_ID, 0, OrderShipmentForm.version(first), redirect, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(order.getShipments()).containsExactly(second);
            verify(orderLifecycle).update(order);
            verify(orderLifecycleEventPublisher, never()).publish(any(), any());
        }

        @Test
        void theOnlyShipmentOfAShippingOrderIsRemoved() {
            // given: OrderLifecycle keeps an order without shipments in Shipping (OrderLifecycleTest)
            Shipment only = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            Order order = orderWith(only);
            order.setStatus(OrderStatus.Shipping);

            // when
            String view = ordersController.removeShipment(ORDER_ID, 0, OrderShipmentForm.version(only), redirect, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(errorMessage()).isNull();
            assertThat(order.getShipments()).isEmpty();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.Shipping);
            verify(orderLifecycle).update(order);
            verify(orderLifecycleEventPublisher, never()).publish(any(), any());
        }

        @Test
        void theRemovalConfirmationOfTheOnlyShipmentSaysTheDeliveryMethodGoesWithIt() {
            // given
            Shipment only = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            orderWith(only);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            ordersController.confirmRemoveShipment(ORDER_ID, 0, OrderShipmentForm.version(only), model, redirect, Locale.ENGLISH);

            // then
            ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
            assertThat(confirm.message()).isEqualTo("order.shipments.remove.confirm.message.last");
            assertThat(confirm.actionPath()).endsWith("/shipments/0/remove?version=" + OrderShipmentForm.version(only));
        }

        @Test
        void noShipmentOfADeliveredOrderIsRemoved() {
            // given
            Shipment first = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            Shipment second = courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0));
            Order order = orderWith(first, second);
            order.setStatus(OrderStatus.Delivered);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String confirm = ordersController.confirmRemoveShipment(ORDER_ID, 1, OrderShipmentForm.version(second), model,
                    redirect, Locale.ENGLISH);
            ordersController.removeShipment(ORDER_ID, 1, OrderShipmentForm.version(second), redirect, Locale.ENGLISH);

            // then
            assertThat(confirm).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(errorMessage()).isEqualTo("order.shipments.remove.error.delivered");
            assertThat(order.getShipments()).containsExactly(first, second);
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void aShipmentWithADeliveryDateIsNotRemoved() {
            // given
            Shipment delivered = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            delivered.setDeliveredAt(LocalDateTime.of(2026, 9, 2, 11, 0));
            Order order = orderWith(delivered, courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0)));
            order.setStatus(OrderStatus.Shipping);

            // when
            ordersController.removeShipment(ORDER_ID, 0, OrderShipmentForm.version(delivered), redirect, Locale.ENGLISH);

            // then
            assertThat(errorMessage()).isEqualTo("order.shipments.remove.error.shipmentDelivered");
            assertThat(order.getShipments()).hasSize(2);
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void aShipmentWithACourierOrderIsNotRemovedEvenWhenItIsTheOnlyOne() {
            // given: dropping it would orphan the paid label at the carrier
            Shipment labelled = courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0));
            labelled.setExternalId("EXT-2");
            orderWith(labelled);

            // when
            ordersController.removeShipment(ORDER_ID, 0, OrderShipmentForm.version(labelled), redirect, Locale.ENGLISH);

            // then
            assertThat(errorMessage()).isEqualTo("order.shipments.remove.error.courier");
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void aRemovalOfAShipmentChangedMeanwhileIsRefused() {
            // given
            Shipment first = courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0));
            orderWith(first, courier("TRACK-2", LocalDateTime.of(2026, 9, 1, 9, 0)));

            // when
            ordersController.removeShipment(ORDER_ID, 0, "stale", redirect, Locale.ENGLISH);

            // then
            assertThat(errorMessage()).isEqualTo("order.shipments.error.stale");
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void theShipmentPageOfAnUnknownIndexIsNotFound() {
            // given
            orderWith(courier("TRACK-1", LocalDateTime.of(2026, 9, 1, 9, 0)));

            // when / then
            assertThatThrownBy(() -> ordersController.showShipment(ORDER_ID, "3", new ExtendedModelMap(), redirect,
                    Locale.ENGLISH)).isInstanceOf(ResponseStatusException.class);
        }
    }

    /** One payment saved or removed at a time from its own dialog of the payments card. */
    @Nested
    class Payments {

        private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        @BeforeEach
        void messagesEchoTheirKeys() {
            when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        private Order orderWith(Payment... payments) {
            Order order = orderBase();
            order.setPayments(new ArrayList<>(List.of(payments)));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            return order;
        }

        private String save(int index, String version, PaymentSource source, String amount, String fee, String date,
                            String requestedWith, MockHttpServletResponse response, ExtendedModelMap model) {
            return ordersController.savePayment(ORDER_ID, index, version, source, "Jan Kowalski", amount, fee, "REF-9",
                    "OP-9", date, requestedWith, new MockHttpServletRequest(), response, model, redirect, Locale.ENGLISH);
        }

        private String save(int index, String version, String amount) {
            return save(index, version, PaymentSource.BankTransfer, amount, "", null, null,
                    new MockHttpServletResponse(), new ExtendedModelMap());
        }

        private Object errorMessage() {
            return redirect.getFlashAttributes().get("errorMessage");
        }

        @Test
        void editingOnePaymentReplacesItAloneAndGoesThroughTheLifecycle() {
            // given
            Payment first = Payment.bankTransfer("REF-1", "Jan", 100);
            Payment second = Payment.bankTransfer("REF-2", "Jan", 200);
            Order order = orderWith(first, second);

            // when
            String view = save(1, OrderPaymentForm.version(second), PaymentSource.Card, "250,50", "1.5", "2026-09-20",
                    null, new MockHttpServletResponse(), new ExtendedModelMap());

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(order.getPayments().get(0)).isSameAs(first);
            Payment saved = order.getPayments().get(1);
            assertThat(saved.getSource()).isEqualTo(PaymentSource.Card);
            assertThat(saved.getAmount()).isEqualTo(250.5);
            assertThat(saved.getFee()).isEqualTo(1.5);
            assertThat(saved.getReferenceNo()).isEqualTo("REF-9");
            assertThat(saved.getBankTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 20));
            verify(orderLifecycle).update(order);
        }

        @Test
        void aRefundKeepsItsDirectionAndTheSignItWasTypedWith() {
            // given: the add dialog asks for a minus, supplier payouts are stored positive; neither is "fixed"
            Payment typedNegative = new Payment("ZW/1", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing,
                    -100, 0, null, null);
            Payment storedPositive = new Payment("ZW/2", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing,
                    100, 0, null, null);
            Order order = orderWith(typedNegative, storedPositive);

            // when
            save(0, OrderPaymentForm.version(typedNegative), "-120");
            save(1, OrderPaymentForm.version(storedPositive), "120");

            // then
            assertThat(order.getPayments()).extracting(Payment::getDirection)
                    .containsExactly(PaymentDirection.Outgoing, PaymentDirection.Outgoing);
            assertThat(order.getPayments()).extracting(Payment::getAmount).containsExactly(-120.0, 120.0);
        }

        @Test
        void thePendingPaymentMayKeepTheAmountZeroAndChangeItsMethod() {
            // given
            Order order = orderWith(new Payment(PaymentSource.BankTransfer));

            // when
            save(0, OrderPaymentForm.version(order.getPayments().get(0)), PaymentSource.CashOnDelivery, "0", "", null,
                    null, new MockHttpServletResponse(), new ExtendedModelMap());

            // then
            assertThat(order.getPayments().get(0).getSource()).isEqualTo(PaymentSource.CashOnDelivery);
            assertThat(order.getPayments().get(0).isUnsettled()).isTrue();
            verify(orderLifecycle).update(order);
        }

        @Test
        void aSettledPaymentSetToZeroComesBackToTheDialogWith422() {
            // given
            Payment paid = Payment.bankTransfer("REF-1", "Jan", 100);
            orderWith(paid);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = save(0, OrderPaymentForm.version(paid), PaymentSource.BankTransfer, "0", "-1", "20.09.2026",
                    "fetch", response, model);

            // then
            assertThat(view).isEqualTo("orders/details/payments :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(((OrderPaymentForm) model.getAttribute("payment")).errors())
                    .containsEntry("payment-0-amount", "order.payments.error.amount.zero")
                    .containsEntry("payment-0-fee", "order.payments.error.fee")
                    .containsEntry("payment-0-bankTransactionDate", "order.payments.error.date");
            assertThat(paid.getAmount()).isEqualTo(100);
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void aSavedPaymentAnswersTheDialogAndLeavesTheNoticeForTheReloadedPage() {
            // given
            Payment paid = Payment.bankTransfer("REF-1", "Jan", 100);
            orderWith(paid);
            MockHttpServletRequest request = new MockHttpServletRequest();
            FlashMap flashMap = new FlashMap();
            request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);
            request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            String view = ordersController.savePayment(ORDER_ID, 0, OrderPaymentForm.version(paid), PaymentSource.BankTransfer,
                    "Jan", "90", "", "REF-1", null, null, "fetch", request, response, new ExtendedModelMap(), redirect,
                    Locale.ENGLISH);

            // then: 200 with the form lets the dialog close and reload; the notice waits for the order page
            assertThat(view).isEqualTo("orders/details/payments :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(flashMap.getTargetRequestPath()).isEqualTo("/dashboard/orders/" + ORDER_ID);
            assertThat(((OrderNotice) flashMap.get(OrderFlash.ATTRIBUTE)).text()).isEqualTo("order.payments.saved");
        }

        @Test
        void aPaymentChangedSinceTheFormWasShownIsNotOverwritten() {
            // given
            Payment rendered = Payment.bankTransfer("REF-1", "Jan", 100);
            String version = OrderPaymentForm.version(rendered);
            rendered.setFee(2);
            orderWith(rendered);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = save(0, version, "150");
            save(0, version, PaymentSource.BankTransfer, "150", "", null, "fetch", response, model);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(errorMessage()).isEqualTo("order.payments.error.stale");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(((OrderPaymentForm) model.getAttribute("payment")).refusal()).isEqualTo("order.payments.error.stale");
            assertThat(rendered.getAmount()).isEqualTo(100);
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void anIndexPastTheListIsRefusedAsStale() {
            // given
            orderWith(Payment.bankTransfer("REF-1", "Jan", 100));

            // when
            save(3, "any", "150");

            // then
            assertThat(errorMessage()).isEqualTo("order.payments.error.stale");
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void aPaymentOfACompletedOrderCanStillBeSavedAndRemovedLikeWithAddPayment() {
            // given: a refund or a correction on a completed order is legitimate (decision of 2026-09-27)
            Payment paid = Payment.bankTransfer("REF-1", "Jan", 100);
            Payment second = Payment.bankTransfer("REF-2", "Jan", 50);
            Order order = orderWith(paid, second);
            order.setStatus(OrderStatus.Completed);
            RedirectAttributesModelMap removal = new RedirectAttributesModelMap();

            // when
            String view = save(0, OrderPaymentForm.version(paid), "150");
            ordersController.removePayment(ORDER_ID, 1, OrderPaymentForm.version(second), removal, Locale.ENGLISH);
            String page = ordersController.showPayment(ORDER_ID, "0", new ExtendedModelMap(), redirect, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(errorMessage()).isNull();
            assertThat(removal.getFlashAttributes()).doesNotContainKey("errorMessage");
            assertThat(order.getPayments()).hasSize(1);
            assertThat(order.getPayments().get(0).getAmount()).isEqualTo(150);
            assertThat(page).isEqualTo("orders/payment");
            verify(orderLifecycle, times(2)).update(order);
        }

        @Test
        void aPaymentOfACancelledOrderIsRefusedBecauseTheSaveWouldBeDropped() {
            // given: OrderLifecycle.update returns before saving a cancelled order, so a "saved" notice would be a lie
            Payment paid = Payment.bankTransfer("REF-1", "Jan", 100);
            Payment second = Payment.bankTransfer("REF-2", "Jan", 50);
            Order order = orderWith(paid, second);
            order.setStatus(OrderStatus.Cancelled);
            RedirectAttributesModelMap removal = new RedirectAttributesModelMap();
            RedirectAttributesModelMap confirmation = new RedirectAttributesModelMap();
            RedirectAttributesModelMap page = new RedirectAttributesModelMap();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = save(0, OrderPaymentForm.version(paid), "150");
            String asyncView = save(0, OrderPaymentForm.version(paid), PaymentSource.BankTransfer, "150", "", null,
                    "fetch", response, model);
            String removeView = ordersController.removePayment(ORDER_ID, 1, OrderPaymentForm.version(second), removal,
                    Locale.ENGLISH);
            String confirmView = ordersController.confirmRemovePayment(ORDER_ID, 1, OrderPaymentForm.version(second),
                    new ExtendedModelMap(), confirmation, Locale.ENGLISH);
            String pageView = ordersController.showPayment(ORDER_ID, "0", new ExtendedModelMap(), page, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(errorMessage()).isEqualTo("order.payments.error.cancelled");
            assertThat(asyncView).isEqualTo("orders/details/payments :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(((OrderPaymentForm) model.getAttribute("payment")).refusal()).isEqualTo("order.payments.error.cancelled");
            for (RedirectAttributesModelMap flash : List.of(removal, confirmation, page)) {
                assertThat(flash.getFlashAttributes().get("errorMessage")).isEqualTo("order.payments.error.cancelled");
            }
            assertThat(removeView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(confirmView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(pageView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            for (RedirectAttributesModelMap flash : List.of(redirect, removal, confirmation, page)) {
                assertThat(flash.getFlashAttributes()).doesNotContainKey(OrderFlash.ATTRIBUTE);
            }
            assertThat(order.getPayments()).containsExactly(paid, second);
            assertThat(paid.getAmount()).isEqualTo(100);
            verifyNoInteractions(orderLifecycle);
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void removingAPaymentKeepsTheOthersAsTheSameObjects() {
            // given
            Payment first = Payment.bankTransfer("REF-1", "Jan", 100);
            Payment second = Payment.bankTransfer("REF-2", "Jan", 200);
            Payment third = Payment.bankTransfer("REF-3", "Jan", 300);
            Order order = orderWith(first, second, third);

            // when
            String view = ordersController.removePayment(ORDER_ID, 1, OrderPaymentForm.version(second), redirect, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(order.getPayments()).containsExactly(first, third);
            assertThat(redirect.getFlashAttributes()).doesNotContainKey("errorMessage");
            verify(orderLifecycle).update(order);
        }

        @Test
        void removingTheOnlyPaymentLeavesAPendingOneWithTheSameMethod() {
            // given: the order never loses how the customer pays
            Payment card = new Payment("REF-1", "Jan", PaymentSource.Card, 100, 0);
            Order order = orderWith(card);

            // when
            ordersController.removePayment(ORDER_ID, 0, OrderPaymentForm.version(card), redirect, Locale.ENGLISH);

            // then
            assertThat(order.getPayments()).hasSize(1);
            Payment placeholder = order.getPayments().get(0);
            assertThat(placeholder.isUnsettled()).isTrue();
            assertThat(placeholder.getSource()).isEqualTo(PaymentSource.Card);
            assertThat(placeholder.getReferenceNo()).isNull();
            verify(orderLifecycle).update(order);
        }

        @Test
        void thePendingPaymentIsNotRemoved() {
            // given
            Payment pending = new Payment(PaymentSource.BankTransfer);
            Order order = orderWith(Payment.bankTransfer("REF-1", "Jan", 50), pending);

            // when
            ordersController.removePayment(ORDER_ID, 1, OrderPaymentForm.version(pending), redirect, Locale.ENGLISH);

            // then
            assertThat(errorMessage()).isEqualTo("order.payments.remove.error.pending");
            assertThat(order.getPayments()).hasSize(2);
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void aRemovalOfAPaymentChangedMeanwhileIsRefused() {
            // given
            Order order = orderWith(Payment.bankTransfer("REF-1", "Jan", 100), Payment.bankTransfer("REF-2", "Jan", 50));

            // when
            ordersController.removePayment(ORDER_ID, 0, "stale", redirect, Locale.ENGLISH);

            // then
            assertThat(errorMessage()).isEqualTo("order.payments.error.stale");
            assertThat(order.getPayments()).hasSize(2);
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void theRemovalConfirmationOfTheOnlyPaymentSaysAPendingOneStays() {
            // given
            Payment only = Payment.bankTransfer("REF-1", "Jan", 100);
            orderWith(only);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            ordersController.confirmRemovePayment(ORDER_ID, 0, OrderPaymentForm.version(only), model, redirect, Locale.ENGLISH);

            // then
            ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
            assertThat(confirm.message()).isEqualTo("order.payments.remove.confirm.message.last");
            assertThat(confirm.actionPath()).endsWith("/payments/0/remove?version=" + OrderPaymentForm.version(only));
        }

        @Test
        void thePaymentPageOfAnUnknownIndexIsNotFound() {
            // given
            orderWith(Payment.bankTransfer("REF-1", "Jan", 100));

            // when / then
            assertThatThrownBy(() -> ordersController.showPayment(ORDER_ID, "3", new ExtendedModelMap(), redirect,
                    Locale.ENGLISH)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.showPayment(ORDER_ID, "new", new ExtendedModelMap(), redirect,
                    Locale.ENGLISH)).isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void thePaymentPageShowsTheFormOfThePayment() {
            // given
            Payment paid = Payment.bankTransfer("REF-1", "Jan", 100);
            orderWith(Payment.bankTransfer("REF-0", "Jan", 50), paid);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.showPayment(ORDER_ID, "1", model, redirect, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("orders/payment");
            OrderPaymentForm form = (OrderPaymentForm) model.getAttribute("payment");
            assertThat(form.index()).isEqualTo(1);
            assertThat(form.version()).isEqualTo(OrderPaymentForm.version(paid));
        }
    }

    @Test
    void savingItemAppliesThePostedServiceFlag() {
        // given
        OrderItem item = existingOrderItem("Obudowy", false);
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

        // then
        assertThat(item.getPrice()).isEqualTo(100.0);
    }

    @Test
    void saveOrderItemRefusesAClosedOrder() {
        // given
        OrderItem item = existingOrderItem("Obudowy", false);
        Order closed = orderBase();
        closed.setStatus(OrderStatus.Completed);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(closed);
        when(messageSource.getMessage(eq("order.item.error.closed"), any(), any(Locale.class))).thenReturn("closed");
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, model);

        // then
        assertThat(view).isEqualTo("orders/item");
        assertThat(model.getAttribute("itemError")).isEqualTo("closed");
        assertThat(item.isService()).isFalse();
        verify(orderItemsRepository, never()).save(any());
        verifyNoInteractions(orderLifecycle);
    }

    @Test
    void saveOrderItemRefusesACancelledOrder() {
        // given
        OrderItem item = existingOrderItem("Obudowy", false);
        Order cancelled = orderBase();
        cancelled.setStatus(OrderStatus.Cancelled);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(cancelled);
        when(messageSource.getMessage(eq("order.item.error.closed"), any(), any(Locale.class))).thenReturn("closed");
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, model);

        // then
        assertThat(view).isEqualTo("orders/item");
        assertThat(model.getAttribute("itemError")).isEqualTo("closed");
        assertThat(item.isService()).isFalse();
        verify(orderItemsRepository, never()).save(any());
        verifyNoInteractions(orderLifecycle);
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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, model);

        // then
        assertThat(item.getDeliveryId()).isNull();
        assertThat(model.getAttribute("itemError")).isEqualTo("routed");
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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
    @DisplayName("removeDocument refuses to remove a document an e-receipt attempt issued automatically")
    void removeDocumentRefusesAnAutomaticReceipt() {
        // given
        String receiptKey = ORDER_ID + ":R1";
        Order existingOrder = orderBase();
        existingOrder.addDocument(new pl.commercelink.documents.Document(
                receiptKey, receiptKey, null, pl.commercelink.documents.DocumentType.Receipt));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey(receiptKey);
        when(receiptAttemptService.attemptsOf(STORE_ID, ORDER_ID)).thenReturn(List.of(attempt));
        when(messageSource.getMessage(eq("receipts.document.remove.automatic"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Cannot remove automatic receipt");

        // when
        String view = ordersController.removeDocument(ORDER_ID, pl.commercelink.documents.DocumentType.Receipt,
                receiptKey, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getDocuments()).hasSize(1);
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Cannot remove automatic receipt"));
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("addReceipt refuses to add a manual Receipt document while an e-receipt attempt is live")
    void addReceiptRefusesAManualReceiptWhileAnAttemptIsLive() {
        // given
        when(receiptAttemptService.blocksManualReceipt(STORE_ID, ORDER_ID)).thenReturn(true);
        when(messageSource.getMessage(eq("receipts.document.add.live"), any(), eq(Locale.ENGLISH)))
                .thenReturn("An automatic receipt is already being issued");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
        pl.commercelink.documents.Document document = new pl.commercelink.documents.Document(
                null, "PAR/1", null, pl.commercelink.documents.DocumentType.Receipt);

        // when
        String view = ordersController.addReceipt(ORDER_ID, document, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("An automatic receipt is already being issued"));
        verify(ordersRepository, never()).save(any());
        verifyNoInteractions(orderLifecycle);
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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

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
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, model);

        // then
        assertThat(item.getDeliveryId()).isNull();
        assertThat(model.getAttribute("itemError")).isEqualTo("unknown");
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    void savingItemTakesTheNameTypedForOtherSupplier() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId("__custom__");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, " HURT-ABC ", new ExtendedModelMap());

        // then
        assertThat(item.getDeliveryId()).isEqualTo("HURT-ABC");
        verify(orderItemsRepository).save(item);
    }

    @Test
    void savingItemWithoutJavaScriptTakesATypedNameWhenTheSelectStayedEmpty() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId("");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, "HURT-ABC", new ExtendedModelMap());

        // then
        assertThat(item.getDeliveryId()).isEqualTo("HURT-ABC");
    }

    @Test
    void savingItemKeepsAnUnchangedSupplierThatIsNoLongerOffered() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setDeliveryId("Bravo-k7f3a9c2");
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId("__custom__");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, "Bravo-k7f3a9c2", model);

        // then
        assertThat(model.getAttribute("itemError")).isNull();
        assertThat(item.getDeliveryId()).isEqualTo("Bravo-k7f3a9c2");
        verify(orderItemsRepository).save(item);
    }

    @Test
    void savingItemKeepsTheConsolidationFlagOnceTheOrderIsInvoiced() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(invoicedOrder());
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setConsolidated(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, null, new ExtendedModelMap());

        // then
        assertThat(item.isConsolidated()).isFalse();
        verify(orderItemsRepository).save(item);
    }

    static final String DELIVERY_ID = "b58e2f14-0001-4c7a-8b21-demo00000002";

    @Test
    void savingANewItemKeepsTheDeliveryIdItHolds() {
        // given: old data, a new item whose deliveryId is a supplier delivery
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setDeliveryId(DELIVERY_ID);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(new pl.commercelink.inventory.deliveries.Delivery());
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setDeliveryId("__custom__");

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, "HURT-ABC", new ExtendedModelMap());

        // then
        assertThat(item.getDeliveryId()).isEqualTo(DELIVERY_ID);
        verify(orderItemsRepository).save(item);
    }

    @Test
    void theItemPageShowsAnOrderedItemsDeliveryAsTheItemsTableDoes() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.markAsOrdered(DELIVERY_ID, 10.0);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        pl.commercelink.web.orders.OrderItemRow.Delivery cell = new pl.commercelink.web.orders.OrderItemRow.Delivery(
                "b58e2f14", "/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID, true);
        when(pageModelFactory.delivery(any(), eq(item), any(), any())).thenReturn(cell);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderItem(ORDER_ID, item.getItemId(), model);

        // then
        assertThat(model.getAttribute("delivery")).isEqualTo(cell);
        assertThat(model.getAttribute("deliveryHeld")).isEqualTo(true);
        assertThat(model.getAttribute("supplierCustom")).isNull();
        verifyNoInteractions(deliveriesRepository);
    }

    @Test
    void theItemPageOfAnItemWithoutDeliveryOffersNoSupplierAndLooksNothingUp() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderItem(ORDER_ID, item.getItemId(), model);

        // then
        assertThat(model.getAttribute("deliveryHeld")).isEqualTo(false);
        assertThat(model.getAttribute("supplierCurrent")).isNull();
        assertThat(model.getAttribute("supplierCustom")).isNull();
        verifyNoInteractions(deliveriesRepository);
    }

    @Test
    void theItemPageOffersTheStoredSupplierAsAChoiceOrAsATypedName() {
        // given
        OrderItem offered = existingOrderItem("Laptopy", false);
        offered.setDeliveryId("Acme");
        OrderItem typed = new OrderItem(ORDER_ID, "Laptopy", "inna", 1, 100.0, null, false);
        typed.setDeliveryId("HURT-ABC");
        when(orderItemsRepository.findById(ORDER_ID, offered.getItemId())).thenReturn(offered);
        when(orderItemsRepository.findById(ORDER_ID, typed.getItemId())).thenReturn(typed);
        Store store = storeRouting("Acme", null);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(supplierLabels.forStore(any())).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(store));
        ExtendedModelMap offeredModel = new ExtendedModelMap();
        ExtendedModelMap typedModel = new ExtendedModelMap();

        // when
        String view = ordersController.getOrderItem(ORDER_ID, offered.getItemId(), offeredModel);
        ordersController.getOrderItem(ORDER_ID, typed.getItemId(), typedModel);

        // then
        assertThat(view).isEqualTo("orders/item");
        assertThat(offeredModel.getAttribute("supplierCurrent")).isEqualTo("Acme");
        assertThat(offeredModel.getAttribute("supplierCustom")).isNull();
        assertThat(offeredModel.getAttribute("statusKey")).isEqualTo("FulfilmentStatus.New");
        assertThat(typedModel.getAttribute("supplierCurrent")).isNull();
        assertThat(typedModel.getAttribute("supplierCustom")).isEqualTo("HURT-ABC");
        assertThat(typedModel.getAttribute("deliveryHeld")).isEqualTo(false);
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

    @Test
    @DisplayName("updateReview leaves an order without a review when the dialog posts no status")
    void updateReviewWithoutAStatusLeavesAnOrderWithoutAReview() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setReview(null);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        Order payload = new Order(STORE_ID);
        payload.setReview(new pl.commercelink.orders.OrderReview());

        // when
        ordersController.updateReview(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getReview()).isNull();
        verify(orderLifecycle).update(existingOrder);
    }

    @Test
    @DisplayName("updateReview saves the chosen review status")
    void updateReviewSavesAChosenStatus() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setReview(null);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        Order payload = new Order(STORE_ID);
        pl.commercelink.orders.OrderReview posted = new pl.commercelink.orders.OrderReview(OrderReviewStatus.ToBeCollected);
        posted.setReferenceNo("REF-1");
        payload.setReview(posted);

        // when
        ordersController.updateReview(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getReview().getStatus()).isEqualTo(OrderReviewStatus.ToBeCollected);
        assertThat(existingOrder.getReview().getReferenceNo()).isEqualTo("REF-1");
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
        void entryWithoutADefaultFilterOpensTheOpenList() {
            // given
            when(orderListService.defaultFilterHref(ACTOR)).thenReturn(Optional.empty());
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.orders(params(), Locale.forLanguageTag("pl"), model);

            // then
            assertThat(view).isEqualTo("orders/list");
            var query = ((pl.commercelink.web.orders.OrdersPageModel) model.get("page")).query();
            assertThat(query.isOpen()).isTrue();
            assertThat(query.hasFilter()).isFalse();
        }

        @Test
        void entryOpensTheUsersDefaultFilter() {
            // given
            when(orderListService.defaultFilterHref(ACTOR)).thenReturn(Optional.of("/dashboard/orders?status=Assembled&filterId=f1"));

            // when
            String view = ordersController.orders(params("lang", "pl"), Locale.forLanguageTag("pl"), new ExtendedModelMap());

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders?status=Assembled&filterId=f1");
        }

        @Test
        void listWithItsStateNeverJumpsToTheDefaultFilter() {
            // given
            lenient().when(orderListService.defaultFilterHref(ACTOR)).thenReturn(Optional.of("/dashboard/orders?filterId=f1"));
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.orders(params("filterId", ""), Locale.forLanguageTag("pl"), model);

            // then
            assertThat(view).isEqualTo("orders/list");
            assertThat(((pl.commercelink.web.orders.OrdersPageModel) model.get("page")).query().hasFilter()).isFalse();
            verify(orderListService, never()).defaultFilterHref(any());
        }

        @Test
        void settingAndClearingTheDefaultReturnToTheManagementPage() {
            // given
            String manage = OrdersController.filtersPage("/dashboard/orders?status=New");

            // when
            String set = ordersController.setDefaultOrderFilter("f1", manage, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());
            String cleared = ordersController.clearDefaultOrderFilter("f1", manage, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            assertThat(set).isEqualTo("redirect:" + manage);
            assertThat(cleared).isEqualTo("redirect:" + manage);
            verify(orderFilters).setDefault(ACTOR, "f1");
            verify(orderFilters).clearDefault(ACTOR, "f1");
        }

        @Test
        void rejectedDefaultBecomesAFlashForTheManagementPage() {
            // given
            String manage = OrdersController.filtersPage("/dashboard/orders");
            org.mockito.Mockito.doThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.not.found"))
                    .when(orderFilters).setDefault(any(), any());
            when(messageSource.getMessage(eq("orders.filters.error.not.found"), any(), any(Locale.class))).thenReturn("Nie ma takiego filtra.");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.setDefaultOrderFilter("gone", manage, redirect, new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            assertThat(view).isEqualTo("redirect:" + manage);
            assertThat(redirect.getFlashAttributes().get("filterError")).isEqualTo("Nie ma takiego filtra.");
        }

        @Test
        void creatingAFilterWithOpenByDefaultMakesItTheDefault() {
            // given
            var created = pl.commercelink.orders.filters.model.OrderFilter.of("Do wysłania", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "Assembled")));
            when(orderFilters.create(any(), anyBoolean(), any(), any())).thenReturn(created);
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Do wysłania");
            form.setStatus(List.of("Assembled"));
            form.setOpenByDefault(true);
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders"));

            // when
            ordersController.createOrderFilter(form, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            verify(orderFilters).setDefault(ACTOR, created.getId());
        }

        @Test
        void updatingAFilterSetsOrClearsItsDefaultFromTheCheckbox() {
            // given
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Do wysłania");
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders"));

            // when
            ordersController.updateOrderFilter("f1", form, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());
            form.setOpenByDefault(true);
            ordersController.updateOrderFilter("f1", form, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            verify(orderFilters).clearDefault(ACTOR, "f1");
            verify(orderFilters).setDefault(ACTOR, "f1");
        }

        @Test
        void editingTheChosenFilterReturnsToTheListWithItsNewStatuses() {
            // given
            var updated = pl.commercelink.orders.filters.model.OrderFilter.of("Czekające", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "Blocked"),
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.Status, "Assembly")));
            updated.setId("f1");
            when(orderFilters.update(any(), any(), anyBoolean(), any(), any())).thenReturn(updated);
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Czekające");
            form.setStatus(List.of("Blocked", "Assembly"));
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders?status=New&filterId=f1&q=kowalski"));

            // when
            String view = ordersController.updateOrderFilter("f1", form, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            assertThat(view).isEqualTo("redirect:" + OrdersController.filtersPage(
                    "/dashboard/orders?status=Blocked&status=Assembly&filterId=f1&q=kowalski"));
        }

        @Test
        void editingTheChosenFilterWithoutStatusesReturnsToAllOpenOrders() {
            // given
            var updated = pl.commercelink.orders.filters.model.OrderFilter.of("Allegro", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.SourceName, "Allegro")));
            updated.setId("f1");
            when(orderFilters.update(any(), any(), anyBoolean(), any(), any())).thenReturn(updated);
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Allegro");
            form.setSourceName(List.of("Allegro"));
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders?status=New&filterId=f1"));

            // when
            String view = ordersController.updateOrderFilter("f1", form, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            assertThat(view).isEqualTo("redirect:" + OrdersController.filtersPage("/dashboard/orders?filterId=f1"));
        }

        @Test
        void editingAnotherFilterLeavesTheListAddressAlone() {
            // given
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Inny");
            form.setStatus(List.of("Blocked"));
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders?status=New&filterId=f2"));

            // when
            String view = ordersController.updateOrderFilter("f1", form, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            assertThat(view).isEqualTo("redirect:" + OrdersController.filtersPage("/dashboard/orders?status=New&filterId=f2"));
        }

        @Test
        void legacyParametersRedirect() {
            assertThat(ordersController.orders(params("statuses", "Blocked", "showAll", "false"), Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isEqualTo("redirect:/dashboard/orders?status=Blocked");
            assertThat(ordersController.orders(params("showAll", "true"), Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                    .isEqualTo("redirect:/dashboard/orders?filterId=");
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
                    .isEqualTo("redirect:/dashboard/orders?filterId=");
            assertThat(ordersController.deleteOrderFilter("f1", "/dashboard/store/x", redirect, new ExtendedModelMap(), Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse()))
                    .isEqualTo("redirect:/dashboard/orders?filterId=");
        }

        @Test
        void creatingAFilterReturnsToTheManagementPage() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Nowy");
            form.setStatus(List.of("New"));
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
            assertThat(form.getStatus()).containsExactly("New");
            assertThat(form.getSourceName()).containsExactly("Allegro");
            assertThat(form.isSharedWithStore()).isFalse();
            assertThat(form.isOpenByDefault()).isFalse();
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
        void editSubpageTicksEverySavedValueOfAField() {
            // given
            var own = pl.commercelink.orders.filters.model.OrderFilter.of("Allegro lub Ceneo", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.SourceName, "Allegro"),
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.SourceName, "Ceneo"),
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.ShippingDue, "Overdue")));
            when(orderFilters.list(ACTOR)).thenReturn(new pl.commercelink.orders.filters.services.ListOrderFiltersView(List.of(), List.of(own)));
            when(messageSource.getMessage(eq("orders.filters.edit.title"), any(), any(Locale.class))).thenReturn("Edytuj filtr");
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            ordersController.editOrderFilterPage(own.getId(), "/dashboard/orders", Locale.forLanguageTag("pl"), model);

            // then
            var form = (pl.commercelink.web.dtos.OrderFilterForm) model.get("filterForm");
            assertThat(form.getSourceName()).containsExactly("Allegro", "Ceneo");
            assertThat(form.getStatus()).isEmpty();
            assertThat(form.getShippingDue()).isEqualTo("Overdue");
        }

        @Test
        void creatingAFilterSavesOneConditionPerTickedValue() {
            // given
            var created = pl.commercelink.orders.filters.model.OrderFilter.of("Allegro lub Ceneo", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.SourceName, "Allegro")));
            when(orderFilters.create(any(), anyBoolean(), any(), any())).thenReturn(created);
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Allegro lub Ceneo");
            form.setSourceName(List.of("Allegro", "Ceneo"));
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders"));

            // when
            ordersController.createOrderFilter(form, new RedirectAttributesModelMap(), new ExtendedModelMap(),
                    Locale.forLanguageTag("pl"), new org.springframework.mock.web.MockHttpServletResponse());

            // then
            verify(orderFilters).create(ACTOR, false, "Allegro lub Ceneo", List.of(
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.SourceName, "Allegro"),
                    pl.commercelink.orders.filters.model.OrderFilterCondition.of(pl.commercelink.orders.filters.OrderFilterField.SourceName, "Ceneo")));
        }

        @Test
        void uncheckingEveryValueOfTheOnlyFieldIsRejectedWithTheFormKept() {
            // given
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Pusty");
            form.setReturnTo(OrdersController.filtersPage("/dashboard/orders"));
            when(orderFilters.update(any(), any(), anyBoolean(), any(), any()))
                    .thenThrow(new pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException("orders.filters.error.no.conditions"));
            when(messageSource.getMessage(eq("orders.filters.error.no.conditions"), any(), any(Locale.class))).thenReturn("Wybierz co najmniej jeden warunek.");
            ExtendedModelMap model = new ExtendedModelMap();
            var response = new org.springframework.mock.web.MockHttpServletResponse();

            // when
            String view = ordersController.updateOrderFilter("f1", form, new RedirectAttributesModelMap(), model,
                    Locale.forLanguageTag("pl"), response);

            // then
            assertThat(view).isEqualTo("orders/filter-edit");
            assertThat(response.getStatus()).isEqualTo(422);
            verify(orderFilters).update(ACTOR, "f1", false, "Pusty", List.of());
        }

        @Test
        void rejectedUpdateKeepsTheSubmittedFieldsAndTheEditedFilterId() {
            var form = new pl.commercelink.web.dtos.OrderFilterForm();
            form.setLabel("Do wysłania jutro");
            form.setStatus(List.of("New"));
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
            assertThat(filterForm.getStatus()).containsExactly("New");
            assertThat(model.get("filterId")).isEqualTo("f1");
        }

        @Test
        void malformedReturnToFallsBackToTheBareList() {
            String view = ordersController.deleteOrderFilter("f1", "/dashboard/orders?x=%",
                    new RedirectAttributesModelMap(), new ExtendedModelMap(), Locale.forLanguageTag("pl"),
                    new org.springframework.mock.web.MockHttpServletResponse());
            assertThat(view).isEqualTo("redirect:/dashboard/orders?filterId=");
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
            String view = ordersController.statusPage(ORDER_ID, model, new RedirectAttributesModelMap(), polish);

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
            String view = ordersController.settingsPage(ORDER_ID, model, new RedirectAttributesModelMap(), Locale.ROOT);

            // then
            assertThat(view).isEqualTo("orders/settings");
            assertThat(model).containsKeys("settings", "orderId", "shortId");
        }

        @Test
        void statusPageOfAClosedOrderGoesBackWithTheReason() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Cancelled));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.statusPage(ORDER_ID, new ExtendedModelMap(), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.status.error.closed");
        }

        @Test
        void settingsPageOfAClosedOrderGoesBackWithTheReason() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Completed));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.settingsPage(ORDER_ID, new ExtendedModelMap(), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.settings.error.closed");
            verifyNoInteractions(pageModelFactory);
        }

        @Test
        void updateOrderInfoRefusesAClosedOrderAndSavesNothing() {
            // given
            Order order = order(OrderStatus.Completed);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
            when(pageModelFactory.settings(eq(order), anyList(), eq(false))).thenReturn(mock(OrderSettingsView.class));
            Order posted = new Order(STORE_ID);
            posted.setComment("changed");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String syncView = ordersController.updateOrderInfo(ORDER_ID, posted, null, new MockHttpServletResponse(),
                    new ExtendedModelMap(), redirect, polish);
            String asyncView = ordersController.updateOrderInfo(ORDER_ID, posted, "fetch", response, model,
                    new RedirectAttributesModelMap(), polish);

            // then
            assertThat(syncView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.settings.error.closed");
            assertThat(asyncView).isEqualTo("orders/details/settings :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(model.getAttribute("settingsError")).isEqualTo("order.settings.error.closed");
            assertThat(order.getComment()).isNull();
            verifyNoInteractions(orderLifecycle);
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
                    ordersController.confirmCancelOrder(ORDER_ID, cancel, new RedirectAttributesModelMap(), polish),
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
        void theInvoiceConfirmationPageCarriesTheTypeAndPostsToTheUnchangedEndpoint() {
            // given: without JavaScript the "Wystaw" entry leads here instead of the issue dialog
            Order order = order(OrderStatus.Realization);
            BillingDetails billing = new BillingDetails();
            billing.setTaxId("5250000000");
            order.setBillingDetails(billing);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.confirmInvoice(ORDER_ID, DocumentType.InvoiceVat, model, polish,
                    new RedirectAttributesModelMap());

            // then
            assertThat(view).isEqualTo("orders/invoicing-confirm");
            assertThat(model.getAttribute("orderId")).isEqualTo(ORDER_ID);
            assertThat(model.getAttribute("documentType")).isEqualTo("InvoiceVat");
            assertThat(model.getAttribute("documentLabelKey")).isEqualTo("DocumentType.InvoiceVat");
            verifyNoInteractions(invoiceCreationEventPublisher);
        }

        @Test
        void theInvoiceConfirmationOfATypeThatCannotBeIssuedGoesBackToTheOrder() {
            // given: a consumer order has no invoice to issue from the menu
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Realization));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.confirmInvoice(ORDER_ID, DocumentType.InvoiceVat, new ExtendedModelMap(), polish, redirect);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "error.message.no.eligible.invoice.to.create");
            verifyNoInteractions(invoiceCreationEventPublisher);
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
            assertThatThrownBy(() -> ordersController.updateSerialNumbers(ORDER_ID, new OrderItemsForm(), new RedirectAttributesModelMap(), polish))
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
        void addReceiptRefusesASecondClosingDocumentOnAnInvoicedOrder() {
            // given
            Order order = order(OrderStatus.Delivered);
            BillingDetails company = new BillingDetails();
            company.setTaxId("5250000000");
            order.setBillingDetails(company);
            order.addDocument(document(DocumentType.InvoiceVat, "FV/1"));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Document forged = new Document();
            forged.setType(DocumentType.Receipt);
            forged.setNumber("FORGED/1");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.addReceipt(ORDER_ID, forged, redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.documents.add.locked");
            assertThat(order.getDocuments()).extracting(Document::getNumber).containsExactly("FV/1");
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void addReceiptRefusesOnACompletedOrder() {
            // given
            Order order = order(OrderStatus.Completed);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Document receipt = new Document();
            receipt.setType(DocumentType.Receipt);
            receipt.setNumber("PAR/1");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.addReceipt(ORDER_ID, receipt, redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.documents.add.locked.closed");
            assertThat(order.getDocuments()).isEmpty();
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void addReceiptRefusesATypeOutsideTheManualOnes() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Document invoice = new Document();
            invoice.setType(DocumentType.InvoiceVat);
            invoice.setNumber("FV/1");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.addReceipt(ORDER_ID, invoice, redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.documents.add.locked");
            assertThat(order.getDocuments()).isEmpty();
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void addReceiptAddsTheNextManualDocument() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Document receipt = new Document();
            receipt.setType(DocumentType.Receipt);
            receipt.setNumber("PAR/1");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.addReceipt(ORDER_ID, receipt, redirect, polish);

            // then
            assertThat(order.getDocuments()).extracting(Document::getType).containsExactly(DocumentType.Receipt);
            verify(orderLifecycle).update(order);
        }

        @Test
        void addReceiptRefusesALinkThatIsNotAWebAddress() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Document receipt = new Document();
            receipt.setType(DocumentType.Receipt);
            receipt.setNumber("PAR/1");
            receipt.setLink(" javascript:alert(1) ");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.addReceipt(ORDER_ID, receipt, redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.documents.add.error.link");
            assertThat(order.getDocuments()).isEmpty();
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void addReceiptKeepsATrimmedWebLink() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Document receipt = new Document();
            receipt.setType(DocumentType.Receipt);
            receipt.setNumber("PAR/1");
            receipt.setLink(" https://receipts.example/1 ");

            // when
            ordersController.addReceipt(ORDER_ID, receipt, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(order.getDocuments()).extracting(Document::getLink).containsExactly("https://receipts.example/1");
            verify(orderLifecycle).update(order);
        }

        @Test
        void deletingAnOrderThatCanNoLongerBeDeletedGoesBackWithTheReason() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));
            org.mockito.Mockito.doThrow(new IllegalStateException("not deletable")).when(ordersManager).deleteOrder(STORE_ID, ORDER_ID);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.deleteOrder(ORDER_ID, redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.page.delete.unavailable");
        }

        @Test
        void deletingADeletableOrderGoesToTheList() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));

            // when
            String view = ordersController.deleteOrder(ORDER_ID, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders");
            verify(ordersManager).deleteOrder(STORE_ID, ORDER_ID);
        }

        @Test
        void reviewShipmentsAndSerialNumbersOfAClosedOrderAreRefusedAndNothingIsSaved() {
            // given
            Order closed = order(OrderStatus.Completed);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(closed);
            pl.commercelink.orders.OrderReview reviewBefore = closed.getReview();
            Order review = new Order(STORE_ID);
            review.setReview(new pl.commercelink.orders.OrderReview(OrderReviewStatus.ToBeCollected));
            RedirectAttributesModelMap reviewRedirect = new RedirectAttributesModelMap();
            RedirectAttributesModelMap shipmentsRedirect = new RedirectAttributesModelMap();
            RedirectAttributesModelMap serialRedirect = new RedirectAttributesModelMap();

            // when
            ordersController.updateReview(ORDER_ID, review, reviewRedirect, polish);
            ordersController.saveShipment(ORDER_ID, null, null, ShipmentType.Courier, null, "T-1", null, null, null,
                    null, null, new MockHttpServletRequest(), new MockHttpServletResponse(),
                    new ExtendedModelMap(), shipmentsRedirect, polish);
            ordersController.updateSerialNumbers(ORDER_ID, new OrderItemsForm(), serialRedirect, polish);

            // then
            assertThat(flash(reviewRedirect)).containsEntry("errorMessage", "order.review.error.closed");
            assertThat(flash(shipmentsRedirect)).containsEntry("errorMessage", "order.shipments.error.closed");
            assertThat(flash(serialRedirect)).containsEntry("errorMessage", "order.item.error.closed");
            assertThat(closed.getReview()).isSameAs(reviewBefore);
            verifyNoInteractions(orderLifecycle, orderLifecycleEventPublisher, shipmentTrackingSubscriber);
            verify(orderItemsRepository, never()).findByOrderId(any());
            verify(orderItemsRepository, never()).save(any());
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
            ordersController.updateSerialNumbers(ORDER_ID, form, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(posted.getSerialNo()).isEqualTo("NEW");
            assertThat(other.getSerialNo()).isEqualTo("KEEP");
            verify(orderItemsRepository).save(posted);
            verify(orderItemsRepository, never()).save(other);
        }

        @Test
        void bulkActionReportsSkippedItems() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
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
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
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
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
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
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.moveSelectedItemsToTheWarehouseForRMA(ORDER_ID, new OrderItemsForm(List.of()), redirect, polish);

            // then
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.bulk.none.selected");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void aBulkActionWithoutJavaScriptIsConfirmedOnAPage() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
            OrderItem chosen = item("a", FulfilmentStatus.New, "SKU-A");
            OrderItem other = item("b", FulfilmentStatus.New, "SKU-B");
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(chosen, other));
            when(messageSource.getMessage(eq("order.bulk.remove.confirm.message"), any(), any(Locale.class)))
                    .thenReturn("Usuniesz zaznaczone pozycje ({n}).");
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.confirmBulk(ORDER_ID, BulkAction.REMOVE, selected("a"), model,
                    new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("orders/bulk-confirm");
            assertThat(model.getAttribute("title")).isEqualTo("order.bulk.remove.confirm.title");
            assertThat(model.getAttribute("message")).isEqualTo("Usuniesz zaznaczone pozycje (1).");
            assertThat(model.getAttribute("confirmLabel")).isEqualTo("order.bulk.remove.confirm.action");
            assertThat(model.getAttribute("danger")).isEqualTo(true);
            assertThat(model.getAttribute("actionPath")).isEqualTo("/dashboard/orders/" + ORDER_ID + "/removeSelectedItemsFromOrder");
            assertThat(model.getAttribute("items")).isEqualTo(List.of(chosen));
            verifyNoInteractions(ordersManager);
            verify(orderItemsRepository, never()).save(any());
        }

        @Test
        void aBulkConfirmationWithNothingSelectedGoesBack() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.confirmBulk(ORDER_ID, BulkAction.ALLOCATE, new OrderItemsForm(List.of()),
                    new ExtendedModelMap(), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.bulk.none.selected");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void itemsMoveToAnOrderFoundByItsShortNumber() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
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
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
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
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
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
        void moveTargetPreviewsAnOrderWithoutAStatusWithoutFailing() {
            // given
            Order bare = new Order();
            bare.setOrderId("bbbbbbbb-0000-0000-0000-000000000000");
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
            when(orderReferenceResolver.resolve(STORE_ID, "bbbbbbbb")).thenReturn(OrderReferenceResolver.Resolution.found(bare));

            // when
            ResponseEntity<MoveTargetView> preview = ordersController.moveTarget(ORDER_ID, "bbbbbbbb", polish);

            // then
            assertThat(preview.getStatusCode().value()).isEqualTo(200);
            assertThat(preview.getBody().statusLabel()).isNull();
            verify(messageSource, never()).getMessage(isNull(), any(), any(Locale.class));
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
        void theShippingAddressStillChangesInShippingWithoutALabel() {
            // given
            Order order = order(OrderStatus.Shipping);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            Order posted = shippingPayload();

            // when
            ordersController.updateAddressDetails(ORDER_ID, "shipping", posted, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirect, polish);

            // then: only a label fixes the parcel address, the status does not
            verify(ordersRepository).save(order);
            assertThat(order.getShippingDetails()).isSameAs(posted.getShippingDetails());
            assertThat(flash(redirect)).doesNotContainKey("errorMessage");
        }

        @Test
        void aLabelLocksTheShippingAddressEvenBeforeShippingAndTheRefusalNamesTheLabel() {
            // given
            Order order = order(OrderStatus.Realization);
            Shipment labelled = new Shipment(ShipmentType.Courier);
            labelled.setTrackingNo("T-1");
            order.setShipments(new ArrayList<>(List.of(labelled)));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.updateAddressDetails(ORDER_ID, "shipping", shippingPayload(), null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), redirect, polish);

            // then
            verify(ordersRepository, never()).save(any());
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.customer.shipping.locked.label");
        }

        @Test
        void theShippingAddressPageOfALabelledOrderGoesBackWithTheReason() {
            // given
            Order order = order(OrderStatus.Realization);
            Shipment labelled = new Shipment(ShipmentType.Courier);
            labelled.setCarrier("DPD");
            labelled.setTrackingNo("T-1");
            labelled.setShippedAt(LocalDateTime.now());
            order.setShipments(new ArrayList<>(List.of(labelled)));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.showAddressDetails(ORDER_ID, "shipping", new ExtendedModelMap(), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.customer.shipping.locked.label");
        }

        @Test
        void theBillingAddressPageOfAnInvoicedOrderGoesBack() {
            // given
            Order order = order(OrderStatus.Delivered);
            order.addDocument(document(DocumentType.Receipt, "PAR/1"));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.showAddressDetails(ORDER_ID, "billing", new ExtendedModelMap(), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.customer.billing.locked");
        }

        @Test
        void addressesOfAClosedOrderCannotBeOpenedOrSaved() {
            // given
            Order order = order(OrderStatus.Completed);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order billingPayload = new Order(STORE_ID);
            billingPayload.setBillingDetails(new BillingDetails());
            RedirectAttributesModelMap openBilling = new RedirectAttributesModelMap();
            RedirectAttributesModelMap openShipping = new RedirectAttributesModelMap();
            RedirectAttributesModelMap saveBilling = new RedirectAttributesModelMap();
            RedirectAttributesModelMap saveShipping = new RedirectAttributesModelMap();

            // when
            String billingView = ordersController.showAddressDetails(ORDER_ID, "billing", new ExtendedModelMap(), openBilling, polish);
            String shippingView = ordersController.showAddressDetails(ORDER_ID, "shipping", new ExtendedModelMap(), openShipping, polish);
            ordersController.updateAddressDetails(ORDER_ID, "billing", billingPayload, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), saveBilling, polish);
            ordersController.updateAddressDetails(ORDER_ID, "shipping", shippingPayload(), null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), saveShipping, polish);

            // then
            assertThat(billingView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(shippingView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(openBilling)).containsEntry("errorMessage", "order.address.error.closed");
            assertThat(flash(openShipping)).containsEntry("errorMessage", "order.address.error.closed");
            assertThat(flash(saveBilling)).containsEntry("errorMessage", "order.address.error.closed");
            assertThat(flash(saveShipping)).containsEntry("errorMessage", "order.address.error.closed");
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void theShippingAddressPageOfAnOpenOrderRendersTheForm() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.showAddressDetails(ORDER_ID, "shipping", model, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("orders/address");
            OrderAddressForm form = (OrderAddressForm) model.getAttribute("address");
            assertThat(form.type()).isEqualTo("shipping");
            assertThat(form.orderId()).isEqualTo(ORDER_ID);
            assertThat(form.errors()).isEmpty();
        }

        @Test
        void anInvalidAddressFromTheDialogComesBackWithItsFieldErrorsAndSavesNothing() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order posted = new Order(STORE_ID);
            BillingDetails billing = validBilling();
            billing.setName(" ");
            billing.setCountry("Polska");
            billing.setPhone("12");
            posted.setBillingDetails(billing);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.updateAddressDetails(ORDER_ID, "billing", posted, "fetch",
                    new MockHttpServletRequest(), response, model, new RedirectAttributesModelMap(), polish);

            // then: the dialog's form is re-rendered with the posted values and an error per changed field (the blank
            // name equals the saved one, so it passes as it is)
            assertThat(view).isEqualTo("orders/details/address :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(422);
            OrderAddressForm form = (OrderAddressForm) model.getAttribute("address");
            assertThat(form.errors()).containsExactly(
                    Map.entry("billingDetails.country", "order.address.country.invalid"),
                    Map.entry("billingDetails.phone", "billing.phone.invalid"));
            assertThat(form.country()).isEqualTo("Polska");
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void anInvalidAddressFromThePageWithoutJavascriptRendersThePageWithTheErrors() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order posted = new Order(STORE_ID);
            ShippingDetails shipping = validShipping();
            shipping.setEmail("not-an-email");
            posted.setShippingDetails(shipping);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.updateAddressDetails(ORDER_ID, "shipping", posted, null,
                    new MockHttpServletRequest(), new MockHttpServletResponse(), model, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("orders/address");
            assertThat(((OrderAddressForm) model.getAttribute("address")).errors())
                    .containsExactly(Map.entry("shippingDetails.email", "billing.email.invalid"));
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void aValidAddressFromTheDialogIsSavedAndLeavesTheNoticeForTheReloadedPage() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order posted = new Order(STORE_ID);
            posted.setShippingDetails(validShipping());
            MockHttpServletRequest request = new MockHttpServletRequest();
            FlashMap flashMap = new FlashMap();
            request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);
            request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.updateAddressDetails(ORDER_ID, "shipping", posted, "fetch", request, response,
                    model, new RedirectAttributesModelMap(), polish);

            // then: 200 with the form lets the dialog close and reload; the notice waits for the order page
            assertThat(view).isEqualTo("orders/details/address :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(((OrderAddressForm) model.getAttribute("address")).errors()).isEmpty();
            assertThat(order.getShippingDetails()).isSameAs(posted.getShippingDetails());
            verify(ordersRepository).save(order);
            assertThat(flashMap.getTargetRequestPath()).isEqualTo("/dashboard/orders/" + ORDER_ID);
            assertThat(((OrderNotice) flashMap.get(OrderFlash.ATTRIBUTE)).text()).isEqualTo("order.address.shipping.saved");
        }

        @Test
        void aValidAddressFromThePageIsSavedAndReturnsToTheOrderWithTheNotice() {
            // given
            Order order = order(OrderStatus.New);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order posted = new Order(STORE_ID);
            posted.setBillingDetails(validBilling());
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.updateAddressDetails(ORDER_ID, "billing", posted, null,
                    new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(), redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(order.getBillingDetails()).isSameAs(posted.getBillingDetails());
            assertThat(((OrderNotice) flash(redirect).get(OrderFlash.ATTRIBUTE)).text()).isEqualTo("order.address.billing.saved");
        }

        @Test
        void theDialogOfAnInvoicedOrderIsRefusedInsideTheDialogEvenWithAValidAddress() {
            // given
            Order order = order(OrderStatus.Delivered);
            order.addDocument(document(DocumentType.Receipt, "PAR/1"));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order posted = new Order(STORE_ID);
            posted.setBillingDetails(validBilling());
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.updateAddressDetails(ORDER_ID, "billing", posted, "fetch",
                    new MockHttpServletRequest(), response, model, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("orders/details/address :: dialogForm");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(((OrderAddressForm) model.getAttribute("address")).refusal()).isEqualTo("order.customer.billing.locked");
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void theDialogOfALabelledOrOfAClosedOrderIsRefusedInsideTheDialog() {
            // given
            Order labelled = order(OrderStatus.Realization);
            Shipment shipment = new Shipment(ShipmentType.Courier);
            shipment.setTrackingNo("T-1");
            labelled.setShipments(new ArrayList<>(List.of(shipment)));
            Order closed = order(OrderStatus.Completed);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(labelled, closed);
            ExtendedModelMap labelledModel = new ExtendedModelMap();
            ExtendedModelMap closedModel = new ExtendedModelMap();

            // when
            ordersController.updateAddressDetails(ORDER_ID, "shipping", shippingPayload(), "fetch",
                    new MockHttpServletRequest(), new MockHttpServletResponse(), labelledModel, new RedirectAttributesModelMap(), polish);
            ordersController.updateAddressDetails(ORDER_ID, "shipping", shippingPayload(), "fetch",
                    new MockHttpServletRequest(), new MockHttpServletResponse(), closedModel, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(((OrderAddressForm) labelledModel.getAttribute("address")).refusal()).isEqualTo("order.customer.shipping.locked.label");
            assertThat(((OrderAddressForm) closedModel.getAttribute("address")).refusal()).isEqualTo("order.address.error.closed");
            verify(ordersRepository, never()).save(any());
        }

        private BillingDetails saveBilling(Order order, BillingDetails posted, RedirectAttributesModelMap redirect,
                                           ExtendedModelMap model) {
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            Order payload = new Order(STORE_ID);
            payload.setBillingDetails(posted);
            ordersController.updateAddressDetails(ORDER_ID, "billing", payload, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), model, redirect, polish);
            return order.getBillingDetails();
        }

        private BillingDetails copyOf(BillingDetails source) {
            BillingDetails copy = new BillingDetails();
            copy.setName(source.getName());
            copy.setSurname(source.getSurname());
            copy.setCompanyName(source.getCompanyName());
            copy.setTaxId(source.getTaxId());
            copy.setStreetAndNumber(source.getStreetAndNumber());
            copy.setPostalCode(source.getPostalCode());
            copy.setCity(source.getCity());
            copy.setCountry(source.getCountry());
            copy.setEmail(source.getEmail());
            copy.setPhone(source.getPhone());
            return copy;
        }

        @Test
        void aCompanyBillingWithoutAPersonSavesWhenOnlyTheCityChanges() {
            // given: a marketplace company billing, imported without a person's name
            Order order = order(OrderStatus.New);
            BillingDetails company = validBilling();
            company.setName(null);
            company.setSurname(null);
            company.setCompanyName("TechNova Sp. z o.o.");
            order.setBillingDetails(company);
            BillingDetails posted = copyOf(company);
            posted.setCity("Kraków");
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            BillingDetails saved = saveBilling(order, posted, new RedirectAttributesModelMap(), model);

            // then
            verify(ordersRepository).save(order);
            assertThat(saved.getCity()).isEqualTo("Kraków");
            assertThat(model.getAttribute("address")).isNull();
        }

        @Test
        void aStoredCountryNameAndAMissingPhoneStayWhenOnlyTheStreetChanges() {
            // given
            Order order = order(OrderStatus.New);
            BillingDetails imported = validBilling();
            imported.setCountry("Polska");
            imported.setPhone(null);
            order.setBillingDetails(imported);
            BillingDetails posted = copyOf(imported);
            posted.setPhone("");
            posted.setStreetAndNumber("ul. Nowa 3");

            // when
            BillingDetails saved = saveBilling(order, posted, new RedirectAttributesModelMap(), new ExtendedModelMap());

            // then
            verify(ordersRepository).save(order);
            assertThat(saved.getStreetAndNumber()).isEqualTo("ul. Nowa 3");
            assertThat(saved.getCountry()).isEqualTo("Polska");
        }

        @Test
        void aNewlyTypedCountryNameIsStillRefused() {
            // given
            Order order = order(OrderStatus.New);
            order.setBillingDetails(validBilling());
            BillingDetails posted = validBilling();
            posted.setCountry("Polska");
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            saveBilling(order, posted, new RedirectAttributesModelMap(), model);

            // then
            assertThat(((OrderAddressForm) model.getAttribute("address")).errors())
                    .containsExactly(Map.entry("billingDetails.country", "order.address.country.invalid"));
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void aCountryCodeTypedInLowerCaseIsStoredInCapitals() {
            // given
            Order order = order(OrderStatus.New);
            BillingDetails german = validBilling();
            german.setCountry("DE");
            order.setBillingDetails(german);
            BillingDetails posted = copyOf(german);
            posted.setCountry(" pl ");

            // when
            BillingDetails saved = saveBilling(order, posted, new RedirectAttributesModelMap(), new ExtendedModelMap());

            // then
            verify(ordersRepository).save(order);
            assertThat(saved.getCountry()).isEqualTo("PL");
        }

        @Test
        void aSingleWordNameWithAnEmptySurnameSaves() {
            // given
            Order order = order(OrderStatus.New);
            order.setBillingDetails(validBilling());
            BillingDetails posted = validBilling();
            posted.setName("Madonna");
            posted.setSurname("");

            // when
            BillingDetails saved = saveBilling(order, posted, new RedirectAttributesModelMap(), new ExtendedModelMap());

            // then
            verify(ordersRepository).save(order);
            assertThat(saved.getName()).isEqualTo("Madonna");
        }

        @Test
        void aBillingWithNeitherNameNorCompanyIsRefusedAtTheName() {
            // given
            Order order = order(OrderStatus.New);
            order.setBillingDetails(validBilling());
            BillingDetails posted = validBilling();
            posted.setName(" ");
            posted.setCompanyName("");
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            saveBilling(order, posted, new RedirectAttributesModelMap(), model);

            // then
            assertThat(((OrderAddressForm) model.getAttribute("address")).errors())
                    .containsExactly(Map.entry("billingDetails.name", "order.address.name.or.company.required"));
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void anUnknownAddressTypeOfTheAddressPageIsNotFoundEvenOnALockedOrder() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Completed));

            // when / then
            assertThatThrownBy(() -> ordersController.showAddressDetails(ORDER_ID, "other", new ExtendedModelMap(),
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void anUnknownAddressTypeIsNotFound() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));

            // when / then
            assertThatThrownBy(() -> ordersController.updateAddressDetails(ORDER_ID, "other", new Order(STORE_ID), null,
                    new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(),
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            verify(ordersRepository, never()).save(any());
        }

        private Order shippingPayload() {
            ShippingDetails posted = validShipping();
            posted.setCity("Wroclaw");
            Order payload = new Order(STORE_ID);
            payload.setShippingDetails(posted);
            return payload;
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

        // --- e-receipt: the locks while it is being issued, refused on the server (never only greyed in the UI) ---

        @Test
        void whileAnEReceiptIsBeingIssuedItemEditsAreRefusedWithItsOwnReason() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(receiptAttemptService.locksOrder(order)).thenReturn(true);
            RedirectAttributesModelMap add = new RedirectAttributesModelMap();
            RedirectAttributesModelMap remove = new RedirectAttributesModelMap();
            RedirectAttributesModelMap split = new RedirectAttributesModelMap();
            RedirectAttributesModelMap move = new RedirectAttributesModelMap();
            RedirectAttributesModelMap consolidate = new RedirectAttributesModelMap();
            RedirectAttributesModelMap delete = new RedirectAttributesModelMap();
            RedirectAttributesModelMap document = new RedirectAttributesModelMap();

            // when
            ordersController.addOrderItems(ORDER_ID, new AddItemsForm(), add, polish);
            ordersController.removeSelectedItemsFromOrder(ORDER_ID, selected("a"), remove, polish);
            ordersController.splitOrder(ORDER_ID, selected("a"), split, polish);
            ordersController.moveItemsToOrder(ORDER_ID, selected("a"), "51aa", move, polish);
            ordersController.toggleConsolidation(ORDER_ID, "i1", consolidate, polish);
            ordersController.deleteOrder(ORDER_ID, delete, polish);
            ordersController.addReceipt(ORDER_ID, document(DocumentType.InvoicePersonal, "FV/1"), document, polish);

            // then
            assertThat(flash(add)).containsEntry("errorMessage", "order.items.add.locked.receipt");
            assertThat(flash(remove)).containsEntry("errorMessage", "order.bulk.unavailable.receipt");
            assertThat(flash(split)).containsEntry("errorMessage", "order.bulk.unavailable.receipt");
            assertThat(flash(move)).containsEntry("errorMessage", "order.bulk.unavailable.receipt");
            assertThat(flash(consolidate)).containsEntry("errorMessage", "order.item.consolidation.locked.receipt");
            assertThat(flash(delete)).containsEntry("errorMessage", "order.page.delete.locked.receipt");
            assertThat(flash(document)).containsEntry("errorMessage", "order.documents.add.locked.receipt");
            assertThat(order.getDocuments()).isEmpty();
            verifyNoInteractions(ordersManager, orderReferenceResolver, orderLifecycle);
            verify(orderItemsRepository, never()).save(any());
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void itemsOfAnInvoicedOrderAreNotRemovedEvenFromAStalePage() {
            // given: the page no longer offers "Usuń" once the receipt document is on the order
            Order order = order(OrderStatus.Delivered);
            order.addDocument(new Document("o:R1", "PAR/1", null, DocumentType.Receipt));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap remove = new RedirectAttributesModelMap();

            // when
            ordersController.removeSelectedItemsFromOrder(ORDER_ID, selected("a"), remove, polish);

            // then
            assertThat(flash(remove)).containsEntry("errorMessage", "order.items.remove.locked.invoiced");
            verifyNoInteractions(ordersManager, orderLifecycle);
        }

        @Test
        void anInvoicedOrderKeepsItsInvoicedReasonsWhateverTheReceipt() {
            // given: the receipt document is on the order, so the order is invoiced and the invoiced reason wins
            Order order = order(OrderStatus.Delivered);
            order.addDocument(new Document("o:R1", "PAR/1", null, DocumentType.Receipt));
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap add = new RedirectAttributesModelMap();
            RedirectAttributesModelMap consolidate = new RedirectAttributesModelMap();

            // when
            ordersController.addOrderItems(ORDER_ID, new AddItemsForm(), add, polish);
            ordersController.toggleConsolidation(ORDER_ID, "i1", consolidate, polish);

            // then
            assertThat(flash(add)).containsEntry("errorMessage", "order.items.add.locked.invoiced");
            assertThat(flash(consolidate)).containsEntry("errorMessage", "order.item.consolidation.locked");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void itemsCannotMoveToAnOrderWhoseEReceiptIsBeingIssued() {
            // given
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
            Order target = new Order(STORE_ID);
            when(orderReferenceResolver.resolve(STORE_ID, "51aa")).thenReturn(OrderReferenceResolver.Resolution.found(target));
            when(receiptAttemptService.locksOrder(target)).thenReturn(true);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = ordersController.moveItemsToOrder(ORDER_ID, selected("a"), "51aa", redirect, polish);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(redirect)).containsEntry("errorMessage", "order.move.target.locked.receipt");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void theBillingAddressIsFixedWhileAnEReceiptIsBeingIssuedButTheShippingOneIsNot() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(receiptAttemptService.locksOrder(order)).thenReturn(true);
            Order posted = new Order(STORE_ID);
            posted.setBillingDetails(new BillingDetails());
            RedirectAttributesModelMap page = new RedirectAttributesModelMap();
            RedirectAttributesModelMap save = new RedirectAttributesModelMap();

            // when
            String pageView = ordersController.showAddressDetails(ORDER_ID, "billing", new ExtendedModelMap(), page, polish);
            ordersController.updateAddressDetails(ORDER_ID, "billing", posted, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), save, polish);
            String shippingView = ordersController.showAddressDetails(ORDER_ID, "shipping", new ExtendedModelMap(),
                    new RedirectAttributesModelMap(), polish);

            // then
            assertThat(pageView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(page)).containsEntry("errorMessage", "order.customer.billing.locked.receipt");
            assertThat(flash(save)).containsEntry("errorMessage", "order.customer.billing.locked.receipt");
            assertThat(shippingView).isEqualTo("orders/address");
            verify(ordersRepository, never()).save(any());
        }

        @Test
        void savingAnItemWhileAnEReceiptIsBeingIssuedKeepsItsPriceAndConsolidation() {
            // given
            Order order = order(OrderStatus.New);
            OrderItem item = item("i1", FulfilmentStatus.New, "MFN-1");
            item.setConsolidated(false);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
            when(receiptAttemptService.locksOrder(order)).thenReturn(true);
            OrderItem posted = new OrderItem();
            posted.setName("Ryzen");
            posted.setQty(1);
            posted.setPrice(1.0);
            posted.setConsolidated(true);

            // when
            ordersController.saveOrderItem(ORDER_ID, "i1", posted, null, new ExtendedModelMap());

            // then
            assertThat(item.getPrice()).isEqualTo(100.0);
            assertThat(item.isConsolidated()).isFalse();
        }

        @Test
        void noInvoiceIsIssuedWhileAnEReceiptAttemptOwnsTheSale() {
            // given: a business order that could otherwise be invoiced
            Order order = order(OrderStatus.Realization);
            BillingDetails billing = new BillingDetails();
            billing.setTaxId("5250000000");
            order.setBillingDetails(billing);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(receiptAttemptService.blocksManualReceipt(STORE_ID, ORDER_ID)).thenReturn(true);
            RedirectAttributesModelMap confirm = new RedirectAttributesModelMap();
            RedirectAttributesModelMap issue = new RedirectAttributesModelMap();

            // when
            String confirmView = ordersController.confirmInvoice(ORDER_ID, DocumentType.InvoiceVat, new ExtendedModelMap(),
                    polish, confirm);
            String issueView = ordersController.createInvoice(ORDER_ID, DocumentType.InvoiceVat, false, polish, issue);

            // then
            assertThat(confirmView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(issueView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(confirm)).containsEntry("errorMessage", "receipts.invoicing.blocked");
            assertThat(flash(issue)).containsEntry("errorMessage", "receipts.invoicing.blocked");
            verifyNoInteractions(invoiceCreationEventPublisher);
        }

        // --- the sale fields (name, quantity, VAT rate) and cancelling, against the sale document ---

        private OrderItem storedNewItem(Order order) {
            OrderItem item = item("i1", FulfilmentStatus.New, "MFN-1");
            item.setTax(1.23);
            item.setComment("old");
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
            when(orderItemsRepository.findById(ORDER_ID, "i1")).thenReturn(item);
            return item;
        }

        /** What the item page posts for it: every field, the read-only ones with their stored values. */
        private OrderItem postedUnchanged(OrderItem item) {
            OrderItem posted = new OrderItem();
            posted.setName(item.getName());
            posted.setQty(item.getQty());
            posted.setTax(item.getTax());
            posted.setCategory(item.getCategory());
            posted.setComment("new comment");
            posted.setSerialNo("SN-1");
            return posted;
        }

        private Order invoiced(OrderStatus status) {
            Order order = order(status);
            order.addDocument(new Document("o:R1", "PAR/1", null, DocumentType.Receipt));
            return order;
        }

        @Test
        void savingAnItemOfAnInvoicedOrderRefusesAChangedNameQuantityOrVatRate() {
            // given
            OrderItem item = storedNewItem(invoiced(OrderStatus.Delivered));
            OrderItem name = postedUnchanged(item);
            name.setName("Ryzen 9");
            OrderItem qty = postedUnchanged(item);
            qty.setQty(5);
            OrderItem tax = postedUnchanged(item);
            tax.setTax(1.08);
            List<ExtendedModelMap> models = List.of(new ExtendedModelMap(), new ExtendedModelMap(), new ExtendedModelMap());

            // when
            List<String> views = List.of(
                    ordersController.saveOrderItem(ORDER_ID, "i1", name, null, models.get(0)),
                    ordersController.saveOrderItem(ORDER_ID, "i1", qty, null, models.get(1)),
                    ordersController.saveOrderItem(ORDER_ID, "i1", tax, null, models.get(2)));

            // then: the page comes back with the full reason, and nothing is saved, not even the comment
            assertThat(views).containsOnly("orders/item");
            assertThat(models).allSatisfy(model -> assertThat(model.getAttribute("itemError"))
                    .isEqualTo("order.item.error.sale.locked.invoiced"));
            assertThat(item.getName()).isEqualTo("Ryzen");
            assertThat(item.getQty()).isEqualTo(1);
            assertThat(item.getTax()).isEqualTo(1.23);
            assertThat(item.getComment()).isEqualTo("old");
            verify(orderItemsRepository, never()).save(any());
            verifyNoInteractions(orderLifecycle);
        }

        @Test
        void savingAnItemOfAnInvoicedOrderWithItsSaleFieldsUnchangedSavesTheOtherFields() {
            // given: the form posts every field, the read-only ones unchanged
            OrderItem item = storedNewItem(invoiced(OrderStatus.Delivered));
            OrderItem posted = postedUnchanged(item);
            posted.setName(" Ryzen ");

            // when
            String view = ordersController.saveOrderItem(ORDER_ID, "i1", posted, null, new ExtendedModelMap());

            // then: the stored name is kept exactly, the comment and serial number are saved
            assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(item.getName()).isEqualTo("Ryzen");
            assertThat(item.getComment()).isEqualTo("new comment");
            assertThat(item.getSerialNo()).isEqualTo("SN-1");
            verify(orderItemsRepository).save(item);
        }

        @Test
        void whileAnEReceiptIsBeingIssuedAChangedItemNameIsRefusedWithItsOwnReason() {
            // given
            Order order = order(OrderStatus.Realization);
            OrderItem item = storedNewItem(order);
            when(receiptAttemptService.locksOrder(order)).thenReturn(true);
            OrderItem posted = postedUnchanged(item);
            posted.setName("Ryzen 9");
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.saveOrderItem(ORDER_ID, "i1", posted, null, model);

            // then
            assertThat(view).isEqualTo("orders/item");
            assertThat(model.getAttribute("itemError")).isEqualTo("order.item.error.sale.locked.receipt");
            assertThat(item.getName()).isEqualTo("Ryzen");
            verify(orderItemsRepository, never()).save(any());
        }

        @Test
        void anOpenOrderStillSavesAChangedNameQuantityAndVatRate() {
            // given
            OrderItem item = storedNewItem(order(OrderStatus.New));
            OrderItem posted = postedUnchanged(item);
            posted.setName("Ryzen 9");
            posted.setQty(2);
            posted.setTax(1.08);

            // when
            ordersController.saveOrderItem(ORDER_ID, "i1", posted, null, new ExtendedModelMap());

            // then
            assertThat(item.getName()).isEqualTo("Ryzen 9");
            assertThat(item.getQty()).isEqualTo(2);
            assertThat(item.getTax()).isEqualTo(1.08);
        }

        @Test
        void theItemPageNamesWhyTheSaleFieldsAreFixed() {
            // given
            Order invoicedOrder = invoiced(OrderStatus.Delivered);
            storedNewItem(invoicedOrder);
            ExtendedModelMap invoicedModel = new ExtendedModelMap();
            ExtendedModelMap issuingModel = new ExtendedModelMap();
            ExtendedModelMap openModel = new ExtendedModelMap();

            // when
            ordersController.getOrderItem(ORDER_ID, "i1", invoicedModel);
            Order issuing = order(OrderStatus.Realization);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(issuing);
            when(receiptAttemptService.locksOrder(issuing)).thenReturn(true);
            ordersController.getOrderItem(ORDER_ID, "i1", issuingModel);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.New));
            ordersController.getOrderItem(ORDER_ID, "i1", openModel);

            // then
            assertThat(invoicedModel.getAttribute("saleLock")).isEqualTo(ItemSaleLock.INVOICED);
            assertThat(invoicedModel.getAttribute("priceLockedKey")).isEqualTo("order.item.form.price.locked.invoiced");
            assertThat(issuingModel.getAttribute("saleLock")).isEqualTo(ItemSaleLock.RECEIPT_ISSUING);
            assertThat(issuingModel.getAttribute("priceLockedKey")).isEqualTo("order.item.form.price.locked.receipt");
            assertThat(openModel.getAttribute("saleLock")).isNull();
            assertThat(openModel.getAttribute("priceLocked")).isEqualTo(false);
        }

        @Test
        void splittingASetIsRefusedOnceInvoicedAndWhileAnEReceiptIsBeingIssued() {
            // given
            SplitGroupForm form = new SplitGroupForm();
            form.setItemId("i1");
            Order issuing = order(OrderStatus.Realization);
            when(receiptAttemptService.locksOrder(issuing)).thenReturn(true);
            RedirectAttributesModelMap afterDocument = new RedirectAttributesModelMap();
            RedirectAttributesModelMap whileIssuing = new RedirectAttributesModelMap();

            // when
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(invoiced(OrderStatus.Delivered));
            ordersController.splitGroupItem(ORDER_ID, form, afterDocument, polish);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(issuing);
            ordersController.splitGroupItem(ORDER_ID, form, whileIssuing, polish);

            // then
            assertThat(flash(afterDocument)).containsEntry("errorMessage", "order.item.split.group.locked.invoiced");
            assertThat(flash(whileIssuing)).containsEntry("errorMessage", "order.item.split.group.locked.receipt");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void cancellingIsRefusedWhileAnEReceiptIsBeingIssued() {
            // given
            Order order = order(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(receiptAttemptService.locksOrder(order)).thenReturn(true);
            RedirectAttributesModelMap page = new RedirectAttributesModelMap();
            RedirectAttributesModelMap cancel = new RedirectAttributesModelMap();

            // when
            String pageView = ordersController.confirmCancelOrder(ORDER_ID, new ExtendedModelMap(), page, polish);
            String cancelView = ordersController.cancelOrder(ORDER_ID, cancel, polish);

            // then
            assertThat(pageView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(cancelView).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
            assertThat(flash(page)).containsEntry("errorMessage", "order.page.cancel.locked.receipt");
            assertThat(flash(cancel)).containsEntry("errorMessage", "order.page.cancel.locked.receipt");
            verifyNoInteractions(ordersManager);
        }

        @Test
        void theNoJsCancelConfirmationWarnsAboutAFiscalisedEReceipt() {
            // given
            Order order = invoiced(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            when(receiptAttemptService.hasFiscalisedReceipt(order)).thenReturn(true);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = ordersController.confirmCancelOrder(ORDER_ID, model, new RedirectAttributesModelMap(), polish);

            // then
            assertThat(view).isEqualTo("settings-confirm");
            assertThat(((ConfirmAction) model.get("confirm")).message())
                    .isEqualTo("order.page.cancel.confirm.message order.page.cancel.confirm.receipt");
        }

        @Test
        void aCancelAfterTheEReceiptIsFiscalisedGoesThrough() {
            // given: the document is on the order, so no attempt waits any more
            Order order = invoiced(OrderStatus.Delivered);
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            ordersController.cancelOrder(ORDER_ID, redirect, polish);

            // then
            verify(ordersManager).cancelOrder(STORE_ID, ORDER_ID);
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

    /** An order id from another store (or none at all) is answered like a missing page, before anything is read or saved. */
    @Nested
    class ForeignOrder {

        private final Locale polish = Locale.forLanguageTag("pl");

        @BeforeEach
        void anotherStoresOrder() {
            when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);
        }

        private OrderItemsForm selectedItem() {
            OrderItem item = new OrderItem();
            item.setItemId("i1");
            item.setSelected(true);
            return new OrderItemsForm(new ArrayList<>(List.of(item)));
        }

        @Test
        void aBulkConfirmationForAnotherStoreIsNotFound() {
            // when / then
            assertThatThrownBy(() -> ordersController.confirmBulk(ORDER_ID, BulkAction.REMOVE, selectedItem(),
                    new ExtendedModelMap(), new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
            verifyNoInteractions(ordersManager, orderItemsRepository);
        }

        @Test
        void splittingASetOfAnotherStoresOrderIsNotFoundAndSavesNothing() {
            // given
            SplitGroupForm form = new SplitGroupForm();
            form.setItemId("i1");

            // when / then
            assertThatThrownBy(() -> ordersController.splitGroupItem(ORDER_ID, form, new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            verify(ordersManager, never()).splitGroupItem(any(), any(), any());
        }

        @Test
        void assigningFromTheWarehouseToAnotherStoresOrderIsNotFound() {
            // when / then
            assertThatThrownBy(() -> ordersController.assignFromWarehouse(ORDER_ID, "i1", "w1",
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            verify(ordersManager, never()).assignFromWarehouse(any(), any(), any(), any());
        }

        @Test
        void printsAndTheItemPageOfAnotherStoresOrderAreNotFound() {
            // when / then
            assertThatThrownBy(() -> ordersController.getOrderCard(ORDER_ID, new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.getOrderCollectionProtocol(ORDER_ID, new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.getOrderItem(ORDER_ID, "i1", new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.saveOrderItem(ORDER_ID, "i1", new OrderItem(), null, new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(orderItemsRepository);
        }

        @Test
        void printsOfAnotherStoresOrderAreNotFoundForTheSuperAdmin() {
            // given
            when(ordersRepository.findById("other-store", ORDER_ID)).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> ordersController.getOrderCardForSuperAdmin("other-store", ORDER_ID, new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.getOrderCollectionProtocolForSuperAdmin("other-store", ORDER_ID,
                    new ExtendedModelMap()))
                    .isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(orderItemsRepository);
        }

        @Test
        void deleteCancelSplitAndMoveOfAnotherStoresOrderAreNotFound() {
            // when / then
            assertThatThrownBy(() -> ordersController.deleteOrder(ORDER_ID, new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.cancelOrder(ORDER_ID, new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.splitOrder(ORDER_ID, selectedItem(), new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.moveItemsToOrder(ORDER_ID, selectedItem(), "51aa",
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.moveTarget(ORDER_ID, "51aa", polish))
                    .isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(ordersManager, orderReferenceResolver);
        }

        @Test
        void everyBulkActionOnAnotherStoresOrderIsNotFound() {
            // when / then
            assertThatThrownBy(() -> ordersController.removeSelectedItemsFromOrder(ORDER_ID, selectedItem(),
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.moveSelectedItemsToAllocation(ORDER_ID, selectedItem(),
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.moveSelectedItemsToTheWarehouse(ORDER_ID, selectedItem(),
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.moveSelectedItemsToTheWarehouseForRMA(ORDER_ID, selectedItem(),
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(ordersManager);
        }

        @Test
        void theAddressPageAndItsSaveForAnotherStoresOrderAreNotFound() {
            // given
            Order posted = new Order(STORE_ID);
            posted.setShippingDetails(new ShippingDetails());

            // when / then
            assertThatThrownBy(() -> ordersController.showAddressDetails(ORDER_ID, "shipping", new ExtendedModelMap(),
                    new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> ordersController.updateAddressDetails(ORDER_ID, "shipping", posted, null, new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new ExtendedModelMap(), new RedirectAttributesModelMap(), polish))
                    .isInstanceOf(ResponseStatusException.class);
            verify(ordersRepository, never()).save(any());
        }
    }

    static BillingDetails validBilling() {
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setStreetAndNumber("ul. Prosta 1");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("jan@example.pl");
        billing.setPhone("+48 600 700 800");
        return billing;
    }

    static ShippingDetails validShipping() {
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Anna");
        shipping.setSurname("Nowak");
        shipping.setStreetAndNumber("ul. Krzywa 2");
        shipping.setPostalCode("30-001");
        shipping.setCity("Kraków");
        shipping.setCountry("PL");
        shipping.setEmail("anna@example.pl");
        shipping.setPhone("600700800");
        return shipping;
    }
}
