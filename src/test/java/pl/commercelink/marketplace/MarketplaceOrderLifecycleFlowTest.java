package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEvent;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrderLifecycleFlowTest {

    private static final String STORE_ID = "store-1";
    private static final String EXTERNAL_ORDER_ID = "EXT-1";
    private static final String MARKETPLACE = "Empik";

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock private OrderNotificationsEventPublisher notificationEventPublisher;
    @Mock private DeliveriesRepository deliveriesRepository;
    @Mock private InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    @Mock private GoodsOutEventPublisher goodsOutEventPublisher;
    @Mock private MarketplaceProviderFactory providerFactory;

    @Mock private Store store;
    @Mock private MarketplaceProvider provider;

    @InjectMocks private OrderLifecycle orderLifecycle;
    @InjectMocks private MarketplaceOrderLifecycleEventListener listener;

    @Test
    void marketplaceReceivesAcceptBeforeCompleteWhenNewOrderSettlesInOnePass() {
        // given
        Order order = settledMarketplaceOrder();

        // when
        orderLifecycle.update(order, List.of(unprocessedItem()));
        consumePublishedEvents(order, false);

        // then
        assertEquals(OrderStatus.Completed, order.getStatus());
        InOrder callOrder = inOrder(provider);
        callOrder.verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        callOrder.verify(provider).completeOrder(EXTERNAL_ORDER_ID);
    }

    @Test
    void marketplaceAcceptsExactlyOnceEvenWhenEventsArriveOutOfOrder() {
        // given
        Order order = settledMarketplaceOrder();

        // when
        orderLifecycle.update(order, List.of(unprocessedItem()));
        consumePublishedEvents(order, true);

        // then
        InOrder callOrder = inOrder(provider);
        callOrder.verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        callOrder.verify(provider).completeOrder(EXTERNAL_ORDER_ID);
        verify(provider, times(1)).acceptOrder(EXTERNAL_ORDER_ID);
    }

    private Order settledMarketplaceOrder() {
        Order order = spy(new Order(STORE_ID));
        order.setExternalOrderId(EXTERNAL_ORDER_ID);
        order.setSource(new OrderSource(MARKETPLACE, OrderSourceType.Marketplace));
        doReturn(true).when(order).isSettled(anyBoolean());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(new MarketplaceIntegration(MARKETPLACE));
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        return order;
    }

    private OrderItem unprocessedItem() {
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(false);
        when(item.isReturned()).thenReturn(false);
        return item;
    }

    private void consumePublishedEvents(Order order, boolean reversed) {
        ArgumentCaptor<OrderLifecycleEventType> publishedTypes = ArgumentCaptor.forClass(OrderLifecycleEventType.class);
        verify(orderLifecycleEventPublisher, atLeastOnce()).publish(eq(order), publishedTypes.capture());
        List<OrderLifecycleEventType> types = new ArrayList<>(publishedTypes.getAllValues());
        if (reversed) {
            Collections.reverse(types);
        }
        types.forEach(type -> listener.handleMessage(new OrderLifecycleEvent(STORE_ID, order.getOrderId(), type)));
    }
}
