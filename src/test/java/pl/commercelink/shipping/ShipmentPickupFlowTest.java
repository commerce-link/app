package pl.commercelink.shipping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.CourierCancellation;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderListService;
import pl.commercelink.orders.OrderRealizationStepBack;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMALifecycle;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.shipping.api.PickupOrder;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pickup page computed from the store's orders and RMAs, ordering a pickup and settling it, on real owners: what
 * the page lists follows the shipments alone.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentPickupFlowTest {

    private static final PickupWindow WINDOW =
            new PickupWindow(LocalDate.of(2026, 10, 7), LocalTime.of(9, 0), LocalTime.of(17, 0), "h");
    private static final String WINDOW_VALUE = "2026-10-07|09:00|17:00|h";
    private static final String GROUP = PickupGroup.key("furgonetka", "dpd", "addr-1");

    @Mock private OrdersRepository ordersRepository;
    @Mock private RMARepository rmaRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private ShipmentTrackingSubscriber trackingSubscriber;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private OrderLifecycleEventPublisher lifecycleEventPublisher;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMALifecycle rmaLifecycle;
    @Mock private OrderEventsRepository orderEventsRepository;
    @Mock private ShippingService shippingService;
    @Mock private ShippingProvider provider;
    @Mock private ShipmentPickupEventPublisher publisher;
    @Mock private StoresRepository storesRepository;
    @Mock private ShippingProviderFactory providerFactory;
    @Mock private Store store;

    private Order order;
    private RMA rma;
    private Order otherStoresOrder;
    private ShipmentPickupService service;
    private ShipmentPickupSettler settler;
    private ShipmentPickupController controller;
    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        order = new Order("store-1");
        order.setOrderId("order-1");
        rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.Processing);
        otherStoresOrder = new Order("store-2");
        otherStoresOrder.setOrderId("order-2");
        otherStoresOrder.setShipments(new ArrayList<>(List.of(awaiting("B-1", "B"))));
        when(ordersRepository.findById("store-1", "order-1")).thenAnswer(i -> order);
        when(ordersRepository.findById("store-2", "order-2")).thenAnswer(i -> otherStoresOrder);
        when(ordersRepository.findByStoreAndStatuses("store-1", OrderListService.OPEN)).thenAnswer(i -> List.of(order));
        when(ordersRepository.findByStoreAndStatuses("store-2", OrderListService.OPEN))
                .thenAnswer(i -> List.of(otherStoresOrder));
        when(rmaRepository.findById("store-1", "rma-1")).thenAnswer(i -> rma);
        when(rmaRepository.findAllByStoreId("store-1")).thenAnswer(i -> List.of(rma));

        ShipmentOwners owners = new ShipmentOwners(List.of(
                new OrderShipmentOwner(ordersRepository, optimisticLockingExecutor, trackingSubscriber, orderLifecycle,
                        lifecycleEventPublisher),
                new RmaShipmentOwner(rmaRepository, optimisticLockingExecutor, rmaItemsRepository, rmaLifecycle,
                        trackingSubscriber)));
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new ShipmentPickupService(new PickupCandidates(ordersRepository, rmaRepository), owners,
                shippingService, publisher, messages);
        settler = new ShipmentPickupSettler(owners);
        controller = new ShipmentPickupController(service, storesRepository, providerFactory, messages);

        when(store.getStoreId()).thenReturn("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(shippingService.providerName(store)).thenReturn("furgonetka");
        when(shippingService.providerFor(store)).thenReturn(provider);
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.pending(i.getArgument(2)));
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private static Shipment awaiting(String externalId, String trackingNo) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId(externalId);
        s.setTrackingNo(trackingNo);
        s.setProvider("furgonetka");
        s.setCarrier("dpd");
        s.setPickUpAddressId("addr-1");
        s.setPickup(ShipmentPickup.awaiting());
        return s;
    }

    private void orderShips(Shipment... shipments) {
        order.setShipments(new ArrayList<>(List.of(shipments)));
    }

    private static ShipmentPickup pending(LocalDateTime requestedAt) {
        return ShipmentPickup.pending("cmd-1", requestedAt, WINDOW.date(), WINDOW.from(), WINDOW.to());
    }

    private List<String> listed() {
        return service.groups("store-1").stream()
                .flatMap(g -> g.entries().stream())
                .map(PickupCandidate::externalId)
                .toList();
    }

    private static ShipmentPickupCheckRequest check(List<PickupTarget> targets) {
        return ShipmentPickupCheckRequest.of("store-1", "furgonetka", "cmd-1", targets, WINDOW);
    }

    @Test
    void anOrderAndAnRmaShareOnePickupAndLeaveThePageOnceItIsOrdered() {
        // given
        orderShips(awaiting("1", "A"));
        rma.setShipments(new ArrayList<>(List.of(awaiting("2", "B"))));
        List<PickupGroup> groups = service.groups("store-1");

        // when
        String view = controller.order(GROUP, List.of("1", "2"), WINDOW_VALUE, "/dashboard/orders/order-1",
                new RedirectAttributesModelMap(), Locale.forLanguageTag("pl"));

        // then: both go in one command, and are no longer listed while it is in flight
        assertThat(groups).singleElement().satisfies(g -> assertThat(g.entries())
                .extracting(PickupCandidate::ownerType, PickupCandidate::ownerId)
                .containsExactly(tuple(ShipmentOwnerType.ORDER, "order-1"),
                        tuple(ShipmentOwnerType.RMA, "rma-1")));
        assertThat(view).isEqualTo("redirect:/dashboard/orders/order-1");
        verify(provider).orderPickup(eq(List.of("1", "2")), eq(WINDOW), anyString());
        assertThat(listed()).isEmpty();
        String commandId = order.getShipments().get(0).getPickup().getCommandId();

        // when: the command succeeds
        settler.ordered(ShipmentPickupCheckRequest.of("store-1", "furgonetka", commandId, List.of(
                new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "1", "A"),
                new PickupTarget(ShipmentOwnerType.RMA, "rma-1", "2", "B")), WINDOW), "P-1");

        // then
        assertThat(order.getShipments().get(0).getPickup().isOrdered()).isTrue();
        assertThat(rma.getShipments().get(0).getPickup().isOrdered()).isTrue();
        assertThat(listed()).isEmpty();
    }

    @Test
    void aPackageWhosePickupFailedIsListedAgain() {
        // given: the page is opened while the command is in flight
        orderShips(awaiting("1", "A"));
        order.getShipments().get(0).setPickup(pending(LocalDateTime.now()));
        List<String> whilePending = listed();

        // when
        settler.failed(check(List.of(new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "1", "A"))), "Brak podjazdu");

        // then
        assertThat(whilePending).isEmpty();
        assertThat(listed()).containsExactly("1");
    }

    @Test
    void aPickupNeverConfirmedIsListedToBeOrderedAgain() {
        // given
        orderShips(awaiting("1", "A"));
        order.getShipments().get(0).setPickup(pending(LocalDateTime.now().minusMinutes(11)));

        // when / then
        assertThat(listed()).containsExactly("1");
    }

    @Test
    void aMultiParcelPackageIsListedOnce() {
        // given
        orderShips(awaiting("1", "A"), awaiting("1", "B"));

        // when / then
        assertThat(listed()).containsExactly("1");
    }

    @Test
    void aCancelledShipmentLeavesThePage() {
        // given
        Shipment shipment = awaiting("1", "A");
        shipment.setCancellation(CourierCancellation.pending("cancel-1", LocalDateTime.now()));
        orderShips(shipment);
        List<String> beforeCancellation = listed();
        ShipmentCancellationSettler cancellation = new ShipmentCancellationSettler(ordersRepository,
                orderEventsRepository, optimisticLockingExecutor, new OrderRealizationStepBack(orderEventsRepository));

        // when
        cancellation.succeed(ShipmentCancellationCheckRequest.first("store-1", "order-1", "1", "cancel-1"));

        // then
        assertThat(beforeCancellation).containsExactly("1");
        assertThat(listed()).isEmpty();
    }

    @Test
    void aRemovedShipmentLeavesThePage() {
        // given
        orderShips(awaiting("1", "A"), awaiting("2", "B"));

        // when: the operator removes the shipment of package 1
        order.getShipments().remove(0);

        // then
        assertThat(listed()).containsExactly("2");
    }

    @Test
    void anRmaClosedRightAfterItsShipmentWasCreatedIsStillListed() {
        // given
        rma.setShipments(new ArrayList<>(List.of(awaiting("2", "B"))));
        rma.setStatus(RMAStatus.Completed);

        // when / then
        assertThat(listed()).containsExactly("2");
    }

    @Test
    void customerReturnsAndPackagesWithoutAPickupAddressAreNotListed() {
        // given: a customer's return is picked up at the customer's address
        Shipment customerReturn = awaiting("3", "C");
        customerReturn.setPickUpAddressId(null);
        rma.setShipments(new ArrayList<>(List.of(customerReturn)));

        // when / then
        assertThat(listed()).isEmpty();
    }

    @Test
    void aStoreCannotOrderAnotherStoresPackage() {
        // given
        orderShips(awaiting("1", "A"));
        RedirectAttributesModelMap ordered = new RedirectAttributesModelMap();

        // when
        controller.order(GROUP, List.of("B-1"), WINDOW_VALUE, null, ordered, Locale.forLanguageTag("pl"));

        // then
        assertThat(ordered.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Żadna z wybranych paczek nie czeka już na odbiór.");
        verify(provider, never()).orderPickup(anyList(), any(), anyString());
        verify(ordersRepository, never()).save(any());
        assertThat(otherStoresOrder.getShipments().get(0).getPickup().isAwaiting()).isTrue();
    }
}
