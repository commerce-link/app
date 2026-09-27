package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
}
