package pl.commercelink.orders.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMARepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemHistoryServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String SERIAL = "SN-123";

    @Mock private SerialNumberLookup lookup;
    @Mock private OrdersRepository ordersRepository;
    @Mock private DeliveriesRepository deliveriesRepository;
    @Mock private RMARepository rmaRepository;
    @InjectMocks private ItemHistoryService service;

    @Test
    void listsDeliveriesOrdersAndRmasNewestFirstWithEachDeliveryOnce() {
        // given: sold, returned, sold again from the warehouse (same delivery id on both items)
        Order first = order("o-1", OrderStatus.Completed, at(9, 2));
        Order second = order("o-2", OrderStatus.Assembly, at(9, 29));
        OrderItem soldItem = item("o-1", "d-1", FulfilmentStatus.Returned);
        OrderItem resoldItem = item("o-2", "d-1", FulfilmentStatus.Reserved);
        RMA rma = rma("rma-1", at(9, 21));
        RMAItem rmaItem = rmaItem("rma-1", RMAItemStatus.MovedToWarehouse);
        Delivery delivery = delivery("d-1", at(8, 25), at(8, 28));
        givenMatches(List.of(soldItem, resoldItem), List.of(rmaItem));
        when(ordersRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of(first, second));
        when(rmaRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of(rma));
        when(deliveriesRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of(delivery));

        // when
        ItemHistory history = service.history(STORE_ID, SERIAL);

        // then
        assertThat(history.events()).extracting(ItemHistoryEvent::type).containsExactly(
                ItemHistoryEvent.Type.ORDER_PLACED, ItemHistoryEvent.Type.RMA_CREATED, ItemHistoryEvent.Type.ORDER_PLACED,
                ItemHistoryEvent.Type.DELIVERY_RECEIVED, ItemHistoryEvent.Type.DELIVERY_ORDERED);
        assertThat(history.now().state()).isEqualTo(ItemNow.State.IN_ORDER);
        assertThat(history.found()).isTrue();
        assertThat(history.ambiguity().ambiguous()).isFalse();
    }

    @Test
    void dropsRecordsOfOtherStoresAndDeliveryIdsThatAreNotDeliveries() {
        // given: o-foreign is another store's order with the same number; "AcmeB" is a supplier name, not a delivery
        givenMatches(List.of(item("o-1", "AcmeB", FulfilmentStatus.Delivered), item("o-foreign", "d-9", FulfilmentStatus.Delivered)),
                List.of(rmaItem("rma-gone", RMAItemStatus.New)));
        when(ordersRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of(order("o-1", OrderStatus.Completed, at(9, 2))));
        when(rmaRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of());
        when(deliveriesRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of());

        // when
        ItemHistory history = service.history(STORE_ID, SERIAL);

        // then
        assertThat(history.events()).extracting(ItemHistoryEvent::type).containsExactly(ItemHistoryEvent.Type.ORDER_PLACED);
        assertThat(history.ambiguity().orderCount()).isEqualTo(1);
    }

    @Test
    void anOrderWithoutAnyDateFallsBackToItsLastEventAndUndatedEventsGoLast() {
        // given
        Order undated = order("o-1", OrderStatus.New, null);
        Delivery delivery = delivery("d-1", null, null);
        givenMatches(List.of(item("o-1", "d-1", FulfilmentStatus.Allocation)), List.of(rmaItem("rma-1", null)));
        when(ordersRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of(undated));
        when(rmaRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of(rma("rma-1", at(9, 1))));
        when(deliveriesRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of(delivery));

        // when
        ItemHistory history = service.history(STORE_ID, SERIAL);

        // then
        assertThat(history.events().get(0).type()).isEqualTo(ItemHistoryEvent.Type.RMA_CREATED);
        assertThat(history.events()).extracting(ItemHistoryEvent::type)
                .containsExactly(ItemHistoryEvent.Type.RMA_CREATED, ItemHistoryEvent.Type.ORDER_PLACED, ItemHistoryEvent.Type.DELIVERY_ORDERED);
    }

    @Test
    void keepsTheNewestHundredEventsAndCountsThemAll() {
        // given
        List<OrderItem> items = new ArrayList<>();
        List<Order> orders = new ArrayList<>();
        IntStream.range(0, 130).forEach(i -> {
            items.add(item("o-" + i, null, FulfilmentStatus.Delivered));
            orders.add(order("o-" + i, OrderStatus.Completed, LocalDateTime.of(2026, 1, 1, 0, 0).plusHours(i)));
        });
        givenMatches(items, List.of());
        when(ordersRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(orders);
        when(rmaRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of());
        when(deliveriesRepository.findAllByIds(eq(STORE_ID), anyCollection())).thenReturn(List.of());

        // when
        ItemHistory history = service.history(STORE_ID, SERIAL);

        // then
        assertThat(history.events()).hasSize(ItemHistory.EVENT_LIMIT);
        assertThat(history.totalEvents()).isEqualTo(130);
        assertThat(history.events().get(0).orderLine().order().getOrderId()).isEqualTo("o-129");
    }

    @Test
    void nothingFoundIsNotFound() {
        // given
        givenMatches(List.of(), List.of());

        // when
        ItemHistory history = service.history(STORE_ID, SERIAL);

        // then
        assertThat(history.found()).isFalse();
        assertThat(history.events()).isEmpty();
    }

    private void givenMatches(List<OrderItem> orderItems, List<RMAItem> rmaItems) {
        when(lookup.find(STORE_ID, SERIAL)).thenReturn(new SerialNumberMatches(orderItems, rmaItems, List.of()));
    }

    static LocalDateTime at(int month, int day) {
        return LocalDateTime.of(2026, month, day, 12, 0);
    }

    static Order order(String id, OrderStatus status, LocalDateTime orderedAt) {
        Order order = new Order();
        order.setStoreId(STORE_ID);
        order.setOrderId(id);
        order.setStatus(status);
        order.setOrderedAt(orderedAt);
        return order;
    }

    static OrderItem item(String orderId, String deliveryId, FulfilmentStatus status) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setSerialNo(SERIAL);
        item.setDeliveryId(deliveryId);
        item.setStatus(status);
        return item;
    }

    static RMA rma(String id, LocalDateTime createdAt) {
        RMA rma = new RMA();
        rma.setStoreId(STORE_ID);
        rma.setRmaId(id);
        rma.setCreatedAt(createdAt);
        return rma;
    }

    static RMAItem rmaItem(String rmaId, RMAItemStatus status) {
        RMAItem item = new RMAItem();
        item.setRmaId(rmaId);
        item.setSerialNo(SERIAL);
        item.setStatus(status);
        return item;
    }

    static Delivery delivery(String id, LocalDateTime orderedAt, LocalDateTime receivedAt) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(id);
        delivery.setProvider("Acme");
        delivery.setOrderedAt(orderedAt);
        delivery.setReceivedAt(receivedAt);
        return delivery;
    }
}
