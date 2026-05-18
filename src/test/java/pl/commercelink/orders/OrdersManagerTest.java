package pl.commercelink.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.fulfilment.OrderFulfilmentEventPublisher;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.warehouse.api.Warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
    private OrderLifecycle orderLifecycle;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private Store store;
    @Mock
    private MatchedInventory matchedInventory;

    @InjectMocks
    private OrdersManager ordersManager;

    @BeforeEach
    void setupExecutorPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
    }

    @Test
    @DisplayName("addOrderItem from matched inventory with offers persists item with taxonomy data and increments order total price")
    void addOrderItemFromMatchedInventoryWithOffersIncrementsOrderTotalsAndPersistsItem() {
        // given
        Order order = orderWithTotalPrice(0.0);
        Taxonomy taxonomy = new Taxonomy("EAN-1", "MFN-1", "TestBrand", "test-product", ProductCategory.Laptops, 1);
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getTaxonomy()).thenReturn(taxonomy);
        when(matchedInventory.getMedianPrice()).thenReturn(Price.fromGross(150.0));
        when(matchedInventory.getEstimatedDeliveryDays()).thenReturn(3);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, matchedInventory);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getName()).isEqualTo("test-product");
        assertThat(itemCaptor.getValue().getCategory()).isEqualTo(ProductCategory.Laptops);
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
        ordersManager.addOrderItem(store, order, matchedInventory);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCategory()).isEqualTo(ProductCategory.Other);
        assertThat(itemCaptor.getValue().getName()).isEmpty();
        assertThat(itemCaptor.getValue().getPrice()).isEqualTo(0);
        assertThat(itemCaptor.getValue().getSku()).isEqualTo("MFN-MISSING");
    }

    @Test
    @DisplayName("addOrderItem from availability and price persists item with availability data and increments order total price")
    void addOrderItemFromAvailabilityAndPriceIncrementsOrderTotalsAndPersistsItem() {
        // given
        Order order = orderWithTotalPrice(50.0);
        AvailabilityAndPrice availability = new AvailabilityAndPrice(
                "pim-1", "EAN-2", "MFN-2", "Brand", "Label", "product-name",
                ProductCategory.Laptops, 200L, 10L, 5, 0L);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, availability);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getName()).isEqualTo("product-name");
        assertThat(itemCaptor.getValue().getCategory()).isEqualTo(ProductCategory.Laptops);
        assertThat(itemCaptor.getValue().getPrice()).isEqualTo(200.0);
        assertThat(itemCaptor.getValue().getSku()).isEqualTo("MFN-2");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalPrice()).isEqualTo(250.0);
    }

    @Test
    @DisplayName("addOrderItem from availability and price marks Services category item as warehouse-fulfilled")
    void addOrderItemFromAvailabilityAndPriceMarksServiceItemAsWarehouseFulfilled() {
        // given
        Order order = orderWithTotalPrice(0.0);
        AvailabilityAndPrice availability = new AvailabilityAndPrice(
                "pim-shipping", "", "Shipping", "", "", "Delivery courier",
                ProductCategory.Services, 30L, 1L, 1, 0L);
        when(store.isPositionConsolidationEnabled()).thenReturn(false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ordersManager.addOrderItem(store, order, availability);

        // then
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemsRepository).save(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getCategory()).isEqualTo(ProductCategory.Services);
        assertThat(savedItem.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        assertThat(savedItem.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
    }

    private Order orderWithTotalPrice(double totalPrice) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setTotalPrice(totalPrice);
        return order;
    }
}
