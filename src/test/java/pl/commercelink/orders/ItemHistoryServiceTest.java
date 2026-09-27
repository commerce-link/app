package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemHistoryServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String SERIAL = "SN-123";

    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private Warehouse warehouse;
    @Mock private StockQueryService stockQueryService;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private DeliveriesRepository deliveriesRepository;
    @Mock private RMARepository rmaRepository;

    @InjectMocks
    private ItemHistoryService service;

    @Test
    void aDeliveryIdThatResolvesToNoDeliveryLeavesTheDeliveryEventOutAndKeepsTheOrder() {
        // given: the item's delivery field holds a supplier name, as before a delivery is created
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        OrderItem item = itemOf(order.getOrderId(), "AcmeB");
        when(orderItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of(item));
        when(deliveriesRepository.findById(STORE_ID, "AcmeB")).thenReturn(null);
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(rmaItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of());
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);

        // when
        List<ItemHistoryEvent> history = service.getHistoryBySerial(SERIAL, STORE_ID);

        // then
        assertThat(history).extracting(ItemHistoryEvent::getSource).containsExactly("Order");
        assertThat(history.get(0).getLink()).isEqualTo("/dashboard/orders/order-1");
    }

    @Test
    void aResolvedDeliveryIsStillListedBeforeTheOrder() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        OrderItem item = itemOf(order.getOrderId(), "delivery-1");
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderedAt(LocalDateTime.of(2020, 1, 1, 10, 0));
        when(orderItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of(item));
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery);
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(rmaItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of());
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);

        // when
        List<ItemHistoryEvent> history = service.getHistoryBySerial(SERIAL, STORE_ID);

        // then
        assertThat(history).extracting(ItemHistoryEvent::getSource).containsExactly("Delivery", "Order");
    }

    @Test
    void anOrderThatNoLongerResolvesIsSkipped() {
        // given
        OrderItem item = itemOf("gone", null);
        when(orderItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of(item));
        when(ordersRepository.findById(STORE_ID, "gone")).thenReturn(null);
        when(rmaItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of());
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);

        // when
        List<ItemHistoryEvent> history = service.getHistoryBySerial(SERIAL, STORE_ID);

        // then
        assertThat(history).isEmpty();
    }

    private static OrderItem itemOf(String orderId, String deliveryId) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setSerialNo(SERIAL);
        item.setDeliveryId(deliveryId);
        return item;
    }

    @Test
    void theDeliveryComesFromAnItemOfThisStoreNotFromAnotherStoresItemWithTheSameSerial() {
        // given: the serial lookup scans every store; the first match belongs to another store
        Order order = orderWithId("order-1");
        OrderItem foreign = itemOf("foreign-order", "foreign-delivery");
        OrderItem own = itemOf("order-1", "delivery-1");
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderedAt(LocalDateTime.of(2020, 1, 1, 10, 0));
        when(orderItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of(foreign, own));
        when(ordersRepository.findById(STORE_ID, "foreign-order")).thenReturn(null);
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery);
        when(rmaItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of());
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);

        // when
        List<ItemHistoryEvent> history = service.getHistoryBySerial(SERIAL, STORE_ID);

        // then
        assertThat(history).extracting(ItemHistoryEvent::getId).containsExactly("delivery-1", "order-1");
        verify(deliveriesRepository, never()).findById(anyString(), eq("foreign-delivery"));
    }

    @Test
    void anRmaThatDoesNotResolveIsSkipped() {
        // given
        Order order = orderWithId("order-1");
        RMAItem rmaItem = new RMAItem();
        rmaItem.setRmaId("rma-gone");
        when(orderItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of(itemOf("order-1", null)));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(rmaItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of(rmaItem));
        when(rmaRepository.findById(STORE_ID, "rma-gone")).thenReturn(null);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);

        // when
        List<ItemHistoryEvent> history = service.getHistoryBySerial(SERIAL, STORE_ID);

        // then
        assertThat(history).extracting(ItemHistoryEvent::getSource).containsExactly("Order");
    }

    @Test
    void noRmaListFromTheRepositoryIsTreatedAsNoRma() {
        // given
        when(orderItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of(itemOf("order-1", null)));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(orderWithId("order-1"));
        when(rmaItemsRepository.findBySerialNo(SERIAL)).thenReturn(null);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);

        // when
        List<ItemHistoryEvent> history = service.getHistoryBySerial(SERIAL, STORE_ID);

        // then
        assertThat(history).extracting(ItemHistoryEvent::getSource).containsExactly("Order");
    }

    @Test
    void eventsWithoutADateSortAfterTheDatedOnes() {
        // given: an order with no date at all (no shipment, no estimates, no orderedAt)
        Order undated = orderWithId("order-undated");
        undated.setOrderedAt(null);
        Order dated = orderWithId("order-dated");
        dated.setOrderedAt(LocalDateTime.of(2019, 1, 1, 10, 0));
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderedAt(LocalDateTime.of(2020, 1, 1, 10, 0));
        when(orderItemsRepository.findBySerialNo(SERIAL))
                .thenReturn(List.of(itemOf("order-undated", "delivery-1"), itemOf("order-dated", null)));
        when(ordersRepository.findById(STORE_ID, "order-undated")).thenReturn(undated);
        when(ordersRepository.findById(STORE_ID, "order-dated")).thenReturn(dated);
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery);
        when(rmaItemsRepository.findBySerialNo(SERIAL)).thenReturn(List.of());
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);

        // when
        List<ItemHistoryEvent> history = service.getHistoryBySerial(SERIAL, STORE_ID);

        // then
        assertThat(history).extracting(ItemHistoryEvent::getId).containsExactly("order-dated", "delivery-1", "order-undated");
    }

    private static Order orderWithId(String orderId) {
        Order order = new Order(STORE_ID);
        order.setOrderId(orderId);
        return order;
    }
}
