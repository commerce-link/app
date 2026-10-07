package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentPickupSettlerTest {

    @Mock private OrdersRepository ordersRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private ShipmentTrackingSubscriber trackingSubscriber;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private OrderLifecycleEventPublisher lifecycleEventPublisher;
    @Mock private ShipmentOwners owners;
    @Mock private WarehouseShipmentOwner warehouseOwner;

    private ShipmentPickupSettler settler;
    private Order order;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        order = new Order("store-1");
        order.setOrderId("order-1");
        order.setShipments(new ArrayList<>(List.of(shipment("1", "A"), shipment("1", "B"))));
        when(ordersRepository.findById("store-1", "order-1")).thenAnswer(i -> order);
        // the real owner: the pickup lands on the order's rows by its rules
        OrderShipmentOwner owner = new OrderShipmentOwner(ordersRepository, optimisticLockingExecutor,
                trackingSubscriber, orderLifecycle, lifecycleEventPublisher);
        when(owners.get(ShipmentOwnerType.ORDER)).thenReturn(owner);
        when(owners.get(ShipmentOwnerType.WAREHOUSE)).thenReturn(warehouseOwner);
        settler = new ShipmentPickupSettler(owners);
    }

    private static Shipment shipment(String externalId, String trackingNo) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId(externalId);
        s.setTrackingNo(trackingNo);
        s.setPickup(ShipmentPickup.pending("cmd-1", LocalDateTime.now(), LocalDate.of(2026, 10, 7),
                LocalTime.of(9, 0), LocalTime.of(17, 0)));
        return s;
    }

    private static ShipmentPickupCheckRequest request(String commandId) {
        return ShipmentPickupCheckRequest.builder().storeId("store-1").provider("furgonetka").commandId(commandId)
                .targets(List.of(new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "1", "A")))
                .date("2026-10-07").from("09:00").to("17:00").token("h").attempt(1).build();
    }

    @Test
    void orderedPickupLandsOnEveryRowOfThePackage() {
        // when
        settler.ordered(request("cmd-1"), "20261006800071");

        // then
        assertThat(order.getShipments()).allMatch(s -> s.getPickup().isOrdered()
                && "20261006800071".equals(s.getPickup().getPickupId()));
        verify(ordersRepository).save(order);
    }

    @Test
    void aPartialPickupOrdersOnlyTheListedPackagesAndLeavesTheOthersUnconfirmed() {
        // given
        order.setShipments(new ArrayList<>(List.of(shipment("1", "A"), shipment("2", "B"))));
        ShipmentPickupCheckRequest request = ShipmentPickupCheckRequest.builder().storeId("store-1")
                .provider("furgonetka").commandId("cmd-1")
                .targets(List.of(new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "1", "A"),
                        new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "2", "B")))
                .date("2026-10-07").from("09:00").to("17:00").token("h").attempt(1).build();

        // when
        settler.ordered(request, "20261006800071", List.of("1"));

        // then
        assertThat(order.getShipments().get(0).getPickup().isOrdered()).isTrue();
        assertThat(order.getShipments().get(1).getPickup().isFailed()).isTrue();
        assertThat(order.getShipments().get(1).getPickup().getErrorKey()).isEqualTo("shipping.pickup.unconfirmed");
        assertThat(order.getShipments().get(1).awaitsPickup()).isTrue();
    }

    @Test
    void lateResultForAnotherCommandIsIgnored() {
        // when
        List<String> warnings;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentPickupSettler.class)) {
            settler.ordered(request("cmd-old"), "x");
            warnings = logs.warnings();
        }

        // then
        assertThat(order.getShipments()).allMatch(s -> s.getPickup().isPendingFor("cmd-1"));
        assertThat(warnings).singleElement().satisfies(m -> assertThat(m).contains("cmd-old", "order-1"));
    }

    @Test
    void aFailedPickupStaysOrderable() {
        // when
        settler.failed(request("cmd-1"), "Brak możliwości podjazdu");

        // then
        assertThat(order.getShipments()).allMatch(s -> s.getPickup().isFailed() && s.awaitsPickup()
                && "Brak możliwości podjazdu".equals(s.getPickup().getError()));
    }

    @Test
    void anUnconfirmedPickupIsStoredAsAKeyAndAlerts() {
        // when
        List<String> errors;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentPickupSettler.class)) {
            settler.failedWithKey(request("cmd-1"), "shipping.pickup.unconfirmed");
            errors = logs.errors();
        }

        // then
        assertThat(order.getShipments()).allMatch(s -> s.getPickup().isFailed()
                && "shipping.pickup.unconfirmed".equals(s.getPickup().getErrorKey()));
        assertThat(errors).singleElement().satisfies(m -> assertThat(m).contains("cmd-1", "store-1", "1"));
    }

    @Test
    void anOwnerWithoutStoredShipmentsLearnsTheOutcome() {
        // given: the warehouse keeps nothing, so the settler computes what the pickup became
        PickupTarget target = new PickupTarget(ShipmentOwnerType.WAREHOUSE, null, "7", "W-7");
        when(warehouseOwner.applyPickup(eq("store-1"), any(), any(), any())).thenReturn(1);
        ShipmentPickupCheckRequest request = request("cmd-1").toBuilder().targets(List.of(target)).build();

        // when
        settler.ordered(request, "P-7");

        // then
        verify(warehouseOwner).onPickupSettled(eq("store-1"), eq("furgonetka"), eq(target),
                argThat(p -> p.isOrdered() && "P-7".equals(p.getPickupId()) && "2026-10-07".equals(p.getDate())));
    }

    @Test
    void aFollowUpThatFailsDoesNotStopTheOtherPackages() {
        // given
        PickupTarget warehouse = new PickupTarget(ShipmentOwnerType.WAREHOUSE, null, "7", "W-7");
        when(warehouseOwner.applyPickup(eq("store-1"), any(), any(), any())).thenReturn(1);
        doThrow(new RuntimeException("SES down")).when(warehouseOwner).onPickupSettled(any(), any(), any(), any());
        ShipmentPickupCheckRequest request = request("cmd-1").toBuilder()
                .targets(List.of(warehouse, new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "1", "A"))).build();

        // when
        settler.ordered(request, "P-7");

        // then
        assertThat(order.getShipments()).allMatch(s -> s.getPickup().isOrdered());
    }
}
