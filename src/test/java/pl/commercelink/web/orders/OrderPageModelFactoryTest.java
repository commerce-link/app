package pl.commercelink.web.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.deliveries.DropshipAssessment;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.inventory.deliveries.DropshipRejection;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.CourierCancellation;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.receipts.ReceiptAlerts;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.receipts.ReceiptLock;
import pl.commercelink.receipts.ReceiptOrderView;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.receipts.ReceiptOrderState;
import pl.commercelink.receipts.ReceiptPageProblem;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPageModelFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private StoresRepository storesRepository;
    @Mock private OrderEventsRepository orderEventsRepository;
    @Mock private DropshipItemLookup dropshipItemLookup;
    @Mock private SupplierLabels supplierLabels;
    @Mock private ShipmentCarrierOptions shipmentCarrierOptions;
    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private TaxonomyCache taxonomyCache;
    @Mock private ReceiptAttemptService receiptAttemptService;
    @Mock private ReceiptAlerts receiptAlerts;
    @Mock private ShippingService shippingService;
    private final DeliveryRedirectResolver deliveryRedirectResolver = new DeliveryRedirectResolver();
    private final DropshipEligibility dropshipEligibility = DropshipEligibilityStubs.acceptingEverySupplier();
    private final MessageSource messageSource = messages();

    private OrderPageModelFactory factory;

    private static MessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }


    @BeforeEach
    void setUp() {
        factory = new OrderPageModelFactory(storesRepository, orderEventsRepository, dropshipItemLookup,
                deliveryRedirectResolver, dropshipEligibility, supplierLabels, shipmentCarrierOptions, productCatalogRepository, taxonomyCache,
                messageSource, receiptAttemptService, receiptAlerts, shippingService);
        ReflectionTestUtils.setField(factory, "appDomain", "https://app.example");
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierLabels.forStore(any())).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of());
        when(orderEventsRepository.findByOrderId(anyString())).thenReturn(List.of());
        when(receiptAttemptService.orderState(any(), any(), any(), any())).thenReturn(ReceiptOrderState.NONE);
        when(shippingService.isAvailable(any())).thenReturn(true);
    }

    private static Order order(OrderStatus status) {
        Order order = new Order("store-1");
        order.setStatus(status);
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        order.addShipment(new Shipment(ShipmentType.Courier));
        return order;
    }

    private static OrderItem item(FulfilmentStatus status) {
        OrderItem item = new OrderItem("o", "CPU", "Ryzen", 1, 100, "MFN", false);
        item.setStatus(status);
        return item;
    }

    @Test
    void aSuperAdminSeesTheOrderReadOnlyWithoutActions() {
        // when
        OrderPageModel page = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(true, false, null), PL);

        // then
        assertThat(page.readOnly()).isTrue();
        assertThat(page.closed()).isFalse();
        assertThat(page.backHref()).isNull();
        assertThat(page.header().canChangeStatus()).isFalse();
        assertThat(page.header().primaryAction()).isNull();
        assertThat(page.items().selectable()).isFalse();
        assertThat(page.items().canAddItems()).isFalse();
        assertThat(page.items().products().get(0).actions()).isEmpty();
        assertThat(page.header().cardHref()).startsWith("/dashboard/store/store-1/orders/");
        assertThat(page.settings().editable()).isFalse();
    }

    @Test
    void aSuperAdminGetsNoItemActionsWhileAUserOnAnOpenOrderDoes() {
        // when: OrderItemRow.Context.readOnly is closed OR SUPER_ADMIN
        OrderPageModel superAdmin = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(true, false, null), PL);
        OrderPageModel user = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, false, null), PL);
        OrderPageModel admin = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(superAdmin.readOnly()).isTrue();
        assertThat(superAdmin.items().products().get(0).actions()).isEmpty();
        assertThat(user.readOnly()).isFalse();
        assertThat(user.items().products().get(0).actions()).isNotEmpty();
        assertThat(admin.readOnly()).isFalse();
        assertThat(admin.items().products().get(0).actions()).isNotEmpty();
    }

    @Test
    void aSuperAdminSeesCostAndProfitReadOnlyAndAUserSeesThemToo() {
        // when
        OrderPageModel superAdmin = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(true, false, null), PL);
        OrderPageModel user = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, false, null), PL);

        // then
        assertThat(superAdmin.finances().costs()).isNotNull();
        assertThat(superAdmin.items().products().get(0).margin()).isNotNull();
        assertThat(superAdmin.readOnly()).isTrue();
        assertThat(superAdmin.admin()).isFalse();
        assertThat(user.finances().costs()).isNotNull();
        assertThat(user.items().products().get(0).margin()).isNotNull();
    }

    @Test
    void aSuperAdminMayCopyTheCustomerLinkUnderTheSameConditionsAsTheStore() {
        // given
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setClientOrderPageEnabled(true);
        storesRepository.findById("store-1").setFulfilmentConfiguration(fulfilment);
        OrderPageModelFactory.Viewer superAdmin = new OrderPageModelFactory.Viewer(true, false, null);

        // when
        OrderPageModel open = factory.build(order(OrderStatus.Assembly), List.of(), superAdmin, PL);
        OrderPageModel completed = factory.build(order(OrderStatus.Completed), List.of(), superAdmin, PL);

        // then
        assertThat(open.header().clientOrderUrl()).startsWith("https://app.example");
        assertThat(completed.header().clientOrderUrl()).isNull();
    }

    @Test
    void aPlainOrderKeepsItsBulkActionsAvailable() {
        // when
        OrderPageModel page = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.items().bulkAvailable()).isTrue();
    }

    @Test
    void aCompletedOrderIsClosedAndReadOnlyForTheStoreToo() {
        // when
        OrderPageModel page = factory.build(order(OrderStatus.Completed), List.of(),
                new OrderPageModelFactory.Viewer(false, true, "/dashboard/orders?view=Completed"), PL);

        // then
        assertThat(page.closed()).isTrue();
        assertThat(page.readOnly()).isTrue();
        assertThat(page.header().completedAutomatically()).isTrue();
        assertThat(page.backHref()).isEqualTo("/dashboard/orders?view=Completed");
    }

    @Test
    void aUserReceivesTheSameCostAndProfitAsAnAdmin() {
        // when
        OrderPageModel user = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, false, null), PL);
        OrderPageModel admin = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(user.finances().costs()).isNotNull().isEqualTo(admin.finances().costs());
        assertThat(user.items().products().get(0).margin()).isNotNull()
                .isEqualTo(admin.items().products().get(0).margin());
    }

    @Test
    void anIncompletePaymentStillAppearsInThePaymentsList() {
        // given: a zero-amount payment with no reference is not Payment.isComplete(), but it is still a
        // recorded payment attempt the operator must see, not one that silently disappears from the list
        Order order = order(OrderStatus.New);
        order.addPayment(new Payment(null, null, PaymentSource.BankTransfer, 0, 0));

        // when
        OrderPageModel page = factory.build(order, List.of(), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.payments().rows()).hasSize(1);
    }

    @Test
    void theCourierIsThePrimaryActionOnlyWhenAShipmentWaitsForIt() {
        // when
        OrderPageModel realization = factory.build(order(OrderStatus.Realization), List.of(),
                new OrderPageModelFactory.Viewer(false, true, null), PL);
        OrderPageModel assembly = factory.build(order(OrderStatus.Assembly), List.of(),
                new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(realization.header().primaryAction().href()).endsWith("/shipping");
        assertThat(assembly.header().primaryAction()).isNull();
    }

    @Test
    void aRealizationOrderWithEveryShipmentAlreadyHandedToACourierHasNoPrimaryAction() {
        // given: the courier button only books a shipment that is not sent yet; nothing is left to book
        // once every shipment already carries a carrier, a tracking number and a ship date
        Order order = order(OrderStatus.Realization);
        Shipment sent = order.getShipments().get(0);
        sent.setCarrier("DPD");
        sent.setTrackingNo("T-1");
        sent.setShippedAt(LocalDateTime.now());

        // when
        OrderPageModel page = factory.build(order, List.of(), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.header().primaryAction()).isNull();
    }

    @Test
    void aFullyInvoicedB2BOrderCannotAddADocumentAndDoesNotThrow() {
        // given: documents() threw NPE — List.of(...).contains(null) — when a fully invoiced order left
        // getNextDocumentToIssue() empty and the old code called manual.contains(next) without a null guard
        Order order = order(OrderStatus.New);
        BillingDetails billing = new BillingDetails();
        billing.setTaxId("1234567890");
        order.setBillingDetails(billing);
        order.addDocument(new Document("fv", "FV/2026/09/1", null, DocumentType.InvoiceVat));

        // when
        OrderPageModel page = factory.build(order, List.of(), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.documents().canAdd()).isFalse();
    }

    @Test
    void aDropshipOrderWithItemsWaitingForTheSupplierOffersTheSupplierOrderToAnAdminOnly() {
        // given
        Order dropship = order(OrderStatus.New);
        dropship.setFulfilmentType(FulfilmentType.DirectToConsumer);
        OrderItem allocated = item(FulfilmentStatus.Allocation);
        allocated.setDeliveryId("Acme");

        // when
        OrderPageModel admin = factory.build(dropship, List.of(allocated), new OrderPageModelFactory.Viewer(false, true, null), PL);
        OrderPageModel user = factory.build(dropship, List.of(allocated), new OrderPageModelFactory.Viewer(false, false, null), PL);

        // then
        assertThat(admin.header().primaryAction().href())
                .isEqualTo("/dashboard/deliveries/create/Acme?order=" + dropship.getOrderId() + "&from=order");
        assertThat(user.header().primaryAction()).isNull();
        assertThat(user.items().products().get(0).deliveryHref()).isNull();
    }

    @Test
    void withItemsAtTwoSuppliersTheSupplierOrderNamesTheFirstWaitingOne() {
        // given: the order has waiting items at several suppliers
        Order dropship = order(OrderStatus.New);
        dropship.setFulfilmentType(FulfilmentType.DirectToConsumer);
        OrderItem fresh = item(FulfilmentStatus.New);
        fresh.setDeliveryId("Other");
        OrderItem first = item(FulfilmentStatus.Allocation);
        first.setDeliveryId("Acme B");
        OrderItem second = item(FulfilmentStatus.Allocation);
        second.setDeliveryId("Elko");

        // when
        OrderPageModel page = factory.build(dropship, List.of(fresh, first, second),
                new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.header().primaryAction().href()).endsWith("/deliveries/create/Acme%20B?order=" + dropship.getOrderId() + "&from=order");
    }

    @Test
    void aSupplierWithoutDropshippingGetsNoSupplierOrderButItsWarehouseDeliveryPlanning() {
        // given: the dropship page and the deliveries planning accept only Acme; AcmeB goes the warehouse route
        Order dropship = order(OrderStatus.New);
        dropship.setFulfilmentType(FulfilmentType.DirectToConsumer);
        OrderItem atAcmeB = item(FulfilmentStatus.Allocation);
        atAcmeB.setDeliveryId("AcmeB");
        OrderItem atAcme = item(FulfilmentStatus.Allocation);
        atAcme.setDeliveryId("Acme");
        org.mockito.Mockito.doReturn(DropshipAssessment.of(List.of("Acme"))).when(dropshipEligibility).assess(any(), any());
        OrderPageModelFactory.Viewer admin = new OrderPageModelFactory.Viewer(false, true, null);

        // when
        OrderPageModel onlyAcmeB = factory.build(dropship, List.of(atAcmeB), admin, PL);
        OrderPageModel both = factory.build(dropship, List.of(atAcmeB, atAcme), admin, PL);

        // then
        assertThat(onlyAcmeB.header().primaryAction()).isNull();
        assertThat(onlyAcmeB.items().products().get(0).deliveryHref()).isEqualTo("/dashboard/deliveries/create/AcmeB");
        assertThat(both.header().primaryAction().href())
                .isEqualTo("/dashboard/deliveries/create/Acme?order=" + dropship.getOrderId() + "&from=order");
        assertThat(both.items().products().get(1).deliveryHref())
                .isEqualTo("/dashboard/deliveries/create/Acme?order=" + dropship.getOrderId() + "&from=order");
    }

    @Test
    void anOrderTheDropshipPageRefusesAsAWholeOffersNoSupplierOrder() {
        // given: e.g. no shipping address; the page would only redirect back with the reason
        Order dropship = order(OrderStatus.New);
        dropship.setFulfilmentType(FulfilmentType.DirectToConsumer);
        OrderItem allocated = item(FulfilmentStatus.Allocation);
        allocated.setDeliveryId("Acme");
        org.mockito.Mockito.doReturn(DropshipAssessment.rejected(DropshipRejection.NO_SHIPPING_DETAILS)).when(dropshipEligibility).assess(any(), any());

        // when
        OrderPageModel page = factory.build(dropship, List.of(allocated), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.header().primaryAction()).isNull();
        assertThat(page.items().products().get(0).deliveryHref()).isEqualTo("/dashboard/deliveries/create/Acme");
    }

    @Test
    void aWarehouseOrderIsNeverAssessedForDropshipping() {
        // given
        Order warehouse = order(OrderStatus.New);
        OrderItem allocated = item(FulfilmentStatus.Allocation);
        allocated.setDeliveryId("Acme");

        // when
        factory.build(warehouse, List.of(allocated), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        org.mockito.Mockito.verify(dropshipEligibility, org.mockito.Mockito.never()).assess(any(), any());
    }

    @Test
    void aNewItemOfADropshipOrderDoesNotOfferTheSupplierOrderYet() {
        // given: the dropship page accepts only items in Allocation; a New one needs its supplier assigned first
        Order dropship = order(OrderStatus.New);
        dropship.setFulfilmentType(FulfilmentType.DirectToConsumer);
        OrderItem fresh = item(FulfilmentStatus.New);
        fresh.setDeliveryId("Acme");
        OrderItem allocated = item(FulfilmentStatus.Allocation);
        allocated.setDeliveryId("Acme");
        OrderPageModelFactory.Viewer admin = new OrderPageModelFactory.Viewer(false, true, null);

        // when
        OrderPageModel withNew = factory.build(dropship, List.of(fresh), admin, PL);
        OrderPageModel withAllocated = factory.build(dropship, List.of(allocated), admin, PL);

        // then
        assertThat(withNew.header().primaryAction()).isNull();
        assertThat(withAllocated.header().primaryAction().labelKey()).isEqualTo("order.page.action.dropship");
    }

    @Test
    void theCourierOrderCannotBeCancelledOnceItsParcelIsDelivered() {
        // given
        Order order = order(OrderStatus.Delivered);
        Shipment sent = order.getShipments().get(0);
        sent.setCarrier("InPost");
        sent.setTrackingNo("T-1");
        sent.setShippedAt(LocalDateTime.now().minusDays(2));
        sent.setExternalId("PKG-1");
        OrderPageModelFactory.Viewer admin = new OrderPageModelFactory.Viewer(false, true, null);

        // when
        OrderPageModel onTheWay = factory.build(order, List.of(item(FulfilmentStatus.Delivered)), admin, PL);
        sent.setDeliveredAt(LocalDateTime.now().minusDays(1));
        OrderPageModel delivered = factory.build(order, List.of(item(FulfilmentStatus.Delivered)), admin, PL);

        // then
        assertThat(onTheWay.shipments().canCancelCourier()).isTrue();
        assertThat(delivered.shipments().canCancelCourier()).isFalse();
    }

    @Test
    void aCourierOrderWithoutItsShippedDateIsNotBookedAgainAndStaysCancellable() {
        // given: legacy data (the dialog no longer lets the date go); the paid label is there whatever the dates say
        Order order = order(OrderStatus.Realization);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        order.getShipments().get(0).setShippedAt(null);

        // when
        OrderPageModel page = factory.build(order, List.of(), ADMIN, PL);

        // then
        assertThat(page.header().primaryAction()).isNull();
        assertThat(page.shipments().canCancelCourier()).isTrue();
        assertThat(page.shipments().rows().get(0).removeReasonKey()).isEqualTo("order.shipments.remove.locked.courier");
    }

    @Test
    void aCourierShipmentOfAnOrderNotYetShippingSaysWhenItCanBeCancelled() {
        // given: a courier ordered while the order is still being assembled; "Cancel courier order" is not offered yet
        Order assembling = order(OrderStatus.Assembly);
        labelled(assembling.getShipments().get(0), "T-1", "PKG-1");
        // two couriers: the button only ever cancels the first courier order on the list
        Order twoCouriers = order(OrderStatus.Shipping);
        labelled(twoCouriers.getShipments().get(0), "T-1", "PKG-1");
        Shipment second = new Shipment(ShipmentType.Courier);
        labelled(second, "T-2", "PKG-2");
        twoCouriers.getShipments().add(second);

        // one courier order of two parcels: they share its externalId and "Cancel courier order" cancels both
        Order twoParcels = order(OrderStatus.Shipping);
        labelled(twoParcels.getShipments().get(0), "T-1", "PKG-1");
        Shipment parcel = new Shipment(ShipmentType.Courier);
        labelled(parcel, "T-1B", "PKG-1");
        twoParcels.getShipments().add(parcel);

        // when
        OrderPageModel.ShipmentsCard early = factory.build(assembling, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentsCard shipping = factory.build(twoCouriers, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentsCard parcels = factory.build(twoParcels, List.of(), ADMIN, PL).shipments();

        // then: the reason never points to a button the page does not show
        assertThat(early.canCancelCourier()).isFalse();
        assertThat(early.rows().get(0).removeHref()).isNull();
        assertThat(early.rows().get(0).removeReasonKey()).isEqualTo("order.shipments.remove.locked.courierLater");
        assertThat(shipping.canCancelCourier()).isTrue();
        assertThat(shipping.rows().get(0).removeReasonKey()).isEqualTo("order.shipments.remove.locked.courier");
        assertThat(shipping.rows().get(1).removeReasonKey()).isEqualTo("order.shipments.remove.locked.courierNotFirst");
        assertThat(parcels.canCancelCourier()).isTrue();
        assertThat(parcels.rows()).extracting(OrderPageModel.ShipmentRow::removeReasonKey)
                .containsExactly("order.shipments.remove.locked.courier", "order.shipments.remove.locked.courier");
    }

    @Test
    void aCourierShipmentWhoseCancellationFailedOrIsUnconfirmedCanBeRemoved() {
        // given: the operator settles the label in the provider's panel and drops the record
        Order failed = order(OrderStatus.Shipping);
        labelled(failed.getShipments().get(0), "T-1", "PKG-1");
        failed.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        failed.getShipments().get(0).setCancellation(failed.getShipments().get(0).getCancellation().failed());
        Order unconfirmed = order(OrderStatus.Shipping);
        labelled(unconfirmed.getShipments().get(0), "T-1", "PKG-1");
        unconfirmed.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        unconfirmed.getShipments().get(0).setCancellation(unconfirmed.getShipments().get(0).getCancellation().unconfirmed());

        // when
        OrderPageModel.ShipmentRow failedRow = factory.build(failed, List.of(), ADMIN, PL).shipments().rows().get(0);

        // then
        assertThat(OrderPageModelFactory.removeLockedKey(failed, 0)).isNull();
        assertThat(OrderPageModelFactory.removeLockedKey(unconfirmed, 0)).isNull();
        assertThat(failedRow.removeHref()).isNotNull();
        assertThat(failedRow.removeReasonKey()).isNull();
    }

    @Test
    void aCourierShipmentWithACancellationPendingStaysLocked() {
        // given
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        order.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now()));

        // when
        String locked = OrderPageModelFactory.removeLockedKey(order, 0);

        // then
        assertThat(locked).isEqualTo("order.shipments.remove.error.courier");
    }

    @Test
    void aCancellationInProgressShowsAnInfoPillGreysTheCancelActionAndAsksThePageToPoll() {
        // given
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        order.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusSeconds(10)));

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentRow row = card.rows().get(0);

        // then
        assertThat(row.cancellationKey()).isEqualTo("shipment.cancellation.pending");
        assertThat(row.cancellationTone()).isEqualTo("is-info");
        assertThat(card.canCancelCourier()).isTrue();
        assertThat(card.cancelCourierLockedKey()).isEqualTo("order.shipments.cancel.locked.pending");
        assertThat(card.cancellationPollHref())
                .isEqualTo("/dashboard/orders/" + order.getOrderId() + "/shipments/cancellation-state");
        assertThat(row.removeHref()).isNull();
    }

    @Test
    void aStalePendingCancellationReadsAsUnconfirmedAndTheCancelActionRechecksIt() {
        // given: no answer for longer than CourierCancellation.STALE
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        order.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(6)));

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).cancellationKey()).isEqualTo("shipment.cancellation.unconfirmed");
        assertThat(card.rows().get(0).cancellationTone()).isEqualTo("is-warn");
        assertThat(card.cancelCourierLockedKey()).isNull();
        assertThat(card.cancellationPollHref()).isNull();
    }

    @Test
    void anUnconfirmedCancellationShowsAWarnPillAndItsRemovalWarnsAboutTheLabel() {
        // given
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        order.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        order.getShipments().get(0).setCancellation(order.getShipments().get(0).getCancellation().unconfirmed());

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentRow row = card.rows().get(0);

        // then
        assertThat(row.cancellationKey()).isEqualTo("shipment.cancellation.unconfirmed");
        assertThat(row.cancellationTone()).isEqualTo("is-warn");
        assertThat(card.canCancelCourier()).isTrue();
        assertThat(card.cancelCourierLockedKey()).isNull();
        assertThat(card.cancellationPollHref()).isNull();
        assertThat(row.removeHref()).isNotNull();
        assertThat(row.removeMessageKey()).isEqualTo("order.shipments.remove.confirm.message.cancellationUnresolved");
        assertThat(OrderPageModelFactory.removeShipmentMessageKey(order, 0))
                .isEqualTo("order.shipments.remove.confirm.message.cancellationUnresolved");
    }

    @Test
    void aFailedCancellationShowsABadPillWithoutAReason() {
        // given
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        order.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        order.getShipments().get(0).setCancellation(order.getShipments().get(0).getCancellation().failed());

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentRow row = card.rows().get(0);

        // then
        assertThat(row.cancellationKey()).isEqualTo("shipment.cancellation.failed");
        assertThat(row.cancellationTone()).isEqualTo("is-bad");
        assertThat(card.cancelCourierLockedKey()).isNull();
        assertThat(row.removeMessageKey()).isEqualTo("order.shipments.remove.confirm.message.cancellationUnresolved");
    }

    @Test
    void aShipmentWithoutACancellationHasNoPillAndTheUsualRemovalText() {
        // given
        Order order = order(OrderStatus.Shipping);
        order.getShipments().get(0).setTrackingNo("T-1");

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).cancellationKey()).isNull();
        assertThat(card.rows().get(0).cancellationTone()).isNull();
        // main's text for the only shipment of a Shipping order, which its removal takes back to Realization
        assertThat(card.rows().get(0).removeMessageKey()).isEqualTo("order.shipments.remove.confirm.message.last.realization");
        assertThat(card.cancellationPollHref()).isNull();
    }

    @Test
    void aReadOnlyPageShowsTheCancellationPillButDoesNotPoll() {
        // given: the super admin page has no route to the store's polling endpoint
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        order.getShipments().get(0).setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now()));

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(),
                new OrderPageModelFactory.Viewer(true, false, null), PL).shipments();

        // then
        assertThat(card.rows().get(0).cancellationKey()).isEqualTo("shipment.cancellation.pending");
        assertThat(card.cancellationPollHref()).isNull();
    }

    @Test
    void aDeliveredShipmentStaysLockedWhateverItsCancellation() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment shipment = order.getShipments().get(0);
        labelled(shipment, "T-1", "PKG-1");
        shipment.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        shipment.setCancellation(shipment.getCancellation().failed());
        shipment.setDeliveredAt(LocalDateTime.now());

        // when
        String locked = OrderPageModelFactory.removeLockedKey(order, 0);

        // then
        assertThat(locked).isEqualTo("order.shipments.remove.error.shipmentDelivered");
    }

    @Test
    void aCourierShipmentOffersNoCarrierOrNumberEdit() {
        // given
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", "PKG-1");
        Shipment typed = new Shipment(ShipmentType.Courier);
        typed.setTrackingNo("T-2");
        order.getShipments().add(typed);

        // when
        List<OrderShipmentForm> forms = factory.build(order, List.of(), ADMIN, PL).shipments().forms();

        // then: the carrier gave the number; only "Cancel courier order" changes it
        assertThat(forms.get(0).courierOrder()).isTrue();
        assertThat(forms.get(1).courierOrder()).isFalse();
        assertThat(forms.get(0).today()).isEqualTo(java.time.LocalDate.now(OrderShipmentForm.OPERATOR_ZONE));
    }

    @Test
    void theRowOfTheLastUndeliveredShipmentSaysRemovingItDeliversTheOrder() {
        // given: shipment 1 delivered, shipment 2 on the way (Realization: both went out, so Shipping -> Delivered)
        Order order = order(OrderStatus.Realization);
        Shipment delivered = order.getShipments().get(0);
        delivered.setCarrier("DPD");
        delivered.setTrackingNo("T-1");
        delivered.setShippedAt(LocalDateTime.now().minusDays(3));
        delivered.setDeliveredAt(LocalDateTime.now().minusDays(1));
        Shipment onTheWay = new Shipment(ShipmentType.Courier);
        onTheWay.setCarrier("DPD");
        onTheWay.setTrackingNo("T-2");
        onTheWay.setShippedAt(LocalDateTime.now().minusDays(2));
        order.getShipments().add(onTheWay);

        // when
        OrderPageModel.ShipmentRow row = factory.build(order, List.of(), ADMIN, PL).shipments().rows().get(1);

        // then
        assertThat(row.removeHref()).isNotNull();
        assertThat(row.removeMessageKey()).isEqualTo("order.shipments.remove.confirm.delivers");
        assertThat(row.removeActionKey()).isEqualTo("order.shipments.remove.confirm.action.delivers");
    }

    @Test
    void addShipmentStartsFromTheCustomersChoiceKeptInTheOnlyPlaceholder() {
        // given: the only shipment holds nothing but the customer's pickup point
        Order order = order(OrderStatus.Realization);
        Shipment placeholder = order.getShipments().get(0);
        placeholder.setType(ShipmentType.PickupPoint);
        placeholder.setCarrier("InPost");
        placeholder.setCollectionPointCode("KRA01M");
        Order withData = order(OrderStatus.Realization);
        withData.getShipments().get(0).setTrackingNo("T-1");

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderShipmentForm blank = card.blank();
        OrderShipmentForm plain = factory.build(withData, List.of(), ADMIN, PL).shipments().blank();

        // then: "Add shipment" fills the placeholder, so its form starts from what the placeholder keeps
        assertThat(blank.isNew()).isTrue();
        assertThat(blank.type()).isEqualTo(ShipmentType.PickupPoint);
        assertThat(blank.carrier()).isEqualTo("InPost");
        assertThat(blank.collectionPointCode()).isEqualTo("KRA01M");
        assertThat(plain.type()).isEqualTo(ShipmentType.Courier);
        assertThat(plain.collectionPointCode()).isNull();
        // the placeholder offers no "Remove" (the server refuses one too) and no greyed one to explain
        assertThat(card.rows().get(0).removeHref()).isNull();
        assertThat(card.rows().get(0).removeReasonKey()).isNull();
        assertThat(OrderPageModelFactory.removeLockedKey(order, 0)).isEqualTo("order.shipments.remove.error.placeholder");
    }

    private static void labelled(Shipment shipment, String trackingNo, String packageId) {
        shipment.setCarrier("InPost");
        shipment.setTrackingNo(trackingNo);
        shipment.setShippedAt(LocalDateTime.now().minusDays(1));
        shipment.setExternalId(packageId);
    }

    @Test
    void dropshipItemsLockTheWarehouseMovesAndTheCourierCancellationNeedsAPackageId() {
        // given
        Order order = order(OrderStatus.Realization);
        Shipment sent = order.getShipments().get(0);
        sent.setCarrier("DPD");
        sent.setTrackingNo("T-1");
        sent.setShippedAt(LocalDateTime.now());
        OrderItem inDropship = item(FulfilmentStatus.Ordered);
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of(inDropship.getItemId()));

        // when
        OrderPageModel withoutPackage = factory.build(order, List.of(inDropship), new OrderPageModelFactory.Viewer(false, true, null), PL);
        sent.setExternalId("PKG-1");
        OrderPageModel withPackage = factory.build(order, List.of(inDropship), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(withoutPackage.items().bulkActions()).filteredOn(b -> b.action() == BulkAction.TO_WAREHOUSE)
                .singleElement().satisfies(b -> {
                    assertThat(b.available()).isFalse();
                    assertThat(b.reasonKey()).isEqualTo("order.items.action.dropship.locked");
                });
        assertThat(withoutPackage.shipments().canCancelCourier()).isFalse();
        assertThat(withPackage.shipments().canCancelCourier()).isTrue();
    }

    @Test
    void eventsAreTranslatedNewestFirst() {
        // given
        Order order = order(OrderStatus.New);
        when(orderEventsRepository.findByOrderId(order.getOrderId())).thenReturn(List.of(
                new OrderEvent(order.getOrderId(), EventType.email, "ORDER_CONFIRMATION", LocalDateTime.of(2026, 9, 21, 9, 14)),
                new OrderEvent(order.getOrderId(), EventType.action, "SHIPMENT_DELIVERED", LocalDateTime.of(2026, 9, 23, 16, 2)),
                new OrderEvent(order.getOrderId(), EventType.action, "SOMETHING_NEW", LocalDateTime.of(2026, 9, 22, 8, 0))));

        // when
        OrderPageModel page = factory.build(order, List.of(), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.history().events()).extracting(OrderPageModel.EventRow::at)
                .containsExactly("23.09.2026, 16:02", "22.09.2026, 08:00", "21.09.2026, 09:14");
        assertThat(page.history().events()).extracting(OrderPageModel.EventRow::titleKey)
                .containsExactly("order.event.type.action.SHIPMENT_DELIVERED", "order.event.type.other", "order.event.type.email");
        assertThat(page.history().events().get(2).argKey()).isEqualTo("email.notification.type.ORDER_CONFIRMATION");
        assertThat(page.history().events().get(1).arg()).isEqualTo("SOMETHING_NEW");
    }

    @Test
    void aRejectedDeliveryRequestShowsItsSupplierAndReasonInTheHistory() {
        // given
        Order order = order(OrderStatus.New);
        OrderEvent rejected = new OrderEvent(order.getOrderId(), EventType.action, OrderEvent.DELIVERY_REQUEST_REJECTED,
                LocalDateTime.of(2026, 10, 2, 9, 30));
        rejected.setDetails("Acme · brak towaru");
        when(orderEventsRepository.findByOrderId(order.getOrderId())).thenReturn(List.of(rejected));

        // when
        OrderPageModel page = factory.build(order, List.of(), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        OrderPageModel.EventRow row = page.history().events().get(0);
        assertThat(row.titleKey()).isEqualTo("order.event.type.action.DELIVERY_REQUEST_REJECTED");
        assertThat(row.argKey()).isNull();
        assertThat(row.arg()).isEqualTo("Acme · brak towaru");
    }

    private static OrderPageModelFactory.Viewer viewer() {
        return new OrderPageModelFactory.Viewer(false, true, null);
    }

    private static Order assembledOrderWithOneEmptyShipment() {
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembled);
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setShippedAt(LocalDateTime.now());
        order.addShipment(shipment);
        return order;
    }

    @Test
    void buildsForAnOrderWithNoAddressesSourceOrReview() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.New);
        order.setBillingDetails(null);
        order.setShippingDetails(null);
        order.setSource(null);
        order.setReview(null);
        order.setPayments(null);

        // when
        OrderPageModel page = factory.build(order, List.of(), new OrderPageModelFactory.Viewer(false, true, null), Locale.forLanguageTag("pl"));

        // then
        assertThat(page.header().clientName()).isNull();
        assertThat(page.customer().billing()).isEqualTo(new AddressBlock(null, null, null, null, null, null, null, null));
        assertThat(page.payments().rows()).isEmpty();
    }

    @Test
    void primaryActionIsCourierOnlyWhenAShipmentStillLacksItsLabel() {
        // given
        Order order = assembledOrderWithOneEmptyShipment();

        // when / then
        assertThat(factory.build(order, List.of(), viewer(), PL).header().primaryAction().labelKey()).isEqualTo("order.page.action.courier");
        order.getShipments().get(0).setExternalId("ext");
        order.getShipments().get(0).setTrackingNo("T");
        assertThat(factory.build(order, List.of(), viewer(), PL).header().primaryAction()).isNull();
    }

    @Test
    void anOrderWithoutShipmentsStillOffersTheCourier() {
        // given: the only shipment removed (2026-09-30)
        Order order = assembledOrderWithOneEmptyShipment();
        order.setShipments(new java.util.ArrayList<>());

        // when / then
        assertThat(factory.build(order, List.of(), viewer(), PL).header().primaryAction().labelKey()).isEqualTo("order.page.action.courier");
    }

    @Test
    void noCourierActionWithoutAShippingProvider() {
        // given: a store that types its shipping data in by hand (no courier account connected)
        Order order = assembledOrderWithOneEmptyShipment();
        when(shippingService.isAvailable(any())).thenReturn(false);

        // when
        OrderPageModel page = factory.build(order, List.of(), viewer(), PL);

        // then: the courier page would only refuse, so the header does not lead there
        assertThat(page.header().primaryAction()).isNull();
    }


    @Test
    void anOrderOfAStoreWhoseCourierAuthorisationWasLostRendersWithoutTheCourierAction() {
        // given: the real availability rule and carrier options over a store whose courier account lost its token
        // (ShippingProviderFactory#onAuthorizationLost stores the integration without a name)
        Store store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, null);
        when(storesRepository.findById("store-1")).thenReturn(store);
        ShippingService realShipping = new ShippingService();
        ReflectionTestUtils.setField(realShipping, "shippingProviderFactory", mock(ShippingProviderFactory.class));
        OrderPageModelFactory withRealShipping = new OrderPageModelFactory(storesRepository, orderEventsRepository,
                dropshipItemLookup, deliveryRedirectResolver, dropshipEligibility, supplierLabels,
                new ShipmentCarrierOptions(new CarrierDictionary()), productCatalogRepository, taxonomyCache,
                messageSource, receiptAttemptService, receiptAlerts, realShipping);
        ReflectionTestUtils.setField(withRealShipping, "appDomain", "https://app.example");

        // when
        OrderPageModel page = withRealShipping.build(assembledOrderWithOneEmptyShipment(), List.of(), viewer(), PL);

        // then: the page renders and does not lead to a courier page that cannot book
        assertThat(page.header().primaryAction()).isNull();
        assertThat(page.shipments()).isNotNull();
    }

    @Test
    void warehouseItemsReadAsTheStoresWarehouse() {
        // given
        Order order = order(OrderStatus.Realization);
        OrderItem item = item(FulfilmentStatus.Delivered);
        item.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);

        // when
        OrderPageModel page = factory.build(order, List.of(item), viewer(), PL);
        OrderItemRow.Delivery delivery = factory.delivery(order, item, List.of(item), viewer(), PL);

        // then: the technical id "Warehouse" never reaches the items table or the item page
        assertThat(page.items().products().get(0).deliveryLabel()).isEqualTo("Magazyn sklepu");
        assertThat(delivery.label()).isEqualTo("Magazyn sklepu");
        // the table's state pill leads nowhere for the warehouse (client 2026-10-06); the item page keeps its link
        assertThat(page.items().products().get(0).deliveryHref()).isNull();
        assertThat(delivery.href()).isEqualTo("/dashboard/warehouse");
    }

    @Test
    void onlyAnAdminGetsTheLinkToCreatingADeliveryWhileEveryoneGetsTheDelivery() {
        // given: a warehouse order, one item waiting for AcmeB, one already in a delivery
        Order order = order(OrderStatus.Realization);
        OrderItem awaiting = item(FulfilmentStatus.Allocation);
        awaiting.setDeliveryId("AcmeB");
        OrderItem ordered = item(FulfilmentStatus.Ordered);
        ordered.setDeliveryId("2f9eb794-74ee-4122-aff2-cc614b6d417d");
        OrderPageModelFactory.Viewer admin = viewer();
        OrderPageModelFactory.Viewer user = new OrderPageModelFactory.Viewer(false, false, null);
        OrderPageModelFactory.Viewer superAdmin = new OrderPageModelFactory.Viewer(true, false, null);

        // when
        List<OrderItemRow> asAdmin = factory.build(order, List.of(awaiting, ordered), admin, PL).items().products();
        List<OrderItemRow> asUser = factory.build(order, List.of(awaiting, ordered), user, PL).items().products();
        List<OrderItemRow> asSuperAdmin = factory.build(order, List.of(awaiting, ordered), superAdmin, PL).items().products();
        OrderItemRow.Delivery itemPageAsUser = factory.delivery(order, awaiting, List.of(awaiting, ordered), user, PL);

        // then: creating a delivery is the admin's (DeliveryCreateController), so a user's link would answer 403 — on
        // the items table and on the item page alike; the delivery itself is everyone's
        assertThat(asAdmin.get(0).deliveryHref()).isEqualTo("/dashboard/deliveries/create/AcmeB");
        assertThat(asAdmin.get(0).deliveryToCreate()).isTrue();
        assertThat(asUser.get(0).deliveryHref()).isNull();
        assertThat(itemPageAsUser.href()).isNull();
        assertThat(asSuperAdmin.get(0).deliveryHref()).isEqualTo("/dashboard/store/store-1/deliveries/create/AcmeB");
        assertThat(asUser.get(1).deliveryHref())
                .isEqualTo("/dashboard/deliveries/details?deliveryId=2f9eb794-74ee-4122-aff2-cc614b6d417d");
        assertThat(asUser.get(1).deliveryToCreate()).isFalse();
        assertThat(asSuperAdmin.get(1).deliveryHref())
                .isEqualTo("/dashboard/store/store-1/deliveries/details?deliveryId=2f9eb794-74ee-4122-aff2-cc614b6d417d");
    }

    @Test
    void addDocumentLockedKeyAgreesWithTheCardsAddDocumentEntry() {
        // given
        Order consumer = order(OrderStatus.Delivered);
        Order business = b2b(order(OrderStatus.Delivered));
        Order invoiced = b2b(order(OrderStatus.Delivered));
        invoiced.addDocument(new Document("fv", "FV/2026/09/1", null, DocumentType.InvoiceVat));
        Order completed = order(OrderStatus.Completed);
        OrderPageModelFactory.Viewer admin = new OrderPageModelFactory.Viewer(false, true, null);

        // when / then
        for (Order order : List.of(consumer, business, invoiced, completed)) {
            boolean canAdd = factory.build(order, List.of(), admin, PL).documents().canAdd();
            assertThat(canAdd).isEqualTo(OrderPageModelFactory.addDocumentLockedKey(order, null) == null);
        }
        assertThat(OrderPageModelFactory.addDocumentLockedKey(consumer, null)).isNull();
        assertThat(OrderPageModelFactory.addDocumentLockedKey(business, null)).isNull();
        assertThat(OrderPageModelFactory.addDocumentLockedKey(invoiced, null)).isEqualTo("order.documents.add.locked");
        assertThat(OrderPageModelFactory.addDocumentLockedKey(completed, null)).isEqualTo("order.documents.add.locked.closed");
        assertThat(OrderPageModelFactory.addDocumentLockedKey(consumer, DocumentType.InvoiceVat)).isEqualTo("order.documents.add.locked");
        assertThat(OrderPageModelFactory.manualDocumentTypes(business))
                .containsExactly(DocumentType.InvoiceVat, DocumentType.InvoiceAdvance, DocumentType.InvoiceFinal);
        assertThat(OrderPageModelFactory.manualDocumentTypes(consumer))
                .containsExactly(DocumentType.Receipt, DocumentType.InvoicePersonal);
    }

    private static Order b2b(Order order) {
        BillingDetails billing = new BillingDetails();
        billing.setTaxId("1234567890");
        order.setBillingDetails(billing);
        return order;
    }

    @Test
    void aTrackingLinkWithAScriptSchemeIsShownAsText() {
        // when / then
        assertThat(OrderPageModelFactory.safeWebUrl("javascript:alert(1)")).isNull();
        assertThat(OrderPageModelFactory.safeWebUrl(" JAVASCRIPT:alert(1)")).isNull();
        assertThat(OrderPageModelFactory.safeWebUrl("data:text/html,<script>alert(1)</script>")).isNull();
        assertThat(OrderPageModelFactory.safeWebUrl("  ")).isNull();
        assertThat(OrderPageModelFactory.safeWebUrl(" https://x ")).isEqualTo("https://x");
        assertThat(OrderPageModelFactory.safeWebUrl("HTTP://tracking.example/T-1")).isEqualTo("HTTP://tracking.example/T-1");
    }

    @Test
    void theShipmentRowCarriesOnlyAWebTrackingLink() {
        // given
        Order order = order(OrderStatus.Shipping);
        order.getShipments().get(0).setTrackingNo("T-1");
        order.getShipments().get(0).setTrackingUrl("javascript:alert(1)");

        // when
        OrderPageModel page = factory.build(order, List.of(), new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(page.shipments().rows().get(0).trackingUrl()).isNull();
        assertThat(page.shipments().rows().get(0).trackingNo()).isEqualTo("T-1");
    }

    private static final OrderPageModelFactory.Viewer ADMIN = new OrderPageModelFactory.Viewer(false, true, null);

    private static Payment refund(double amount) {
        return new Payment("ZW/1", "Zwrot", PaymentSource.BankTransfer, pl.commercelink.orders.PaymentDirection.Outgoing,
                amount, 0, null, null);
    }

    @Test
    void aRefundRowShowsTheAmountThePaidTotalCounts() {
        // given: a refund saved positive by older code counts as money that came in, so its row says so
        Order typedNegative = order(OrderStatus.New);
        typedNegative.addPayment(refund(-100));
        Order storedPositive = order(OrderStatus.New);
        storedPositive.addPayment(refund(100));

        // when
        OrderPageModel.PaymentsCard negative = factory.build(typedNegative, List.of(), ADMIN, PL).payments();
        OrderPageModel.PaymentsCard positive = factory.build(storedPositive, List.of(), ADMIN, PL).payments();

        // then
        assertThat(negative.rows().get(0).amount()).isEqualTo("−100,00");
        assertThat(negative.paid()).isEqualTo("−100,00");
        assertThat(positive.rows().get(0).amount()).isEqualTo("100,00");
        assertThat(positive.paid()).isEqualTo("100,00");
        assertThat(negative.rows().get(0).refund()).isTrue();
        assertThat(positive.rows().get(0).refund()).isTrue();
    }

    @Test
    void aRefundWithAFeeShowsWhatPaidCountsAndTheFeeInItsDescription() {
        // given: a refund of 50 with a fee of 2 counts as -52 (Payment#getAppliedAmount); the dialog shows 50 and 2
        Order order = order(OrderStatus.New);
        order.addPayment(new Payment("ZW/1", "Zwrot", PaymentSource.BankTransfer,
                pl.commercelink.orders.PaymentDirection.Outgoing, -50, 2, null, null));

        // when
        OrderPageModel.PaymentsCard card = factory.build(order, List.of(), ADMIN, PL).payments();

        // then
        assertThat(card.rows().get(0).amount()).isEqualTo("−52,00");
        assertThat(card.rows().get(0).fee()).isEqualTo("2,00");
        assertThat(card.paid()).isEqualTo("−52,00");
        assertThat(card.forms().get(0).amount()).isEqualTo("50.00");
        assertThat(card.forms().get(0).fee()).isEqualTo("2.00");
    }

    @Test
    void aPlaceholderPaymentReadsAsExpected() {
        // given
        Order order = order(OrderStatus.New);
        order.addPayment(new Payment(PaymentSource.BankTransfer));
        order.addPayment(Payment.bankTransfer("R/1", "Jan", 10));

        // when
        List<OrderPageModel.PaymentRow> rows = factory.build(order, List.of(), ADMIN, PL).payments().rows();

        // then
        assertThat(rows).extracting(OrderPageModel.PaymentRow::pending).containsExactly(true, false);
    }

    @Test
    void anOverpaidOrderShowsTheOverpaymentNotANegativeDue() {
        // given
        Order overpaid = order(OrderStatus.New);
        overpaid.setTotalPrice(100);
        overpaid.addPayment(Payment.bankTransfer("R/1", "Jan", 101));
        Order unpaid = order(OrderStatus.New);
        unpaid.setTotalPrice(100);

        // when
        OrderPageModel.PaymentsCard over = factory.build(overpaid, List.of(), ADMIN, PL).payments();
        OrderPageModel.PaymentsCard due = factory.build(unpaid, List.of(), ADMIN, PL).payments();

        // then
        assertThat(over.overpaid()).isTrue();
        assertThat(over.overpaidAmount()).isEqualTo("1,00");
        assertThat(over.unpaid()).isEqualTo("0,00");
        assertThat(due.overpaid()).isFalse();
        assertThat(due.overpaidAmount()).isNull();
        assertThat(due.unpaid()).isEqualTo("100,00");
    }

    @Test
    void theEmptyShipmentsTextSaysWhatCanBeDoneNow() {
        // given
        Order warehouse = order(OrderStatus.New);
        warehouse.setShipments(new java.util.ArrayList<>());
        Order dropship = order(OrderStatus.New);
        dropship.setShipments(new java.util.ArrayList<>());
        dropship.setFulfilmentType(FulfilmentType.DirectToConsumer);
        Order completed = order(OrderStatus.Completed);
        completed.setShipments(new java.util.ArrayList<>());

        // when / then
        assertThat(factory.build(warehouse, List.of(), ADMIN, PL).shipments().emptyKey()).isEqualTo("order.shipments.empty");
        assertThat(factory.build(dropship, List.of(), ADMIN, PL).shipments().emptyKey()).isEqualTo("order.shipments.empty.dropship");
        assertThat(factory.build(completed, List.of(), ADMIN, PL).shipments().emptyKey()).isEqualTo("order.shipments.empty.readonly");
        assertThat(factory.build(warehouse, List.of(), new OrderPageModelFactory.Viewer(true, false, null), PL).shipments().emptyKey())
                .isEqualTo("order.shipments.empty.readonly");
    }

    @Test
    void aFailedTrackingSubscriptionCarriesItsToneAndExplanation() {
        // given
        Order order = order(OrderStatus.Shipping);
        order.getShipments().get(0).setTrackingSubscriptionStatus(pl.commercelink.orders.ShipmentTrackingStatus.FAILED);
        Shipment active = new Shipment(ShipmentType.Courier);
        active.setTrackingSubscriptionStatus(pl.commercelink.orders.ShipmentTrackingStatus.ACTIVE);
        order.addShipment(active);

        // when
        List<OrderPageModel.ShipmentRow> rows = factory.build(order, List.of(), ADMIN, PL).shipments().rows();

        // then
        assertThat(rows.get(0).trackingTone()).isEqualTo("is-bad");
        assertThat(rows.get(0).trackingHelpKey()).isEqualTo("order.shipment.tracking.failed.help");
        assertThat(rows.get(1).trackingTone()).isEqualTo("is-info");
        assertThat(rows.get(1).trackingHelpKey()).isNull();
    }

    @Test
    void aFailedTrackingSubscriptionOnAReadOnlyPageKeepsItsToneWithoutTheEditHelp() {
        // given
        Order order = order(OrderStatus.Completed);
        order.getShipments().get(0).setTrackingSubscriptionStatus(pl.commercelink.orders.ShipmentTrackingStatus.FAILED);
        Order open = order(OrderStatus.Shipping);
        open.getShipments().get(0).setTrackingSubscriptionStatus(pl.commercelink.orders.ShipmentTrackingStatus.FAILED);

        // when
        OrderPageModel.ShipmentRow closed = factory.build(order, List.of(), ADMIN, PL).shipments().rows().get(0);
        OrderPageModel.ShipmentRow superAdmin = factory.build(open, List.of(),
                new OrderPageModelFactory.Viewer(true, false, null), PL).shipments().rows().get(0);

        // then
        assertThat(closed.trackingTone()).isEqualTo("is-bad");
        assertThat(closed.trackingHelpKey()).isNull();
        assertThat(superAdmin.trackingHelpKey()).isNull();
    }

    @Test
    void theHeaderNamesTheClientLikeTheList() {
        // given
        Order business = order(OrderStatus.New);
        BillingDetails company = new BillingDetails();
        company.setName("Jan");
        company.setSurname("Kowalski");
        company.setCompanyName("Firma Sp. z o.o.");
        business.setBillingDetails(company);
        Order person = order(OrderStatus.New);
        BillingDetails jan = new BillingDetails();
        jan.setName("Jan");
        jan.setSurname("Kowalski");
        jan.setEmail("jan@example.pl");
        person.setBillingDetails(jan);
        Order shipped = order(OrderStatus.New);
        shipped.setBillingDetails(jan);
        pl.commercelink.orders.ShippingDetails shipping = new pl.commercelink.orders.ShippingDetails();
        shipping.setName("Anna");
        shipping.setSurname("Nowak");
        shipped.setShippingDetails(shipping);
        Order emailOnly = order(OrderStatus.New);
        BillingDetails email = new BillingDetails();
        email.setEmail("anon@example.pl");
        emailOnly.setBillingDetails(email);

        // when / then
        assertThat(factory.build(business, List.of(), ADMIN, PL).header().clientName()).isEqualTo("Firma Sp. z o.o.");
        assertThat(factory.build(person, List.of(), ADMIN, PL).header().clientName()).isEqualTo("Jan Kowalski");
        assertThat(factory.build(shipped, List.of(), ADMIN, PL).header().clientName()).isEqualTo("Anna Nowak");
        assertThat(factory.build(emailOnly, List.of(), ADMIN, PL).header().clientName()).isEqualTo("anon@example.pl");
        assertThat(MoveTargetView.of(business, 1, "Nowe", "1,00 PLN", null).clientName()).isEqualTo("Firma Sp. z o.o.");
    }

    @Test
    void aMarketplaceSourceShowsItsNameOnly() {
        // given
        Order named = order(OrderStatus.New);
        named.setSource(new pl.commercelink.orders.OrderSource("Allegro", pl.commercelink.orders.OrderSourceType.Marketplace));
        Order unnamed = order(OrderStatus.New);
        unnamed.setSource(new pl.commercelink.orders.OrderSource(null, pl.commercelink.orders.OrderSourceType.WebStore));

        // when
        OrderPageModel.Header withName = factory.build(named, List.of(), ADMIN, PL).header();
        OrderPageModel.Header withoutName = factory.build(unnamed, List.of(), ADMIN, PL).header();

        // then
        assertThat(withName.sourceName()).isEqualTo("Allegro");
        assertThat(withName.sourceTypeKey()).isNull();
        assertThat(withoutName.sourceName()).isNull();
        assertThat(withoutName.sourceTypeKey()).isEqualTo("order.source.type.WebStore");
    }

    @Test
    void theEmptyDocumentsTextPointsToWhereTheNextDocumentIsMade() {
        // when
        OrderPageModel.DocumentsCard consumer = factory.build(order(OrderStatus.New), List.of(), ADMIN, PL).documents();
        OrderPageModel.DocumentsCard business = factory.build(b2b(order(OrderStatus.New)), List.of(), ADMIN, PL).documents();

        // then
        assertThat(consumer.emptyKey()).isEqualTo("order.documents.empty.next.manual");
        assertThat(business.emptyKey()).isEqualTo("order.documents.empty.next");
    }

    @Test
    void theSelectionRowHasOneMoveMenuWithTheWarehouseEntriesAndNoAllocation() {
        // given
        Order order = order(OrderStatus.New);
        order.addPayment(Payment.bankTransfer("R/1", "Jan", 10));
        OrderItem item = item(FulfilmentStatus.New);
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of(item.getItemId()));

        // when
        OrderPageModel.ItemsCard items = factory.build(order, List.of(item), ADMIN, PL).items();

        // then
        assertThat(items.bulkActions()).extracting(OrderPageModel.BulkActionButton::action)
                .doesNotContain(BulkAction.ALLOCATE);
        assertThat(items.bulkMenus()).extracting(OrderPageModel.BulkMenu::menu).containsExactly(BulkAction.Menu.MOVE);
        assertThat(items.bulkMenus().get(0).actions()).extracting(OrderPageModel.BulkActionButton::action)
                .containsExactly(BulkAction.SPLIT, BulkAction.MOVE, BulkAction.TO_WAREHOUSE, BulkAction.TO_WAREHOUSE_RMA);
        assertThat(items.bulkMenus().get(0).actions()).extracting(OrderPageModel.BulkActionButton::reasonKey)
                .containsExactly("order.bulk.unavailable.split", "order.bulk.unavailable.split",
                        "order.items.action.dropship.locked", "order.items.action.dropship.locked");
        assertThat(items.bulkStandalone().action()).isEqualTo(BulkAction.REMOVE);
        assertThat(items.bulkStandalone().reasonKey()).isEqualTo("order.items.action.dropship.locked");
        assertThat(items.bulkStandalone().shortReasonKey()).isEqualTo("order.items.action.dropship.locked.short");
    }

    @Test
    void theItemMenuOffersAllocationWithTheReasonWhenItWouldChangeNothing() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem ready = item(FulfilmentStatus.New);
        ready.setEan("5900000000001");
        ready.setManufacturerCode("MFN-1");
        ready.setDeliveryId("Acme");
        OrderItem noSupplier = item(FulfilmentStatus.New);
        noSupplier.setEan("5900000000001");
        noSupplier.setManufacturerCode("MFN-1");
        noSupplier.setDeliveryId(null);
        OrderItem noCodes = item(FulfilmentStatus.New);
        noCodes.setEan(null);
        noCodes.setDeliveryId("Acme");
        OrderItem ordered = item(FulfilmentStatus.Ordered);

        // when
        OrderPageModel.ItemsCard items = factory.build(order, List.of(ready, noSupplier, noCodes, ordered), ADMIN, PL).items();

        // then
        assertThat(items.products()).extracting(row -> allocate(row).reasonKey())
                .containsExactly(null, "order.item.unavailable.allocation.supplier",
                        "order.item.unavailable.allocation.codes", "order.item.unavailable.not.new");
        assertThat(allocate(items.products().get(0)).available()).isTrue();
        assertThat(allocate(items.products().get(0)).labelKey()).isEqualTo("order.item.menu.allocate");
    }

    @Test
    void theItemMenuGreysAllocationWhileTheOrderHasDropshipItems() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem ready = item(FulfilmentStatus.New);
        ready.setEan("5900000000001");
        ready.setManufacturerCode("MFN-1");
        ready.setDeliveryId("Acme");
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of("other-item"));

        // when
        OrderItemRow row = factory.build(order, List.of(ready), ADMIN, PL).items().products().get(0);

        // then
        assertThat(allocate(row).available()).isFalse();
        assertThat(allocate(row).reasonKey()).isEqualTo("order.item.unavailable.dropship");
    }

    private static ItemAction.State allocate(OrderItemRow row) {
        return row.actions().stream().filter(state -> state.action() == ItemAction.ALLOCATE).findFirst().orElseThrow();
    }

    @Test
    void everyReasonRemoveCanShowHasItsShortWordingInBothBundles() throws Exception {
        // given: every reason the factory can give REMOVE (and, as each reason carries both keys, any other action)
        java.util.Set<BulkReason> reasons = new java.util.LinkedHashSet<>();
        for (boolean canSplit : List.of(true, false)) {
            for (boolean dropship : List.of(true, false)) {
                BulkReason reason = OrderPageModelFactory.bulkReason(BulkAction.REMOVE, canSplit, dropship);
                if (reason != null) {
                    reasons.add(reason);
                }
            }
        }
        reasons.addAll(List.of(BulkReason.values()));
        List<String> keys = new java.util.ArrayList<>();
        reasons.forEach(r -> keys.addAll(List.of(r.key(), r.shortKey())));
        keys.add(BulkAction.REMOVE.skippedKey());
        keys.add(BulkAction.REMOVE.shortSkippedKey());

        // when
        java.util.Properties pl = bundle("messages_pl.properties");
        java.util.Properties en = bundle("messages_en.properties");

        // then: "Remove" prints its reason from these keys; a missing one would render as ??key?? with every test green
        assertThat(reasons).contains(BulkReason.DROPSHIP_LOCKED);
        assertThat(keys).doesNotContainNull().allSatisfy(key -> {
            assertThat(pl.getProperty(key)).as("pl:" + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en:" + key).isNotBlank();
        });
    }

    private static java.util.Properties bundle(String name) throws Exception {
        java.util.Properties properties = new java.util.Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("src/main/resources", name),
                java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void anInvoicedOrderHasNoStandaloneRemoveInTheSelectionRow() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.addDocument(new Document("fv", "FV/2026/09/1", null, DocumentType.InvoiceVat));

        // when
        OrderPageModel.ItemsCard items = factory.build(order, List.of(item(FulfilmentStatus.New)), ADMIN, PL).items();

        // then
        assertThat(items.bulkStandalone()).isNull();
        assertThat(items.bulkMenus()).hasSize(1);
    }

    @Test
    void aClosedOrderWithoutDocumentsDoesNotNameANextDocument() {
        // when
        OrderPageModel.DocumentsCard cancelled = factory.build(order(OrderStatus.Cancelled), List.of(), ADMIN, PL).documents();
        OrderPageModel.DocumentsCard completed = factory.build(b2b(order(OrderStatus.Completed)), List.of(), ADMIN, PL).documents();

        // then
        assertThat(cancelled.emptyKey()).isEqualTo("order.documents.empty");
        assertThat(completed.emptyKey()).isEqualTo("order.documents.empty");
    }

    @Test
    void aReadOnlyViewerIsNotToldWhichDocumentToIssueNext() {
        // when
        OrderPageModel.DocumentsCard superAdmin = factory.build(b2b(order(OrderStatus.New)), List.of(),
                new OrderPageModelFactory.Viewer(true, false, null), PL).documents();

        // then
        assertThat(superAdmin.emptyKey()).isEqualTo("order.documents.empty");
    }

    @Test
    void aDocumentLinkWithAScriptSchemeIsShownAsText() {
        // given
        Order order = order(OrderStatus.New);
        order.addDocument(new Document("r1", "PAR/1", "javascript:alert(1)", DocumentType.Receipt));
        order.addDocument(new Document("r2", "PAR/2", "https://receipts.example/2", DocumentType.Receipt));

        // when
        List<OrderPageModel.DocumentRow> rows = factory.build(order, List.of(), ADMIN, PL).documents().rows();

        // then
        assertThat(rows.get(0).href()).isNull();
        assertThat(rows.get(0).number()).isEqualTo("PAR/1");
        assertThat(rows.get(1).href()).isEqualTo("https://receipts.example/2");
    }

    // --- e-receipt in the documents card and the locks while it is being issued -------------------------------------

    private static final String KEY_1 = "o:R1";
    private static final String KEY_2 = "o:R2";

    private static ReceiptAttempt attempt(String key, int attemptNo, ReceiptAttemptState state) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey(key);
        attempt.setAttemptNo(attemptNo);
        attempt.setState(state);
        return attempt;
    }

    private static final ReceiptPageProblem WAITING = new ReceiptPageProblem("Czeka ponad 48 h",
            "Sprawdź, czy drukarka fiskalna działa.", null, null);

    /** A view row as ReceiptOrderView.of would give it; the flags are what the attempt allows at the moment. */
    private static ReceiptOrderView.Row row(String key, int attemptNo, ReceiptAttemptState state, String url,
                                            String number, java.time.Instant fiscalisedAt, ReceiptPageProblem problem,
                                            boolean canCheck, boolean canClose, boolean canResendEmail, String outcome) {
        return new ReceiptOrderView.Row(key, state, "receipts.state." + state.name(), "is-info", url, problem, null,
                null, canCheck, canClose, canResendEmail, attemptNo, number, fiscalisedAt, outcome, false);
    }

    private void receipts(ReceiptOrderState state) {
        when(receiptAttemptService.orderState(any(), any(), any(), any())).thenReturn(state);
    }

    private static ReceiptOrderState issuing() {
        return new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.ISSUING)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.ISSUING, null, null, null, null,
                        true, true, false, null)), false), false, true);
    }

    @Test
    void aFiscalisedEReceiptIsOneRowAndItsOrderDocumentIsNotListedAgain() {
        // given: the attempt attached its document (id = its key); a receipt typed in by hand stays a document row
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new Document(KEY_1, "PAR/7/2026", "https://paragony.example/7", DocumentType.Receipt,
                java.time.LocalDate.of(2026, 9, 28)));
        order.addDocument(new Document("typed", "PAR/R/1", null, DocumentType.Receipt));
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, "https://paragony.example/7",
                        "PAR/7/2026", java.time.Instant.parse("2026-09-27T23:30:00Z"), null, false, false, false, null)),
                        false), false, true));

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order, List.of(), ADMIN, PL).documents();

        // then: the fiscal date is the Polish date (23:30 UTC on the 27th is the 28th in Warsaw)
        assertThat(documents.rows()).extracting(OrderPageModel.DocumentRow::number).containsExactly("PAR/R/1");
        OrderPageModel.ReceiptRow receipt = documents.receipt();
        assertThat(receipt.number()).isEqualTo("PAR/7/2026");
        assertThat(receipt.href()).isEqualTo("https://paragony.example/7");
        assertThat(receipt.statusKey()).isEqualTo("receipts.state.FISCALISED");
        assertThat(receipt.dateKey()).isEqualTo("receipts.row.fiscalised");
        assertThat(receipt.date()).isEqualTo(OrderFormats.date(java.time.LocalDate.of(2026, 9, 28)));
        assertThat(receipt.hasActions()).isFalse();
        assertThat(receipt.earlier()).isEmpty();
        assertThat(documents.isEmpty()).isFalse();
    }

    @Test
    void aDocumentUrlThatIsNotAWebAddressIsNotLinked() {
        // given
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, "javascript:alert(1)",
                        "PAR/1", null, null, false, false, false, null)), false), false, true));

        // when
        OrderPageModel.ReceiptRow receipt = factory.build(order(OrderStatus.Delivered), List.of(), ADMIN, PL)
                .documents().receipt();

        // then
        assertThat(receipt.href()).isNull();
        assertThat(receipt.number()).isEqualTo("PAR/1");
    }

    @Test
    void earlierAttemptsCollapseUnderTheNewestRowWithTheirOutcomeAndNoActions() {
        // given: R1 blocked and superseded, R2 waiting for the printer
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.BLOCKED),
                attempt(KEY_2, 2, ReceiptAttemptState.PENDING)),
                new ReceiptOrderView(List.of(
                        row(KEY_2, 2, ReceiptAttemptState.PENDING, null, null, null, WAITING, true, true, false, null),
                        row(KEY_1, 1, ReceiptAttemptState.BLOCKED, null, null, null, null, false, false, false, "brak pozycji")),
                        false), false, true));

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order(OrderStatus.Shipping), List.of(), ADMIN, PL).documents();

        // then
        OrderPageModel.ReceiptRow receipt = documents.receipt();
        assertThat(receipt.attemptNo()).isEqualTo(2);
        // the page problem travels unchanged from the view row (cause, action, provider details)
        assertThat(receipt.problem()).isSameAs(WAITING);
        assertThat(receipt.earlier()).containsExactly(new OrderPageModel.ReceiptEarlierRow(1,
                "receipts.state.BLOCKED", "is-info", "brak pozycji"));
        assertThat(receipt.canCheck()).isTrue();
        assertThat(receipt.canClose()).isTrue();
        assertThat(receipt.canReissue()).isFalse();
        assertThat(receipt.closeDialogId()).isEqualTo("receipt-close-2");
        assertThat(receipt.closeHref()).isEqualTo("/dashboard/orders/" + documents.closeForms().get(0).orderId()
                + "/receipts/close?receiptKey=o%3AR2");
        assertThat(documents.closeForms()).extracting(ReceiptCloseForm::dialogId).containsExactly("receipt-close-2");
    }

    @Test
    void aSuperAdminSeesTheEReceiptWithoutAnyAction() {
        // given
        receipts(issuing());

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order(OrderStatus.Shipping), List.of(),
                new OrderPageModelFactory.Viewer(true, false, null), PL).documents();

        // then
        assertThat(documents.receipt()).isNotNull();
        assertThat(documents.receipt().hasActions()).isFalse();
        assertThat(documents.receipt().closeHref()).isNull();
        assertThat(documents.closeForms()).isEmpty();
        assertThat(documents.canIssueReceipt()).isFalse();
    }

    @Test
    void aClosedOrderKeepsTheReceiptActionsThatChangeTheReceiptButNeverReissues() {
        // given: a completed order whose e-receipt e-mail failed; a cancelled one whose attempt is still issuing
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, "https://p.example/1",
                        "PAR/1", null, new ReceiptPageProblem("Mail nie wyszedł", null, null, null), false, false, true, null)), true), false, true));

        // when
        OrderPageModel.ReceiptRow completed = factory.build(order(OrderStatus.Completed), List.of(), ADMIN, PL)
                .documents().receipt();

        // then
        assertThat(completed.canResendEmail()).isTrue();
        assertThat(completed.canReissue()).isFalse();
    }

    @Test
    void wystawPonownieIsOfferedOnTheNewestRowOnceEveryAttemptIsDead() {
        // given
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FAILED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FAILED, null, null, null,
                        new ReceiptPageProblem("Odrzucony: VAT", null, null, null), false, false, false, "VAT")), true), false, false));

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order(OrderStatus.Shipping), List.of(), ADMIN, PL).documents();

        // then: a dead attempt locks nothing, so a receipt may be typed in again as well
        assertThat(documents.receipt().canReissue()).isTrue();
        assertThat(documents.receipt().earlier()).isEmpty();
        assertThat(documents.manualTypes()).extracting(o -> o.value()).contains(DocumentType.Receipt);
        assertThat(documents.canAdd()).isTrue();
    }

    @Test
    void eParagonIsInTheIssueMenuWhenTheOrderCanGetOne() {
        // given
        receipts(new ReceiptOrderState(List.of(), new ReceiptOrderView(List.of(), false), true, false));

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order(OrderStatus.New), List.of(), ADMIN, PL).documents();
        OrderPageModel.DocumentsCard superAdmin = factory.build(order(OrderStatus.New), List.of(),
                new OrderPageModelFactory.Viewer(true, false, null), PL).documents();

        // then: a consumer order issues nothing else from the menu, which now exists for the e-receipt alone
        assertThat(documents.canIssueReceipt()).isTrue();
        assertThat(documents.canIssue()).isTrue();
        assertThat(documents.issuable()).isEmpty();
        assertThat(documents.receipt()).isNull();
        assertThat(documents.emptyKey()).isEqualTo("order.documents.empty.next.receipt");
        assertThat(superAdmin.canIssueReceipt()).isFalse();
        assertThat(superAdmin.canIssue()).isFalse();
    }

    @Test
    void whileAnAttemptOwnsTheReceiptNoReceiptIsTypedInAndNothingIsInvoiced() {
        // given: a business order (issuable types) whose receipt attempt was fiscalised before the document came
        Order order = b2b(order(OrderStatus.Shipping));
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, null, "PAR/1", null, null,
                        true, false, false, null)), false), false, true));

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order, List.of(), ADMIN, PL).documents();

        // then: a second sale document is offered neither from the invoicing system nor by hand (worded as being
        // attached: the receipt is fiscalised, only its document is on its way)
        assertThat(documents.issuable()).isEmpty();
        assertThat(documents.canIssue()).isFalse();
        assertThat(documents.canAdd()).isFalse();
        assertThat(documents.addLockedKey()).isEqualTo("order.documents.add.locked.receiptAttaching");
    }

    @Test
    void manualTypesLeaveReceiptOutWhileAnAttemptOwnsIt() {
        // given
        Order consumer = order(OrderStatus.Shipping);

        // when / then
        assertThat(OrderPageModelFactory.manualDocumentTypes(consumer, true))
                .containsExactly(DocumentType.InvoicePersonal);
        assertThat(OrderPageModelFactory.manualDocumentTypes(consumer, false))
                .containsExactly(DocumentType.Receipt, DocumentType.InvoicePersonal);
    }

    @Test
    void anEReceiptBeingIssuedLocksTheEditsAnIssuedInvoiceLocksWithItsOwnReason() {
        // given
        receipts(issuing());
        OrderItem item = item(FulfilmentStatus.New);

        // when
        OrderPageModel page = factory.build(order(OrderStatus.New), List.of(item), ADMIN, PL);
        OrderPageModel empty = factory.build(order(OrderStatus.New), List.of(), ADMIN, PL);

        // then: adding items
        assertThat(page.items().canAddItems()).isFalse();
        assertThat(page.items().addItemsReasonKey()).isEqualTo("order.items.add.locked.receipt");
        // removing, splitting off and moving items (greyed with the reason); routing stays
        assertThat(page.items().bulkStandalone().action()).isEqualTo(BulkAction.REMOVE);
        assertThat(page.items().bulkStandalone().reasonKey()).isEqualTo("order.bulk.unavailable.receipt");
        assertThat(page.items().bulkStandalone().shortReasonKey()).isEqualTo("order.bulk.unavailable.receipt.short");
        assertThat(page.items().bulkMenus().get(0).actions())
                .filteredOn(b -> b.action() == BulkAction.SPLIT || b.action() == BulkAction.MOVE)
                .extracting(OrderPageModel.BulkActionButton::reasonKey).containsOnly("order.bulk.unavailable.receipt");
        assertThat(page.items().bulkMenus().get(0).actions())
                .filteredOn(b -> b.action() == BulkAction.TO_WAREHOUSE || b.action() == BulkAction.TO_WAREHOUSE_RMA)
                .hasSize(2).allMatch(OrderPageModel.BulkActionButton::available);
        // merge on invoice
        assertThat(page.items().products().get(0).actions()).filteredOn(a -> a.action() == ItemAction.CONSOLIDATE)
                .singleElement().satisfies(a -> {
                    assertThat(a.available()).isFalse();
                    assertThat(a.reasonKey()).isEqualTo("order.item.unavailable.receipt");
                });
        // billing details
        assertThat(page.customer().billingLockedKey()).isEqualTo("order.customer.billing.locked.receipt");
        assertThat(page.customer().billingEditHref()).isNull();
        assertThat(page.customer().shippingEditHref()).isNotNull();
        // documents typed in by hand
        assertThat(page.documents().canAdd()).isFalse();
        assertThat(page.documents().addLockedKey()).isEqualTo("order.documents.add.locked.receipt");
        // deleting the (item-less) order
        assertThat(empty.header().canDelete()).isFalse();
    }

    @Test
    void onceTheReceiptDocumentIsOnTheOrderTheInvoicedLocksApplyByThemselves() {
        // given: fiscalised and attached — Receipt is a closing document, so the order is invoiced
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new Document(KEY_1, "PAR/1", null, DocumentType.Receipt));
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, null, "PAR/1", null, null,
                        false, false, false, null)), false), false, true));

        // when
        OrderPageModel page = factory.build(order, List.of(item(FulfilmentStatus.Delivered)), ADMIN, PL);

        // then
        assertThat(order.isInvoiced()).isTrue();
        assertThat(page.items().addItemsReasonKey()).isEqualTo("order.items.add.locked.invoiced");
        assertThat(page.items().bulkStandalone()).isNull();
        assertThat(page.customer().billingLockedKey()).isEqualTo("order.customer.billing.locked");
        assertThat(page.documents().canAdd()).isFalse();
        assertThat(page.documents().addLockedKey()).isNull();
    }

    @Test
    void theLockKeysOfTheControllerAgreeWithThePage() {
        // given
        Order order = order(OrderStatus.New);

        // when / then
        assertThat(OrderPageModelFactory.addItemsLockedKey(order, false, true)).isEqualTo("order.items.add.locked.receipt");
        assertThat(OrderPageModelFactory.addItemsLockedKey(order, false, false)).isNull();
        assertThat(OrderPageModelFactory.addDocumentLockedKey(order, DocumentType.InvoicePersonal, true))
                .isEqualTo("order.documents.add.locked.receipt");
        assertThat(OrderPageModelFactory.addDocumentLockedKey(order, DocumentType.InvoicePersonal, false)).isNull();
        assertThat(OrderPageModelFactory.bulkReason(BulkAction.ALLOCATE, true, false, true)).isNull();
        assertThat(OrderPageModelFactory.bulkReason(BulkAction.SPLIT, true, false, true)).isEqualTo(BulkReason.RECEIPT_ISSUING);
        assertThat(CustomerView.lockedKey(order, false, true)).isNull();
    }

    // --- cancelling against the e-receipt: waits for the outcome, warns once it is fiscalised ------------------------

    /** A delivered order whose only product came back: the order could be cancelled (Order#canBeCancelled). */
    private static List<OrderItem> returnedItems() {
        return List.of(item(FulfilmentStatus.Returned));
    }

    @Test
    void cancellingWaitsForTheEReceiptBeingIssuedWithItsOwnReason() {
        // given
        receipts(issuing());

        // when
        OrderPageModel.Header header = factory.build(order(OrderStatus.Delivered), returnedItems(), ADMIN, PL).header();

        // then
        assertThat(header.canCancel()).isFalse();
        assertThat(header.cancelLockedKey()).isEqualTo("order.page.cancel.locked.receipt");
    }

    @Test
    void cancelReasonOfAnOpenOrderPointsToRemoval() {
        // given: a new order without items can be deleted
        Order fresh = order(OrderStatus.New);

        // when
        OrderPageModel.Header header = factory.build(fresh, List.of(), ADMIN, PL).header();

        // then: the reason names the menu entry that is actually offered, by its label
        ResourceBundle bundle = ResourceBundle.getBundle("messages", PL);
        assertThat(header.canDelete()).isTrue();
        assertThat(header.cancelUnavailableKey()).isEqualTo("order.page.cancel.unavailable.delete");
        assertThat(bundle.getString(header.cancelUnavailableKey())).contains("„" + bundle.getString("order.page.delete") + "”");
    }

    @Test
    void cancelReasonOfAnOpenOrderThatCannotBeDeletedStaysNeutral() {
        // when: an order in assembly has items, so "Usuń zamówienie" is greyed too
        OrderPageModel.Header header = factory.build(order(OrderStatus.Assembly), List.of(item(FulfilmentStatus.New)),
                ADMIN, PL).header();

        // then: no advice to delete what cannot be deleted
        assertThat(header.canDelete()).isFalse();
        assertThat(header.cancelUnavailableKey()).isEqualTo("order.page.cancel.unavailable.open");
        assertThat(ResourceBundle.getBundle("messages", PL).getString(header.cancelUnavailableKey()))
                .isEqualTo("Anulować można dostarczone zamówienie, po zwrocie wszystkich produktów i wpłat.");
    }

    @Test
    void cancelReasonOfADeliveredOrderNamesTheMissingCondition() {
        // given
        Order paid = order(OrderStatus.Delivered);
        paid.addPayment(new Payment("REF-1", "Jan", PaymentSource.BankTransfer, 100, 0));
        Order unpaid = order(OrderStatus.Delivered);

        // when
        String itemsAndPayments = factory.build(paid, List.of(item(FulfilmentStatus.Delivered)), ADMIN, PL).header().cancelUnavailableKey();
        String payments = factory.build(paid, returnedItems(), ADMIN, PL).header().cancelUnavailableKey();
        String items = factory.build(unpaid, List.of(item(FulfilmentStatus.Delivered)), ADMIN, PL).header().cancelUnavailableKey();
        OrderPageModel.Header cancellable = factory.build(order(OrderStatus.Delivered), returnedItems(), ADMIN, PL).header();

        // then
        assertThat(itemsAndPayments).isEqualTo("order.page.cancel.unavailable.itemsAndPayments");
        assertThat(payments).isEqualTo("order.page.cancel.unavailable.payments");
        assertThat(items).isEqualTo("order.page.cancel.unavailable.items");
        assertThat(cancellable.canCancel()).isTrue();
        ResourceBundle bundle = ResourceBundle.getBundle("messages", PL);
        assertThat(bundle.getString(payments)).contains("wpłat").doesNotContain("produktów");
        assertThat(bundle.getString(items)).contains("produktów").doesNotContain("wpłat");
    }

    @Test
    void anOrderThatCannotBeCancelledAnywayKeepsTheGeneralReasonWhileIssuing() {
        // given
        receipts(issuing());

        // when
        OrderPageModel.Header header = factory.build(order(OrderStatus.Shipping), List.of(item(FulfilmentStatus.Delivered)),
                ADMIN, PL).header();

        // then
        assertThat(header.canCancel()).isFalse();
        assertThat(header.cancelLockedKey()).isNull();
    }

    @Test
    void theCancelConfirmationWarnsOnlyOnceTheEReceiptIsFiscalised() {
        // given: fiscalised and attached, so the order is invoiced and nothing waits any more
        Order fiscalised = order(OrderStatus.Delivered);
        fiscalised.addDocument(new Document(KEY_1, "PAR/1", null, DocumentType.Receipt));
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, null, "PAR/1", null, null,
                        false, false, false, null)), false), false, true));

        // when
        OrderPageModel.Header withReceipt = factory.build(fiscalised, returnedItems(), ADMIN, PL).header();
        receipts(ReceiptOrderState.NONE);
        OrderPageModel.Header without = factory.build(order(OrderStatus.Delivered), returnedItems(), ADMIN, PL).header();

        // then
        assertThat(withReceipt.canCancel()).isTrue();
        assertThat(withReceipt.cancelLockedKey()).isNull();
        assertThat(withReceipt.cancelMessage()).isEqualTo(
                "Zamówienie przejdzie w status Anulowane, a ceny usług zostaną wyzerowane. Zamówienie ma zafiskalizowany"
                        + " e-paragon — anulowanie go nie cofa. Pieniądze rozlicz osobno: fakturą korygującą albo zwrotem.");
        assertThat(without.canCancel()).isTrue();
        assertThat(without.cancelMessage()).isEqualTo("Zamówienie przejdzie w status Anulowane, a ceny usług zostaną wyzerowane.");
    }

    @Test
    void aManuallyClosedEReceiptCountsAsFiscalisedForTheWarning() {
        // given
        Order order = order(OrderStatus.Delivered);

        // when / then
        assertThat(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.CLOSED_MANUALLY)),
                new ReceiptOrderView(List.of(), false), false, true).hasFiscalisedReceipt(order)).isTrue();
        assertThat(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FAILED)),
                new ReceiptOrderView(List.of(), true), false, false).hasFiscalisedReceipt(order)).isFalse();
        assertThat(ReceiptOrderState.NONE.hasFiscalisedReceipt(order)).isFalse();
    }

    @Test
    void aSettledAttemptSaysNothingIsNeededWithWhyItStopped() {
        // given: the POS sale's attempt blocked, the cash register's receipt typed in since
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new Document("typed", "PAR/KASA/1", null, DocumentType.Receipt));
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.BLOCKED)),
                new ReceiptOrderView(List.of(new ReceiptOrderView.Row(KEY_1, ReceiptAttemptState.BLOCKED,
                        "receipts.state.BLOCKED", "is-neutral", null, null, null, null, false, false, false, 1, null, null,
                        "sprzedaż z kasy (POS) bez e-maila klienta", true)), false), false, false));

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order, List.of(), ADMIN, PL).documents();

        // then: the typed-in receipt stays its own row
        assertThat(documents.rows()).extracting(OrderPageModel.DocumentRow::number).containsExactly("PAR/KASA/1");
        OrderPageModel.ReceiptRow receipt = documents.receipt();
        assertThat(receipt.settled()).isTrue();
        assertThat(receipt.settledOutcome()).isEqualTo("sprzedaż z kasy (POS) bez e-maila klienta");
        assertThat(receipt.problem()).isNull();
        assertThat(receipt.hasActions()).isFalse();
    }

    @Test
    void aDeadAttemptOfACancelledOrderSaysTheOrderIsCancelled() {
        // given
        Order order = order(OrderStatus.Cancelled);
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FAILED)),
                new ReceiptOrderView(List.of(new ReceiptOrderView.Row(KEY_1, ReceiptAttemptState.FAILED,
                        "receipts.state.FAILED", "is-neutral", null, null, null, null, false, false, false, 1, null, null,
                        "zła stawka VAT", true)), false), false, false));

        // when
        OrderPageModel.ReceiptRow receipt = factory.build(order, List.of(), ADMIN, PL).documents().receipt();

        // then
        assertThat(receipt.settled()).isTrue();
        assertThat(receipt.settledKey()).isEqualTo("receipts.row.settled.cancelled");
        assertThat(receipt.settledOutcome()).isEqualTo("zła stawka VAT");
        assertThat(receipt.canReissue()).isFalse();
    }

    // --- a POS sale without the customer's e-mail; the fiscalised receipt being attached ---------------------------

    /** A point-of-sale order whose walk-in buyer has no e-mail of their own. */
    private static Order posSale(OrderStatus status) {
        Order order = order(status);
        order.setSource(new pl.commercelink.orders.OrderSource("operator", pl.commercelink.orders.OrderSourceType.PointOfSale));
        order.setBillingDetails(new BillingDetails());
        return order;
    }

    /** The POS sale's automatic attempt, blocked for the missing e-mail: dead, so "Wystaw ponownie" is offered. */
    private static ReceiptOrderState posBlocked() {
        return new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.BLOCKED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.BLOCKED, null, null, null,
                        ReceiptPageProblem.ofLines("E-paragonu nie wysłano", List.of("a", "b", "c"), null, null),
                        false, false, false, "sprzedaż z kasy (POS) bez e-maila klienta")), true), false, false);
    }

    @Test
    void reissueIsGreyedForAPosSaleWithoutTheCustomersEmail() {
        // given
        receipts(posBlocked());

        // when
        OrderPageModel.ReceiptRow receipt = factory.build(posSale(OrderStatus.Delivered), List.of(), ADMIN, PL)
                .documents().receipt();

        // then: still there (the row's only way on), greyed with what to do first; the cause has the bad tone
        assertThat(receipt.canReissue()).isTrue();
        assertThat(receipt.reissueBlockedKey()).isEqualTo("receipts.action.posNeedsEmail");
        assertThat(receipt.hasActions()).isTrue();
        assertThat(receipt.problemTone()).isEqualTo("is-bad");
        assertThat(receipt.problem().actions()).hasSize(3);
    }

    @Test
    void eReceiptInTheIssueMenuIsGreyedForAPosSaleWithoutTheCustomersEmail() {
        // given: no attempt yet, the store has a receipt system
        receipts(new ReceiptOrderState(List.of(), new ReceiptOrderView(List.of(), false), true, false));

        // when
        OrderPageModel.DocumentsCard pos = factory.build(posSale(OrderStatus.New), List.of(), ADMIN, PL).documents();
        OrderPageModel.DocumentsCard web = factory.build(order(OrderStatus.New), List.of(), ADMIN, PL).documents();

        // then: the entry and its menu stay, greyed with the same reason as "Wystaw ponownie"
        assertThat(pos.canIssueReceipt()).isTrue();
        assertThat(pos.canIssue()).isTrue();
        assertThat(pos.issueReceiptBlockedKey()).isEqualTo("receipts.action.posNeedsEmail");
        assertThat(web.canIssueReceipt()).isTrue();
        assertThat(web.issueReceiptBlockedKey()).isNull();
    }

    @Test
    void reissueIsOfferedOnceThePosSaleHasTheCustomersEmail() {
        // given
        receipts(posBlocked());
        Order order = posSale(OrderStatus.Delivered);
        order.getBillingDetails().setEmail("klient@example.com");

        // when
        OrderPageModel.ReceiptRow receipt = factory.build(order, List.of(), ADMIN, PL).documents().receipt();
        OrderPageModel.ReceiptRow web = factory.build(order(OrderStatus.Delivered), List.of(), ADMIN, PL)
                .documents().receipt();

        // then: the confirmation adds the cash register sentence for the POS sale only
        assertThat(receipt.canReissue()).isTrue();
        assertThat(receipt.reissueBlockedKey()).isNull();
        assertThat(receipt.reissueConfirmKey()).isEqualTo("receipts.action.reissue.confirm.message.pos");
        assertThat(web.reissueConfirmKey()).isEqualTo("receipts.action.reissue.confirm.message");
    }

    @Test
    void locksSayTheReceiptIsBeingAttachedOnceItIsFiscalised() {
        // given: fiscalised, its document not on the order yet
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, null, "PAR/1", null, null,
                        false, false, false, null)), false), false, true));
        OrderItem item = item(FulfilmentStatus.New);

        // when
        OrderPageModel page = factory.build(order(OrderStatus.Delivered), List.of(item), ADMIN, PL);
        OrderPageModel.Header cancellable = factory.build(order(OrderStatus.Delivered), returnedItems(), ADMIN, PL).header();

        // then: every reason says "being attached", none "being issued"
        assertThat(page.items().addItemsReasonKey()).isEqualTo("order.items.add.locked.receiptAttaching");
        assertThat(page.items().addItemsReasonIsSentence()).isTrue();
        assertThat(page.items().bulkStandalone().reasonKey()).isEqualTo("order.bulk.unavailable.receiptAttaching");
        assertThat(page.items().bulkStandalone().shortReasonKey()).isEqualTo("order.bulk.unavailable.receiptAttaching.short");
        assertThat(page.items().products().get(0).actions()).filteredOn(a -> a.action() == ItemAction.CONSOLIDATE)
                .singleElement().satisfies(a -> assertThat(a.reasonKey()).isEqualTo("order.item.unavailable.receiptAttaching"));
        assertThat(page.customer().billingLockedKey()).isEqualTo("order.customer.billing.locked.receiptAttaching");
        assertThat(page.documents().addLockedKey()).isEqualTo("order.documents.add.locked.receiptAttaching");
        assertThat(cancellable.cancelLockedKey()).isEqualTo("order.page.cancel.locked.receiptAttaching");
        // the row says what the pill does not: the document is on its way
        assertThat(page.documents().receipt().attachingKey()).isEqualTo("receipts.row.attaching");
        // the server wording agrees
        assertThat(ItemSaleLock.of(order(OrderStatus.Delivered), ReceiptLock.ATTACHING)).isEqualTo(ItemSaleLock.RECEIPT_ATTACHING);
        // every attaching key exists in both bundles (OrderDetailsMessagesTest only sees the keys written out)
        for (String key : List.of("order.items.add.locked.receiptAttaching", "order.bulk.unavailable.receiptAttaching",
                "order.bulk.unavailable.receiptAttaching.short", "order.item.unavailable.receiptAttaching",
                "order.customer.billing.locked.receiptAttaching", "order.documents.add.locked.receiptAttaching",
                "order.page.cancel.locked.receiptAttaching", "order.page.delete.locked.receiptAttaching",
                "order.item.consolidation.locked.receiptAttaching")) {
            assertThat(messageSource.getMessage(key, null, PL)).as(key).contains("dołącza");
            assertThat(messageSource.getMessage(key, null, Locale.ENGLISH)).as(key).containsIgnoringCase("attached");
        }
    }

    @Test
    void whileTheReceiptIsBeingIssuedTheRowHasNoAttachingNote() {
        // given
        receipts(issuing());

        // when
        OrderPageModel page = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)), ADMIN, PL);

        // then
        assertThat(page.documents().receipt().attachingKey()).isNull();
        assertThat(page.items().addItemsReasonKey()).isEqualTo("order.items.add.locked.receipt");
        assertThat(page.items().addItemsReasonIsSentence()).isTrue();
    }

    @Test
    void manualReceiptIsNotOfferedWhileAnAttemptOwnsTheReceipt() {
        // given: an attempt is issuing (it owns the receipt); then one that failed (it owns nothing)
        receipts(issuing());
        Order order = order(OrderStatus.Delivered);

        // when
        OrderPageModel.DocumentsCard owned = factory.build(order, List.of(), ADMIN, PL).documents();
        receipts(new ReceiptOrderState(List.of(attempt(KEY_1, 1, ReceiptAttemptState.FAILED)),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FAILED, null, null, null, null,
                        false, false, false, "VAT")), true), false, false));
        OrderPageModel.DocumentsCard free = factory.build(order, List.of(), ADMIN, PL).documents();

        // then: a typed-in "Paragon" would be a second receipt for the same sale
        assertThat(owned.manualTypes()).extracting(o -> o.value()).doesNotContain(DocumentType.Receipt);
        assertThat(free.manualTypes()).extracting(o -> o.value()).contains(DocumentType.Receipt);
    }

    @Test
    void aReceiptWhoseAttachingKeepsFailingPointsToTheRowInsteadOfAskingToWait() {
        // given: fiscalised, its effects failed often enough to raise EFFECTS_FAILED, the document never reached the order
        ReceiptAttempt stuck = attempt(KEY_1, 1, ReceiptAttemptState.FISCALISED);
        stuck.setEffectsFailures(3);
        receipts(new ReceiptOrderState(List.of(stuck),
                new ReceiptOrderView(List.of(row(KEY_1, 1, ReceiptAttemptState.FISCALISED, null, "PAR/1", null,
                        new ReceiptPageProblem("Dołączenie wciąż się nie udaje", "Sprawdź, czego brakuje.", null, null),
                        false, false, false, null)), false), false, true));

        // when
        OrderPageModel page = factory.build(order(OrderStatus.Delivered), List.of(item(FulfilmentStatus.New)), ADMIN, PL);
        OrderPageModel.Header cancellable = factory.build(order(OrderStatus.Delivered), returnedItems(), ADMIN, PL).header();

        // then: no "Dołączanie … odśwież za chwilę" under a problem saying it keeps failing
        assertThat(page.documents().receipt().attachingKey()).isNull();
        assertThat(page.documents().receipt().problem()).isNotNull();
        // the lock stays, its reasons send the operator to the row instead of promising it clears by itself
        assertThat(page.items().addItemsReasonKey()).isEqualTo("order.items.add.locked.receiptAttachFailed");
        assertThat(page.items().addItemsReasonIsSentence()).isTrue();
        assertThat(page.items().bulkStandalone().reasonKey()).isEqualTo("order.bulk.unavailable.receiptAttachFailed");
        assertThat(page.customer().billingLockedKey()).isEqualTo("order.customer.billing.locked.receiptAttachFailed");
        assertThat(page.documents().addLockedKey()).isEqualTo("order.documents.add.locked.receiptAttachFailed");
        assertThat(cancellable.cancelLockedKey()).isEqualTo("order.page.cancel.locked.receiptAttachFailed");
        assertThat(ItemSaleLock.of(order(OrderStatus.Delivered), ReceiptLock.ATTACH_FAILED))
                .isEqualTo(ItemSaleLock.RECEIPT_ATTACH_FAILED);
        for (String key : List.of("order.items.add.locked.receiptAttachFailed", "order.bulk.unavailable.receiptAttachFailed",
                "order.bulk.unavailable.receiptAttachFailed.short", "order.item.unavailable.receiptAttachFailed",
                "order.customer.billing.locked.receiptAttachFailed", "order.documents.add.locked.receiptAttachFailed",
                "order.page.cancel.locked.receiptAttachFailed", "order.page.delete.locked.receiptAttachFailed",
                "order.item.consolidation.locked.receiptAttachFailed", "order.item.form.name.locked.receiptAttachFailed",
                "order.item.form.numbers.locked.receiptAttachFailed", "order.item.form.price.locked.receiptAttachFailed",
                "order.item.error.sale.locked.receiptAttachFailed", "order.item.split.group.locked.receiptAttachFailed")) {
            String pl = messageSource.getMessage(key, null, PL);
            assertThat(pl).as(key).doesNotContain("odśwież").doesNotContain("trwa dołączanie");
            assertThat(messageSource.getMessage(key, null, Locale.ENGLISH)).as(key).doesNotContain("refresh");
        }
        assertThat(messageSource.getMessage("order.page.cancel.locked.receiptAttaching", null, PL)).doesNotContain("odśwież");
    }

    private static Shipment furgonetkaPackage() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        shipment.setCarrier("DPD");
        shipment.setPickUpAddressId("addr-1");
        shipment.setExternalId("21480003");
        shipment.setTrackingNo("0000123");
        shipment.setShippedAt(LocalDateTime.now().minusHours(1));
        shipment.setPickup(ShipmentPickup.awaiting());
        return shipment;
    }

    private static Shipment creating() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        shipment.setCarrier("DPD");
        shipment.setPickUpAddressId("addr-1");
        shipment.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        return shipment;
    }

    private static Order orderWith(Shipment... shipments) {
        Order order = order(OrderStatus.Shipping);
        order.setShipments(new java.util.ArrayList<>(List.of(shipments)));
        return order;
    }

    @Test
    void aShipmentBeingCreatedReadsAsCreatingAndThePagePollsWithoutEditOrRemoval() {
        // given
        Order order = orderWith(creating());

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentRow row = card.rows().get(0);

        // then
        assertThat(row.stateKey()).isEqualTo("order.shipments.state.creating");
        assertThat(row.stateTone()).isEqualTo("is-info");
        assertThat(row.stateInProgress()).isTrue();
        assertThat(card.cancellationPollHref())
                .isEqualTo("/dashboard/orders/" + order.getOrderId() + "/shipments/cancellation-state");
        assertThat(row.editHref()).isNull();
        assertThat(row.removeHref()).isNull();
        assertThat(row.removeReasonKey()).isEqualTo("order.shipments.remove.locked.creating");
        assertThat(row.labelHref()).isNull();
        assertThat(row.retryHref()).isNull();
    }

    @Test
    void aFailedCreationShowsTheProviderReasonWithRetryAndRemove() {
        // given
        Shipment failed = creating();
        failed.setCreation(failed.getCreation().failed("Brak środków na koncie"));
        Order order = orderWith(failed);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentRow row = card.rows().get(0);

        // then
        assertThat(row.stateKey()).isEqualTo("order.shipments.state.creation.failed");
        assertThat(row.stateArgs()).containsExactly("Brak środków na koncie");
        assertThat(row.stateTone()).isEqualTo("is-warn");
        assertThat(row.stateInProgress()).isFalse();
        assertThat(row.retryHref()).isEqualTo("/dashboard/orders/" + order.getOrderId() + "/shipping");
        assertThat(row.removeHref()).startsWith("/dashboard/orders/" + order.getOrderId() + "/shipments/0/remove");
        assertThat(row.editHref()).isNull();
        assertThat(card.cancellationPollHref()).isNull();
    }

    @Test
    void aFailedCreationWithOurOwnReasonShowsThatReasonInTheViewersLanguage() {
        // given
        Shipment failed = creating();
        failed.setCreation(failed.getCreation().failedWithKey("shipping.creation.unconfirmed"));

        // when
        OrderPageModel.ShipmentRow row = factory.build(orderWith(failed), List.of(), ADMIN, PL).shipments().rows().get(0);

        // then
        assertThat(row.stateKey()).isEqualTo("shipping.creation.unconfirmed");
        assertThat(row.stateArgs()).isEmpty();
    }

    @Test
    void aCreationPendingPastTheTimeoutReadsAsUnconfirmedWithRetryAndRemove() {
        // given: the check never came (the JVM stopped before it was sent, or it kept failing)
        Shipment stuck = creating();
        stuck.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now().minusMinutes(11)));
        Order order = orderWith(stuck);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentRow row = card.rows().get(0);

        // then
        assertThat(row.stateKey()).isEqualTo("shipping.creation.unconfirmed");
        assertThat(row.stateInProgress()).isFalse();
        assertThat(row.retryHref()).isEqualTo("/dashboard/orders/" + order.getOrderId() + "/shipping");
        assertThat(row.removeHref()).startsWith("/dashboard/orders/" + order.getOrderId() + "/shipments/0/remove");
        assertThat(OrderPageModelFactory.removeLockedKey(order, 0)).isNull();
        assertThat(card.cancellationPollHref()).isNull();
        assertThat(order.hasShipmentBeingCreated()).isFalse();
        assertThat(order.hasShipmentToBook()).isTrue();
    }

    @Test
    void aPackageWaitingForPickupOffersItsLabelAndTheCardOffersThePickupPage() {
        // given
        Order order = orderWith(furgonetkaPackage());
        when(shippingService.supportsLabels(any(), eq("furgonetka"))).thenReturn(true);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentRow row = card.rows().get(0);

        // then
        assertThat(row.stateKey()).isEqualTo("order.shipments.state.pickup.awaiting");
        assertThat(row.stateTone()).isEqualTo("is-neutral");
        assertThat(row.labelHref())
                .isEqualTo("/dashboard/shipping/labels/furgonetka/21480003?back=/dashboard/orders/" + order.getOrderId());
        assertThat(card.pickupHref()).isEqualTo("/dashboard/shipping/pickups/new?group=furgonetka%7CDPD%7Caddr-1&back=/dashboard/orders/"
                + order.getOrderId());
        assertThat(row.editHref()).isNotNull();
    }

    @Test
    void noLabelLinkWhenTheIntegrationCannotHandOutLabels() {
        // given
        when(shippingService.supportsLabels(any(), any())).thenReturn(false);

        // when
        OrderPageModel.ShipmentRow row = factory.build(orderWith(furgonetkaPackage()), List.of(), ADMIN, PL)
                .shipments().rows().get(0);

        // then
        assertThat(row.labelHref()).isNull();
    }

    @Test
    void anOrderedPickupShowsItsDayAndHoursAndNeedsNoPickupButton() {
        // given
        Shipment ordered = furgonetkaPackage();
        ordered.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)).ordered("P-1"));

        // when
        OrderPageModel.ShipmentsCard card = factory.build(orderWith(ordered), List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).stateKey()).isEqualTo("order.shipments.state.pickup.ordered");
        assertThat(card.rows().get(0).stateArgs()).containsExactly("czw. 8 paź", "9:00", "17:00");
        assertThat(card.rows().get(0).stateTone()).isEqualTo("is-ok");
        assertThat(card.pickupHref()).isNull();
    }

    @Test
    void aPickupBeingOrderedReadsAsInProgressAndThePagePolls() {
        // given
        Shipment pending = furgonetkaPackage();
        pending.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)));
        Order order = orderWith(pending);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).stateKey()).isEqualTo("order.shipments.state.pickup.pending");
        assertThat(card.rows().get(0).stateInProgress()).isTrue();
        assertThat(card.cancellationPollHref()).isNotNull();
        assertThat(card.pickupHref()).isNull();
    }

    @Test
    void aPickupPendingPastTheTimeoutReadsAsUnconfirmedAndCanBeOrderedAgain() {
        // given
        Shipment stuck = furgonetkaPackage();
        stuck.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now().minusMinutes(11), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)));
        Order order = orderWith(stuck);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).stateKey()).isEqualTo("shipping.pickup.unconfirmed");
        assertThat(card.rows().get(0).stateInProgress()).isFalse();
        assertThat(card.cancellationPollHref()).isNull();
        assertThat(card.pickupHref()).isNotNull();
    }

    @Test
    void aFailedPickupShowsTheReasonAndCanBeOrderedAgain() {
        // given
        Shipment failed = furgonetkaPackage();
        failed.setPickup(ShipmentPickup.awaiting().failed("Brak kuriera w rejonie"));
        Order order = orderWith(failed);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).stateKey()).isEqualTo("order.shipments.state.pickup.failed");
        assertThat(card.rows().get(0).stateArgs()).containsExactly("Brak kuriera w rejonie");
        assertThat(card.pickupHref()).isNotNull();
    }

    @Test
    void aPackageHandedInAtAPointSaysSo() {
        // given
        Shipment point = furgonetkaPackage();
        point.setPickup(ShipmentPickup.notRequired());

        // when
        OrderPageModel.ShipmentsCard card = factory.build(orderWith(point), List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).stateKey()).isEqualTo("order.shipments.state.pickup.point");
        assertThat(card.pickupHref()).isNull();
    }

    @Test
    void aShipmentTypedInByHandHasNoStateLabelOrPickup() {
        // given
        Order order = order(OrderStatus.Shipping);
        labelled(order.getShipments().get(0), "T-1", null);
        when(shippingService.supportsLabels(any(), any())).thenReturn(true);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(order, List.of(), ADMIN, PL).shipments();

        // then
        assertThat(card.rows().get(0).stateKey()).isNull();
        assertThat(card.rows().get(0).labelHref()).isNull();
        assertThat(card.rows().get(0).retryHref()).isNull();
        assertThat(card.pickupHref()).isNull();
        assertThat(card.cancellationPollHref()).isNull();
    }

    @Test
    void aReadOnlyPageShowsTheStateWithoutActions() {
        // given
        when(shippingService.supportsLabels(any(), any())).thenReturn(true);

        // when
        OrderPageModel.ShipmentsCard card = factory.build(orderWith(furgonetkaPackage()), List.of(),
                new OrderPageModelFactory.Viewer(true, false, null), PL).shipments();

        // then
        assertThat(card.rows().get(0).stateKey()).isEqualTo("order.shipments.state.pickup.awaiting");
        assertThat(card.rows().get(0).labelHref()).isNull();
        assertThat(card.pickupHref()).isNull();
    }

    @Test
    void theCourierActionIsHiddenWhileAShipmentIsBeingCreated() {
        // given: a data-less row next to the one being created would otherwise still offer it
        Order order = assembledOrderWithOneEmptyShipment();
        order.getShipments().get(0).setShippedAt(null);
        order.addShipment(creating());

        // when
        OrderPageModel page = factory.build(order, List.of(), viewer(), PL);

        // then
        assertThat(page.header().primaryAction()).isNull();
    }
}
