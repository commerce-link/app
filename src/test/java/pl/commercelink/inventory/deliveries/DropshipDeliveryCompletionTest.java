package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipDeliveryCompletionTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrderLifecycle orderLifecycle;

    @InjectMocks
    private DropshipDeliveryCompletion completion;

    private static Order order() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
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

    @Test
    void confirmsSelectedOrderedItemsAndReceivesTheDeliveryWhenNothingRemains() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(selected));

        // when
        completion.confirmDelivered(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)), List.of());

        // then
        assertThat(selected.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        verify(orderItemsRepository).save(selected);
        verify(orderLifecycle).update(same(order), anyList());
        assertThat(delivery.hasBeenReceived()).isTrue();
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void leavesTheDeliveryOpenWhileUnconfirmedAllocationsRemain() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        OrderItem remaining = item("2", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(selected, remaining));

        // when
        completion.confirmDelivered(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)),
                List.of(Allocation.fromOrderItem(order, remaining)));

        // then
        assertThat(selected.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(remaining.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        verify(orderItemsRepository, never()).save(remaining);
        assertThat(delivery.hasBeenReceived()).isFalse();
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void ignoresItemsClaimedByAnotherDeliveryOrAlreadyDelivered() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem fromOtherDelivery = item("1", "other-delivery", FulfilmentStatus.Ordered);
        OrderItem alreadyDelivered = item("2", delivery.getDeliveryId(), FulfilmentStatus.Delivered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(fromOtherDelivery, alreadyDelivered));

        // when
        completion.confirmDelivered(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, fromOtherDelivery),
                        Allocation.fromOrderItem(order, alreadyDelivered)),
                List.of());

        // then
        assertThat(fromOtherDelivery.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        verify(orderItemsRepository, never()).save(fromOtherDelivery);
        verify(orderItemsRepository, never()).save(alreadyDelivered);
        verify(orderLifecycle).update(same(order), anyList());
    }

    @Test
    void skipsAnOrderThatIsGoneButStillSavesTheDelivery() {
        // given
        Delivery delivery = dropshipDelivery();
        Order order = order();
        OrderItem selected = item("1", delivery.getDeliveryId(), FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        completion.confirmDelivered(STORE_ID, delivery,
                List.of(Allocation.fromOrderItem(order, selected)), List.of());

        // then
        verifyNoInteractions(orderItemsRepository, orderLifecycle);
        verify(deliveriesRepository).save(delivery);
    }
}
