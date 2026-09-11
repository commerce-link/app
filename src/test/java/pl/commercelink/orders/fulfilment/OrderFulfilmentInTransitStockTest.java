package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.GoodsOutEventPublisher;
import pl.commercelink.warehouse.WarehouseFulfilmentService;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.ReservationConfirmation;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the promotion to Assembly that {@link OrderFulfilment#commit} triggers via {@link OrderLifecycle},
 * including the case where the batch is fulfilled from a confirmed, in-transit delivery rather than from
 * stock already on the shelf.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderFulfilmentInTransitStockTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EAN = "5901234123457";
    private static final String MFN = "MFN-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private WarehouseFulfilmentService warehouseFulfilmentService;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private OrderNotificationsEventPublisher notificationEventPublisher;
    @Mock
    private InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    @Mock
    private GoodsOutEventPublisher goodsOutEventPublisher;
    @Mock
    private DropshipItemLookup dropshipItemLookup;

    @InjectMocks
    private OrderLifecycle orderLifecycle;

    private ManualWarehouseItemFulfilment fulfilment() {
        return new ManualWarehouseItemFulfilment(ordersRepository, orderLifecycle, orderItemsRepository, warehouseFulfilmentService);
    }

    private OrderItem inTransitItem() {
        OrderItem item = new OrderItem(ORDER_ID, Categories.UNCATEGORIZED, "Widget", 1, 199.0, MFN, false);
        // inStock=false: the reservation was confirmed against a delivery still travelling to us.
        item.copyFulfilmentFrom(new ReservationConfirmation("delivery-1", EAN, MFN, Price.fromNet(20.0), 1, false, null, ItemCondition.Sealed));
        return item;
    }

    @Test
    void ordersFulfilledFromInTransitStockReachAssemblyWithDatesDerivedFromDelivery() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setOrderRealizationDays(3);
        OrderItem item = inTransitItem();
        Delivery delivery = new Delivery(STORE_ID, "ext-1", "Acme", LocalDate.of(2026, 9, 10), 0, 0, 0, 0);

        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(warehouseFulfilmentService.run(eq(order), any())).thenReturn(List.of(item));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(dropshipItemLookup.deliveriesOf(eq(STORE_ID), anyList())).thenReturn(List.of(delivery));
        when(dropshipItemLookup.isEntirelyDropship(anyList())).thenReturn(false);

        // when
        fulfilment().commit(STORE_ID, List.of(item));

        // then
        assertEquals(OrderStatus.Assembly, order.getStatus());
        assertEquals(LocalDate.of(2026, 9, 10), order.getEstimatedAssemblyAt());
        // 2026-09-10 is a Thursday: +3 working days lands on Tuesday 2026-09-15 (weekend skipped).
        assertEquals(LocalDate.of(2026, 9, 15), order.getEstimatedShippingAt());
    }

    @Test
    void directToConsumerOrderFulfilledFromInTransitWarehouseStockKeepsRealizationDays() {
        // given: the delivery is a WAREHOUSE delivery (the default Delivery type), not a dropship one, so
        // even on a DirectToConsumer order these goods still pass through our warehouse and have to be
        // picked, packed and forwarded by hand - the realization days must not be skipped.
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        order.setOrderRealizationDays(3);
        OrderItem item = inTransitItem();
        Delivery delivery = new Delivery(STORE_ID, "ext-1", "Acme", LocalDate.of(2026, 9, 10), 0, 0, 0, 0);

        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(warehouseFulfilmentService.run(eq(order), any())).thenReturn(List.of(item));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(dropshipItemLookup.deliveriesOf(eq(STORE_ID), anyList())).thenReturn(List.of(delivery));
        when(dropshipItemLookup.isEntirelyDropship(anyList())).thenReturn(false);

        // when
        fulfilment().commit(STORE_ID, List.of(item));

        // then
        assertEquals(OrderStatus.Assembly, order.getStatus());
        assertEquals(LocalDate.of(2026, 9, 10), order.getEstimatedAssemblyAt());
        // 2026-09-10 is a Thursday: +3 working days lands on Tuesday 2026-09-15 (weekend skipped). The
        // shipping date must NOT equal the assembly date - that would mean the warehouse handling step
        // was skipped because the order happens to be DirectToConsumer.
        assertEquals(LocalDate.of(2026, 9, 15), order.getEstimatedShippingAt());
        assertNotEquals(order.getEstimatedAssemblyAt(), order.getEstimatedShippingAt());
    }

    @Test
    void aPartiallyFulfilledOrderIsNotPromotedAndStaysNew() {
        // given: the batch committed here is fully fulfilled (Ordered from in-transit stock), so the
        // widened OrderFulfilment guard passes and OrderLifecycle.update(order) IS invoked. commit() never
        // passes it any items, so update() re-reads ALL of the order's items from the repository - including
        // one this batch never touched, that is still New with no warehouse candidate. The lifecycle's own
        // allMatch(isOrdered) over that full set is what must keep the order from being promoted.
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        OrderItem batchItem = inTransitItem();
        OrderItem stillUnfulfilled = new OrderItem(ORDER_ID, Categories.UNCATEGORIZED, "Gadget", 1, 99.0, "MFN-2", false);

        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(warehouseFulfilmentService.run(eq(order), any())).thenReturn(List.of(batchItem));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(batchItem, stillUnfulfilled));

        // when
        fulfilment().commit(STORE_ID, List.of(batchItem));

        // then
        verify(orderItemsRepository).findByOrderId(ORDER_ID);
        assertEquals(OrderStatus.New, order.getStatus());
    }

    @Test
    void ordersFulfilledEntirelyFromInStockItemsStillReachAssembled() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        OrderItem item = new OrderItem(ORDER_ID, Categories.UNCATEGORIZED, "Widget", 1, 199.0, MFN, false);
        // inStock=true: the reservation was confirmed against stock already on the shelf.
        item.copyFulfilmentFrom(new ReservationConfirmation("delivery-1", EAN, MFN, Price.fromNet(20.0), 1, true, null, ItemCondition.Sealed));

        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(warehouseFulfilmentService.run(eq(order), any())).thenReturn(List.of(item));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(dropshipItemLookup.isEntirelyDropship(eq(STORE_ID), anyList())).thenReturn(false);

        // when
        fulfilment().commit(STORE_ID, List.of(item));

        // then
        assertEquals(OrderStatus.Assembled, order.getStatus());
        assertEquals(LocalDate.now(), order.getEstimatedAssemblyAt());
    }
}
