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
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
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
    private final DeliveryRedirectResolver deliveryRedirectResolver = new DeliveryRedirectResolver();
    private final MessageSource messageSource = OrderClosingChecklistTest.messages();

    private OrderPageModelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OrderPageModelFactory(storesRepository, orderEventsRepository, dropshipItemLookup,
                deliveryRedirectResolver, supplierLabels, shipmentCarrierOptions, productCatalogRepository, taxonomyCache,
                messageSource);
        ReflectionTestUtils.setField(factory, "appDomain", "https://app.example");
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierLabels.forStore(any())).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of());
        when(orderEventsRepository.findByOrderId(anyString())).thenReturn(List.of());
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
        // when (P12, Review Focus 1)
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
        // when (9a CARRY: OrderItemRow.Context.readOnly = closed OR SUPER_ADMIN, spec §3.5)
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
    void aPlainOrderKeepsItsBulkActionsAvailable() {
        // when (moved from OrderDetailsControllerTest, Task 10)
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
        assertThat(page.checklist()).isNull();
        assertThat(page.header().completedAutomatically()).isTrue();
        assertThat(page.backHref()).isEqualTo("/dashboard/orders?view=Completed");
    }

    @Test
    void aUserNeverReceivesCostOrProfit() {
        // when (B10)
        OrderPageModel user = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, false, null), PL);
        OrderPageModel admin = factory.build(order(OrderStatus.New), List.of(item(FulfilmentStatus.New)),
                new OrderPageModelFactory.Viewer(false, true, null), PL);

        // then
        assertThat(user.finances().costs()).isNull();
        assertThat(user.items().products().get(0).unitCost()).isNull();
        assertThat(admin.finances().costs()).isNotNull();
    }

    @Test
    void anIncompletePaymentStillAppearsInThePaymentsList() {
        // given (D-11): a zero-amount payment with no reference is not Payment.isComplete(), but it is still a
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
        // given (B8)
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
        assertThat(page.customer().billing().isEmpty()).isTrue();
        assertThat(page.checklist()).isNotNull();
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
}
