package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The pickup page, its index and the settling of a pickup command, on a real order owner. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentPickupIndexFlowTest {

    private static final PickupWindow WINDOW =
            new PickupWindow(LocalDate.of(2026, 10, 7), LocalTime.of(9, 0), LocalTime.of(17, 0), "h");

    @Mock private OrdersRepository ordersRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private ShipmentTrackingSubscriber trackingSubscriber;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private OrderLifecycleEventPublisher lifecycleEventPublisher;
    @Mock private AwaitingPickupIndex index;
    @Mock private ShippingService shippingService;
    @Mock private ShipmentPickupEventPublisher publisher;
    @Mock private MessageSource messageSource;

    private Order order;
    private ShipmentPickupService service;
    private ShipmentPickupSettler settler;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        order = new Order("store-1");
        order.setOrderId("order-1");
        when(ordersRepository.findById("store-1", "order-1")).thenAnswer(i -> order);
        AwaitingPickup entry = new AwaitingPickup();
        entry.setStoreId("store-1");
        entry.setExternalId("21480003");
        entry.setProvider("furgonetka");
        entry.setCarrier("dpd");
        entry.setPickUpAddressId("addr-1");
        entry.setOwnerType(ShipmentOwnerType.ORDER);
        entry.setOwnerId("order-1");
        when(index.list("store-1")).thenReturn(List.of(entry));
        ShipmentOwners owners = new ShipmentOwners(List.of(new OrderShipmentOwner(ordersRepository,
                optimisticLockingExecutor, trackingSubscriber, orderLifecycle, lifecycleEventPublisher)));
        service = new ShipmentPickupService(index, owners, shippingService, publisher, messageSource);
        settler = new ShipmentPickupSettler(owners, index);
    }

    private void packageWithPickup(ShipmentPickup pickup) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId("21480003");
        s.setTrackingNo("A");
        s.setProvider("furgonetka");
        s.setPickUpAddressId("addr-1");
        s.setPickup(pickup);
        order.setShipments(new ArrayList<>(List.of(s)));
    }

    private static ShipmentPickup pending(LocalDateTime requestedAt) {
        return ShipmentPickup.pending("cmd-1", requestedAt, WINDOW.date(), WINDOW.from(), WINDOW.to());
    }

    @Test
    void aPackageSeenWhileItsPickupWasPendingIsListedAgainOnceTheCommandFails() {
        // given: the page is opened while the command is in flight
        packageWithPickup(pending(LocalDateTime.now()));
        List<PickupGroup> whilePending = service.groups("store-1");

        // when
        settler.failed(ShipmentPickupCheckRequest.of("store-1", "furgonetka", "cmd-1",
                List.of(new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "21480003", "A")), WINDOW), "Brak podjazdu");
        List<PickupGroup> afterFailure = service.groups("store-1");

        // then
        assertThat(whilePending).isEmpty();
        assertThat(afterFailure).hasSize(1);
        assertThat(afterFailure.get(0).entries()).extracting(AwaitingPickup::getExternalId).containsExactly("21480003");
        verify(index, never()).remove(anyString(), anyCollection());
    }

    @Test
    void aPickupNeverConfirmedIsListedToBeOrderedAgain() {
        // given
        packageWithPickup(pending(LocalDateTime.now().minusMinutes(11)));

        // when
        List<PickupGroup> groups = service.groups("store-1");

        // then
        assertThat(groups).hasSize(1);
        verify(index, never()).remove(anyString(), anyCollection());
    }

    @Test
    void anOrderedPickupLeavesTheIndex() {
        // given
        packageWithPickup(pending(LocalDateTime.now()).ordered("P-1"));

        // when
        List<PickupGroup> groups = service.groups("store-1");

        // then
        assertThat(groups).isEmpty();
        verify(index).remove("store-1", List.of("21480003"));
    }
}
