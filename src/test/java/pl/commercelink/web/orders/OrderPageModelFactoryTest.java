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
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.receipts.ReceiptAlerts;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.receipts.ReceiptOrderView;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.receipts.ReceiptOrderState;
import pl.commercelink.receipts.ReceiptPageProblem;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
                messageSource, receiptAttemptService, receiptAlerts);
        ReflectionTestUtils.setField(factory, "appDomain", "https://app.example");
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierLabels.forStore(any())).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of());
        when(orderEventsRepository.findByOrderId(anyString())).thenReturn(List.of());
        when(receiptAttemptService.orderState(any(), any(), any(), any())).thenReturn(ReceiptOrderState.NONE);
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
        assertThat(superAdmin.items().products().get(0).unitCost()).isNotNull();
        assertThat(superAdmin.readOnly()).isTrue();
        assertThat(superAdmin.admin()).isFalse();
        assertThat(user.finances().costs()).isNotNull();
        assertThat(user.items().products().get(0).unitCost()).isNotNull();
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
        assertThat(user.items().products().get(0).unitCost()).isNotNull()
                .isEqualTo(admin.items().products().get(0).unitCost());
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
        assertThat(admin.header().primaryAction().href()).endsWith("/dropship?provider=Acme");
        assertThat(user.header().primaryAction()).isNull();
        assertThat(user.items().products().get(0).deliveryHref()).isNull();
    }

    @Test
    void withItemsAtTwoSuppliersTheSupplierOrderNamesTheFirstWaitingOne() {
        // given: without ?provider= the dropship page redirects back to choose a supplier
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
        assertThat(page.header().primaryAction().href()).endsWith("/dropship?provider=Acme+B");
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
        assertThat(both.header().primaryAction().href()).endsWith("/dropship?provider=Acme");
        assertThat(both.items().products().get(1).deliveryHref()).endsWith("/dropship?provider=Acme");
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
    void aCourierShipmentOfAnOrderNotYetShippingSaysWhenItCanBeCancelled() {
        // given: a courier ordered while the order is still being assembled; "Cancel courier order" is not offered yet
        Order assembling = order(OrderStatus.Assembly);
        labelled(assembling.getShipments().get(0), "T-1", "PKG-1");
        // two couriers: the button only ever cancels the first shipment that went out
        Order twoCouriers = order(OrderStatus.Shipping);
        labelled(twoCouriers.getShipments().get(0), "T-1", "PKG-1");
        Shipment second = new Shipment(ShipmentType.Courier);
        labelled(second, "T-2", "PKG-2");
        twoCouriers.getShipments().add(second);

        // when
        OrderPageModel.ShipmentsCard early = factory.build(assembling, List.of(), ADMIN, PL).shipments();
        OrderPageModel.ShipmentsCard shipping = factory.build(twoCouriers, List.of(), ADMIN, PL).shipments();

        // then: the reason never points to a button the page does not show
        assertThat(early.canCancelCourier()).isFalse();
        assertThat(early.rows().get(0).removeHref()).isNull();
        assertThat(early.rows().get(0).removeReasonKey()).isEqualTo("order.shipments.remove.locked.courierLater");
        assertThat(shipping.canCancelCourier()).isTrue();
        assertThat(shipping.rows().get(0).removeReasonKey()).isEqualTo("order.shipments.remove.locked.courier");
        assertThat(shipping.rows().get(1).removeReasonKey()).isEqualTo("order.shipments.remove.locked.courierNotFirst");
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
    void aRefundIsShownWithOneMinusWhateverItsSign() {
        // given
        Order typedNegative = order(OrderStatus.New);
        typedNegative.addPayment(refund(-100));
        Order storedPositive = order(OrderStatus.New);
        storedPositive.addPayment(refund(100));

        // when
        OrderPageModel.PaymentRow negative = factory.build(typedNegative, List.of(), ADMIN, PL).payments().rows().get(0);
        OrderPageModel.PaymentRow positive = factory.build(storedPositive, List.of(), ADMIN, PL).payments().rows().get(0);

        // then
        assertThat(negative.amount()).isEqualTo("−100,00");
        assertThat(positive.amount()).isEqualTo("−100,00");
        assertThat(negative.refund()).isTrue();
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

    private String itemHistory(String... serials) {
        List<OrderItem> items = java.util.Arrays.stream(serials).map(sn -> {
            OrderItem item = item(FulfilmentStatus.Delivered);
            item.setSerialNo(sn);
            return item;
        }).toList();
        return factory.build(order(OrderStatus.Delivered), items, ADMIN, PL).header().itemHistoryHref();
    }

    @Test
    void theItemHistoryLinkNeedsExactlyOneSerialNumber() {
        // when / then
        assertThat(itemHistory()).isNull();
        assertThat(itemHistory(new String[]{null})).isNull();
        assertThat(itemHistory("SN-1")).isEqualTo("/dashboard/item/history?serialNo=SN-1");
        assertThat(itemHistory("A, B")).isNull();
        assertThat(itemHistory("A", "B")).isNull();
        assertThat(itemHistory("SN-1", " SN-1 ")).isEqualTo("/dashboard/item/history?serialNo=SN-1");
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
    void theSelectionRowGroupsTheActionsIntoRouteAndMoveMenusEachCarryingItsOwnReason() {
        // given
        Order order = order(OrderStatus.New);
        order.addPayment(Payment.bankTransfer("R/1", "Jan", 10));
        OrderItem item = item(FulfilmentStatus.New);
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of(item.getItemId()));

        // when
        OrderPageModel.ItemsCard items = factory.build(order, List.of(item), ADMIN, PL).items();

        // then
        assertThat(items.bulkMenus()).extracting(OrderPageModel.BulkMenu::menu)
                .containsExactly(BulkAction.Menu.ROUTE, BulkAction.Menu.MOVE);
        assertThat(items.bulkMenus().get(0).actions()).extracting(OrderPageModel.BulkActionButton::action)
                .containsExactly(BulkAction.ALLOCATE, BulkAction.TO_WAREHOUSE, BulkAction.TO_WAREHOUSE_RMA);
        assertThat(items.bulkMenus().get(0).actions()).extracting(OrderPageModel.BulkActionButton::reasonKey)
                .containsOnly("order.items.action.dropship.locked");
        assertThat(items.bulkMenus().get(1).actions()).extracting(OrderPageModel.BulkActionButton::action)
                .containsExactly(BulkAction.SPLIT, BulkAction.MOVE);
        assertThat(items.bulkMenus().get(1).actions()).extracting(OrderPageModel.BulkActionButton::reasonKey)
                .containsOnly("order.bulk.unavailable.split");
        assertThat(items.bulkStandalone().action()).isEqualTo(BulkAction.REMOVE);
        assertThat(items.bulkStandalone().reasonKey()).isEqualTo("order.items.action.dropship.locked");
        assertThat(items.bulkStandalone().shortReasonKey()).isEqualTo("order.items.action.dropship.locked.short");
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
        assertThat(items.bulkMenus()).hasSize(2);
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

        // then: a second sale document is offered neither from the invoicing system nor by hand
        assertThat(documents.issuable()).isEmpty();
        assertThat(documents.canIssue()).isFalse();
        assertThat(documents.canAdd()).isFalse();
        assertThat(documents.addLockedKey()).isEqualTo("order.documents.add.locked.receipt");
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
        assertThat(page.items().bulkMenus().get(1).actions()).extracting(OrderPageModel.BulkActionButton::reasonKey)
                .containsOnly("order.bulk.unavailable.receipt");
        assertThat(page.items().bulkMenus().get(0).actions()).allMatch(OrderPageModel.BulkActionButton::available);
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
                        + " e-paragon — anulowanie go nie cofa. Zwrot rozlicz osobno (korekta lub zwrot).");
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
                        "sprzedaż POS bez e-maila klienta", true)), false), false, false));

        // when
        OrderPageModel.DocumentsCard documents = factory.build(order, List.of(), ADMIN, PL).documents();

        // then: the typed-in receipt stays its own row
        assertThat(documents.rows()).extracting(OrderPageModel.DocumentRow::number).containsExactly("PAR/KASA/1");
        OrderPageModel.ReceiptRow receipt = documents.receipt();
        assertThat(receipt.settled()).isTrue();
        assertThat(receipt.settledOutcome()).isEqualTo("sprzedaż POS bez e-maila klienta");
        assertThat(receipt.problem()).isNull();
        assertThat(receipt.hasActions()).isFalse();
    }
}
