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
}
