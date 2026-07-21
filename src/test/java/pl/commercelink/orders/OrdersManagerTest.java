package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.fulfilment.AutomatedOrderFulfilment;
import pl.commercelink.orders.fulfilment.OrderFulfilmentEventPublisher;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.stores.Store;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationService;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersManagerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private Warehouse warehouse;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrderFulfilmentEventPublisher orderFulfilmentEventPublisher;
    @Mock
    private AutomatedOrderFulfilment automatedOrderFulfilment;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private Store store;
    @Mock
    private MatchedInventory matchedInventory;
    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private OrdersManager ordersManager;

    @Test
    @DisplayName("addOrderItem from matched inventory with offers persists item with taxonomy data and increments order total price")
    void addOrderItemFromMatchedInventoryWithOffersIncrementsOrderTotalsAndPersistsItem() {
        // given
        Order order = orderWithTotalPrice(0.0);
        Taxonomy taxonomy = new Taxonomy("EAN-1", "MFN-1", "TestBrand", "test-product", "Laptops", 1, null, null);
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getTaxonomy()).thenReturn(taxonomy);
        when(matchedInventory.getMedianPrice()).thenReturn(Price.fromGross(150.0));
        when(matchedInventory.getEstimatedDeliveryDays()).thenReturn(3);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, matchedInventory, 0);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getName()).isEqualTo("test-product");
        assertThat(itemCaptor.getValue().getCategory()).isEqualTo("Laptops");
        assertThat(itemCaptor.getValue().getPrice()).isEqualTo(150.0);
        assertThat(itemCaptor.getValue().getSku()).isEqualTo("MFN-1");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalPrice()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("addOrderItem from matched inventory with no offers falls back to Other category and uses MFN from inventory key")
    void addOrderItemFromMatchedInventoryWithoutOffersFallsBackToOtherCategory() {
        // given
        Order order = orderWithTotalPrice(0.0);
        InventoryKey key = new InventoryKey("EAN-Z", "MFN-MISSING");
        when(matchedInventory.hasAnyOffers()).thenReturn(false);
        when(matchedInventory.getInventoryKey()).thenReturn(key);
        when(matchedInventory.getEstimatedDeliveryDays()).thenReturn(7);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, matchedInventory, 0);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCategory()).isEqualTo("Other");
        assertThat(itemCaptor.getValue().getName()).isEmpty();
        assertThat(itemCaptor.getValue().getPrice()).isEqualTo(0);
        assertThat(itemCaptor.getValue().getSku()).isEqualTo("MFN-MISSING");
    }

    @Test
    @DisplayName("addOrderItem from matched inventory treats a legacy Services category string as a regular product")
    void addOrderItemFromMatchedInventoryTreatsLegacyServicesCategoryAsRegularProduct() {
        // given
        Order order = orderWithTotalPrice(0.0);
        Taxonomy taxonomy = new Taxonomy("EAN-S", "MFN-S", "TestBrand", "assembly-service", "Services", 1, null, null);
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getTaxonomy()).thenReturn(taxonomy);
        when(matchedInventory.getMedianPrice()).thenReturn(Price.fromGross(30.0));
        when(matchedInventory.getEstimatedDeliveryDays()).thenReturn(0);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, matchedInventory, 3);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.isService()).isFalse();
        assertThat(savedItem.getPosition()).isEqualTo(3);
        assertThat(savedItem.getDeliveryId()).isNull();
        assertThat(savedItem.getStatus()).isEqualTo(FulfilmentStatus.New);
    }

    @Test
    @DisplayName("addOrderItem from availability and price persists item with availability data and increments order total price")
    void addOrderItemFromAvailabilityAndPriceIncrementsOrderTotalsAndPersistsItem() {
        // given
        Order order = orderWithTotalPrice(50.0);
        AvailabilityAndPrice availability = new AvailabilityAndPrice(
                "pim-1", "EAN-2", "MFN-2", "Brand", "Label", "product-name",
                "Laptops", 200L, 10L, 5, 0L, false);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, availability, 0);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getName()).isEqualTo("product-name");
        assertThat(itemCaptor.getValue().getCategory()).isEqualTo("Laptops");
        assertThat(itemCaptor.getValue().getPrice()).isEqualTo(200.0);
        assertThat(itemCaptor.getValue().getSku()).isEqualTo("MFN-2");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalPrice()).isEqualTo(250.0);
    }

    @Test
    @DisplayName("addOrderItem from availability and price puts a service-flagged row into the service band")
    void addOrderItemFromServiceFlaggedRowGoesToServiceBand() {
        // given
        Order order = orderWithTotalPrice(0.0);
        AvailabilityAndPrice availability = new AvailabilityAndPrice(
                "pim-montaz", "", "MONTAZ-1", "", "", "Montaż PC",
                "Usługi dodatkowe", 30L, 1L, 1, 0L, true);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, availability, 3);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.isService()).isTrue();
        assertThat(savedItem.getPosition()).isEqualTo(PositionGroup.SERVICE_GROUP_START + 3);
        assertThat(savedItem.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        assertThat(savedItem.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
    }

    @Test
    @DisplayName("addOrderItem from availability and price treats a row without the service flag as a regular product")
    void addOrderItemFromUnflaggedRowIsARegularProductEvenWithServiceLikeCategory() {
        // given
        Order order = orderWithTotalPrice(0.0);
        AvailabilityAndPrice availability = new AvailabilityAndPrice(
                "pim-shipping", "", "Shipping", "", "", "Delivery courier",
                "Services", 30L, 1L, 1, 0L, false);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, availability, 3);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.isService()).isFalse();
        assertThat(savedItem.getPosition()).isEqualTo(3);
        assertThat(savedItem.getDeliveryId()).isNull();
        assertThat(savedItem.getStatus()).isEqualTo(FulfilmentStatus.New);
    }

    @Test
    @DisplayName("addOrderItem stores the provided position and never scans existing items")
    void addOrderItemStoresProvidedPositionWithoutScanningExistingItems() {
        // given
        Order order = orderWithTotalPrice(0.0);
        AvailabilityAndPrice availability = new AvailabilityAndPrice(
                "pim-1", "EAN-2", "MFN-2", "Brand", "Label", "product-name",
                "Laptops", 200L, 10L, 5, 0L, false);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, availability, 4);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getPosition()).isEqualTo(4);
        verify(orderItemsRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("cancelOrder zeroes service prices, recalculates totalPrice, sets Cancelled and publishes OrderCancelled")
    void cancelOrderZeroesServicesAndSetsCancelled() {
        // given
        Order order = deliveredOrder(150.0);
        OrderItem product = returnedProduct("item-1", 100.0);
        OrderItem service = serviceItem("item-2", 50.0);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(product, service));

        // when
        ordersManager.cancelOrder(STORE_ID, ORDER_ID);

        // then
        assertThat(service.getPrice()).isEqualTo(0.0);
        assertThat(service.isReturned()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Cancelled);
        assertThat(order.getTotalPrice()).isEqualTo(0.0);
        verify(orderItemsRepository).batchSave(List.of(product, service));
        verify(ordersRepository).save(order);
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCancelled);
    }

    @Test
    @DisplayName("cancelOrder throws when not all non-service items are returned")
    void cancelOrderThrowsWhenItemsNotReturned() {
        // given
        Order order = deliveredOrder(150.0);
        OrderItem product = orderItem("item-1", 100.0);
        OrderItem service = serviceItem("item-2", 50.0);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(product, service));

        // when / then
        assertThatThrownBy(() -> ordersManager.cancelOrder(STORE_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancelOrder throws when payments are not settled")
    void cancelOrderThrowsWhenPaymentsNotSettled() {
        // given
        Order order = deliveredOrder(150.0);
        Payment incoming = new Payment(PaymentSource.BankTransfer);
        incoming.setAmount(150.0);
        order.addPayment(incoming);
        OrderItem product = returnedProduct("item-1", 100.0);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(product));

        // when / then
        assertThatThrownBy(() -> ordersManager.cancelOrder(STORE_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deleteOrder publishes OrderCancelled then deletes for a marketplace order")
    void deleteOrderPublishesCancelForMarketplaceOrder() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setSource(new OrderSource("Empik", OrderSourceType.Marketplace));
        order.setExternalOrderId("EXT-1");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        // when
        ordersManager.deleteOrder(STORE_ID, ORDER_ID);

        // then
        InOrder inOrder = inOrder(orderLifecycleEventPublisher, ordersRepository);
        inOrder.verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCancelled);
        inOrder.verify(ordersRepository).delete(order);
    }

    @Test
    @DisplayName("deleteOrder does not delete when publishing OrderCancelled fails")
    void deleteOrderDoesNotDeleteWhenPublishFails() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setSource(new OrderSource("Empik", OrderSourceType.Marketplace));
        order.setExternalOrderId("EXT-1");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        doThrow(new RuntimeException("SQS unavailable"))
                .when(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCancelled);

        // when / then
        assertThatThrownBy(() -> ordersManager.deleteOrder(STORE_ID, ORDER_ID))
                .isInstanceOf(RuntimeException.class);
        verify(ordersRepository, never()).delete(order);
    }

    @Test
    @DisplayName("deleteOrder deletes a non-marketplace order without publishing")
    void deleteOrderDoesNotPublishForNonMarketplaceOrder() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        // when
        ordersManager.deleteOrder(STORE_ID, ORDER_ID);

        // then
        verify(ordersRepository).delete(order);
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("a Delivered service item can be removed from the order")
    void serviceItemCanBeRemovedEvenWhenDelivered() {
        // given
        Order order = orderWithTotalPrice(50.0);
        OrderItem service = deliveredWarehouseService("item-1");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(new ArrayList<>(List.of(service)));

        // when
        ordersManager.removeFromOrder(STORE_ID, ORDER_ID, List.of(service.getItemId()));

        // then
        verify(orderItemsRepository).delete(service);
        assertThat(order.getTotalPrice()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a Delivered product item is NOT removable")
    void deliveredProductItemIsNotRemovable() {
        // given
        Order order = orderWithTotalPrice(100.0);
        OrderItem product = allocatedProduct("item-1");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(product));

        // when
        ordersManager.removeFromOrder(STORE_ID, ORDER_ID, List.of(product.getItemId()));

        // then
        verify(orderItemsRepository, never()).delete(any(OrderItem.class));
        assertThat(order.getTotalPrice()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("splitGroupItem refuses to split a service item regardless of status")
    void splitGroupItemIgnoresServiceItems() {
        // given
        OrderItem source = new OrderItem(ORDER_ID, "Usługi dodatkowe", "Pakiet montażowy", 1, 100.0, "MONTAZ-A+MONTAZ-B", false);
        source.setService(true);
        when(orderItemsRepository.findById(ORDER_ID, source.getItemId())).thenReturn(source);

        // when / then
        assertThatThrownBy(() -> ordersManager.splitGroupItem(ORDER_ID, source.getItemId(), List.of(
                new SplitGroupComponent("MONTAZ-A", "Montaż A", 1, 60.0),
                new SplitGroupComponent("MONTAZ-B", "Montaż B", 1, 40.0))))
                .isInstanceOf(IllegalStateException.class);
        verify(orderItemsRepository, never()).save(any(OrderItem.class));
        verify(orderItemsRepository, never()).delete(any(OrderItem.class));
    }

    @Test
    @DisplayName("moveOrderItemsToTheWarehouse skips service items entirely")
    void moveToWarehouseSkipsServiceItems() {
        // given
        Order order = orderWithTotalPrice(150.0);
        OrderItem product = allocatedProduct("item-1");
        OrderItem service = deliveredWarehouseService("item-2");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(product, service));
        when(warehouse.reservationService(STORE_ID)).thenReturn(reservationService);

        // when
        ordersManager.moveOrderItemsToTheWarehouse(STORE_ID, ORDER_ID, List.of(product.getItemId(), service.getItemId()));

        // then
        verify(reservationService, times(1)).remove(any(Reservation.class));
        verify(orderItemsRepository).save(product);
        verify(orderItemsRepository, never()).save(service);
        assertThat(product.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(product.getEan()).isNull();
        assertThat(product.getDeliveryId()).isNull();
        assertThat(service.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(service.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
    }

    @Test
    @DisplayName("moveOrderItemsToTheWarehouseForRMA skips service items entirely")
    void moveToWarehouseForRmaSkipsServiceItems() {
        // given
        Order order = orderWithTotalPrice(150.0);
        OrderItem product = allocatedProduct("item-1");
        OrderItem service = deliveredWarehouseService("item-2");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(product, service));
        when(warehouse.reservationService(STORE_ID)).thenReturn(reservationService);

        // when
        ordersManager.moveOrderItemsToTheWarehouseForRMA(STORE_ID, ORDER_ID, List.of(product.getItemId(), service.getItemId()));

        // then
        verify(reservationService, times(1)).remove(any(Reservation.class));
        verify(orderItemsRepository).save(product);
        verify(orderItemsRepository, never()).save(service);
        assertThat(product.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(product.getEan()).isNull();
        assertThat(product.getDeliveryId()).isNull();
        assertThat(service.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(service.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
    }

    private Order orderWithTotalPrice(double totalPrice) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setTotalPrice(totalPrice);
        return order;
    }

    private Order deliveredOrder(double totalPrice) {
        Order order = orderWithTotalPrice(totalPrice);
        order.setStatus(OrderStatus.Delivered);
        order.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));
        return order;
    }

    private OrderItem orderItem(String itemId, double price) {
        OrderItem item = new OrderItem(ORDER_ID, "Other", "product", 1, price, "SKU-" + itemId, false);
        item.setItemId(itemId);
        return item;
    }

    private OrderItem returnedProduct(String itemId, double price) {
        OrderItem item = orderItem(itemId, price);
        item.markAsReturned();
        return item;
    }

    private OrderItem serviceItem(String itemId, double price) {
        OrderItem item = new OrderItem(ORDER_ID, "Usługi dodatkowe", "service", 1, price, null, false);
        item.setService(true);
        item.setItemId(itemId);
        return item;
    }

    private OrderItem allocatedProduct(String itemId) {
        OrderItem item = orderItem(itemId, 100.0);
        item.setEan("1111111111111");
        item.setManufacturerCode("MFN-" + itemId);
        item.setDeliveryId("Supplier-1");
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }

    private OrderItem deliveredWarehouseService(String itemId) {
        OrderItem item = serviceItem(itemId, 50.0);
        item.markAsWarehouseFulfilled();
        return item;
    }
}
