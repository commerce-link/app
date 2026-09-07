package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.ShipmentTrackingSubscriber;
import pl.commercelink.starter.util.OperationResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipDeliveryCompletionTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final LocalDateTime SHIPPED_AT = LocalDateTime.of(2026, 8, 25, 10, 30);
    private static final DropshipShipment COURIER =
            new DropshipShipment(ShipmentType.Courier, "DPD", "PKG-1", null, SHIPPED_AT);

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private ShipmentTrackingSubscriber shipmentTrackingSubscriber;

    @InjectMocks
    private DropshipDeliveryCompletion completion;

    private static Order order() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.Assembly);
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        return order;
    }

    private static OrderItem item(String itemId, String deliveryId, FulfilmentStatus status) {
        OrderItem item = new OrderItem(ORDER_ID, "Category", "Product " + itemId, 1, 100.0, null, false);
        item.setItemId(itemId);
        item.setDeliveryId(deliveryId);
        item.setStatus(status);
        item.setEan("590000000000" + itemId);
        item.setManufacturerCode("MFN-" + itemId);
        return item;
    }

    private static Delivery dropshipDelivery() {
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        return delivery;
    }

    private static Shipment marketplaceShipment() {
        Shipment shipment = new Shipment();
        shipment.setType(ShipmentType.PickupPoint);
        shipment.setCollectionPointCode("WAW04A");
        return shipment;
    }

    private static Shipment shippedParcel(String trackingNo) {
        Shipment shipment = new Shipment();
        new DropshipShipment(ShipmentType.Courier, "DPD", trackingNo, null, SHIPPED_AT).applyTo(shipment);
        return shipment;
    }

    @Test
    void confirmingEveryItemShipsTheOrderAndReceivesTheDelivery() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(selected));

        // when
        OperationResult<DropshipShipmentResult> result = completion.confirmShipped(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)), List.of(), COURIER);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPayload()).isEqualTo(DropshipShipmentResult.COMPLETED);
        assertThat(selected.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        verify(orderItemsRepository).save(selected);
        assertThat(order.getShipments()).hasSize(1);
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getCarrier()).isEqualTo("DPD");
        assertThat(shipment.getTrackingNo()).isEqualTo("PKG-1");
        assertThat(shipment.getShippedAt()).isEqualTo(SHIPPED_AT);
        assertThat(shipment.hasShippingData()).isTrue();
        verify(orderLifecycle).update(same(order), anyList());
        verify(orderLifecycleEventPublisher).publish(same(order), same(OrderLifecycleEventType.ShipmentCreated));
        assertThat(delivery.hasBeenReceived()).isTrue();
        assertThat(delivery.hasEvent("DROPSHIP_SHIPMENT_CONFIRMED")).isTrue();
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void subscribesTheOrderShipmentsToTrackingBeforeTheLifecycleUpdate() {
        // given
        Order order = order();
        Delivery delivery = dropshipDelivery();
        OrderItem item = item("item-1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        List<Allocation> selected = List.of(Allocation.fromOrderItem(order, item));

        // when
        completion.confirmShipped(STORE_ID, delivery, selected, List.of(), COURIER);

        // then
        InOrder inOrder = inOrder(shipmentTrackingSubscriber, orderLifecycle);
        inOrder.verify(shipmentTrackingSubscriber).subscribe(STORE_ID, order);
        inOrder.verify(orderLifecycle).update(same(order), anyList());
        assertThat(order.getShipments().get(0).getTrackingNo()).isEqualTo("PKG-1");
    }

    @Test
    void partialConfirmationAddsAParcelButLeavesTheDeliveryOpen() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        OrderItem remaining = item("2", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(selected, remaining));

        // when
        OperationResult<DropshipShipmentResult> result = completion.confirmShipped(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)),
                List.of(Allocation.fromOrderItem(order, remaining)), COURIER);

        // then
        assertThat(result.getPayload()).isEqualTo(DropshipShipmentResult.PARTIAL);
        assertThat(selected.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(remaining.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        verify(orderItemsRepository, never()).save(remaining);
        assertThat(order.getShipments()).hasSize(1);
        verify(orderLifecycle).update(same(order), anyList());
        verify(orderLifecycleEventPublisher).publish(same(order), same(OrderLifecycleEventType.ShipmentCreated));
        assertThat(delivery.hasBeenReceived()).isFalse();
        assertThat(delivery.hasEvent("DROPSHIP_SHIPMENT_CONFIRMED")).isTrue();
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void firstConfirmationFillsTheShipmentBroughtByTheMarketplace() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        order.addShipment(marketplaceShipment());
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(selected));
        DropshipShipment pickup = new DropshipShipment(ShipmentType.PickupPoint, "InPost", "PKG-1", "WAW04A", SHIPPED_AT);

        // when
        completion.confirmShipped(STORE_ID, delivery, List.of(Allocation.fromOrderItem(order, selected)), List.of(), pickup);

        // then
        assertThat(order.getShipments()).hasSize(1);
        assertThat(order.getShipments().get(0).getTrackingNo()).isEqualTo("PKG-1");
        assertThat(order.getShipments().get(0).getCollectionPointCode()).isEqualTo("WAW04A");
        verify(orderLifecycleEventPublisher).publish(same(order), same(OrderLifecycleEventType.ShipmentCreated));
    }

    @Test
    void secondParcelIsAddedNextToTheAlreadyShippedOne() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        order.addShipment(shippedParcel("PKG-1"));
        OrderItem selected = item("2", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(selected));
        DropshipShipment second = new DropshipShipment(ShipmentType.Courier, "DPD", "PKG-2", null, SHIPPED_AT);

        // when
        completion.confirmShipped(STORE_ID, delivery, List.of(Allocation.fromOrderItem(order, selected)), List.of(), second);

        // then
        assertThat(order.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("PKG-1", "PKG-2");
        verifyNoInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void cancelledOrderIsRejectedBeforeAnythingChanges() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        order.setStatus(OrderStatus.Cancelled);
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        OperationResult<DropshipShipmentResult> result = completion.confirmShipped(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)), List.of(), COURIER);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("deliveries.dropship.shipment.error.orderCancelled");
        assertThat(selected.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(order.getShipments()).isEmpty();
        verifyNoInteractions(orderItemsRepository, orderLifecycle, orderLifecycleEventPublisher, deliveriesRepository);
    }

    @Test
    void nothingConfirmedWhenSelectedItemsBelongElsewhereOrAreAlreadyDelivered() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem fromOtherDelivery = item("1", "other-delivery", FulfilmentStatus.Ordered);
        OrderItem alreadyDelivered = item("2", delivery.getDeliveryId(), FulfilmentStatus.Delivered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(fromOtherDelivery, alreadyDelivered));

        // when
        OperationResult<DropshipShipmentResult> result = completion.confirmShipped(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, fromOtherDelivery),
                        Allocation.fromOrderItem(order, alreadyDelivered)),
                List.of(), COURIER);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("deliveries.dropship.shipment.error.nothingToConfirm");
        assertThat(fromOtherDelivery.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(order.getShipments()).isEmpty();
        verifyNoInteractions(orderLifecycle, orderLifecycleEventPublisher, deliveriesRepository);
    }

    @Test
    void beforeSaveCallbackRunsWithResultBeforeTheDeliveryIsSaved() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(selected));
        List<DropshipShipmentResult> capturedResults = new ArrayList<>();
        AtomicBoolean saveAlreadyCalledWhenCallbackRan = new AtomicBoolean();

        // when
        OperationResult<DropshipShipmentResult> result = completion.confirmShipped(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)), List.of(), COURIER,
                (d, r) -> {
                    capturedResults.add(r);
                    saveAlreadyCalledWhenCallbackRan.set(!mockingDetails(deliveriesRepository).getInvocations().isEmpty());
                });

        // then
        assertThat(result.getPayload()).isEqualTo(DropshipShipmentResult.COMPLETED);
        assertThat(capturedResults).containsExactly(DropshipShipmentResult.COMPLETED);
        assertThat(saveAlreadyCalledWhenCallbackRan).isFalse();
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void missingOrderIsReportedAsNothingToConfirm() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        OperationResult<DropshipShipmentResult> result = completion.confirmShipped(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)), List.of(), COURIER);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("deliveries.dropship.shipment.error.nothingToConfirm");
        verifyNoInteractions(orderItemsRepository, orderLifecycle, orderLifecycleEventPublisher, deliveriesRepository);
    }
}
