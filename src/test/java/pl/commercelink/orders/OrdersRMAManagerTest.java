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
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RmaGoodsInService;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersRMAManagerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String RMA_ID = "rma-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private RmaGoodsInService rmaGoodsInService;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private OrderItemFamily orderItemFamily;

    @InjectMocks
    private OrdersRMAManager ordersRMAManager;

    @BeforeEach
    void setupExecutorPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
    }

    @Test
    @DisplayName("acceptReturn decreases order total price by sum of returned item totals and reopens order")
    void acceptReturnDecreasesOrderTotalPriceByAggregatedItemPrices() {
        // given
        Order order = orderWithTotalPrice(300.0, OrderStatus.Completed);
        OrderItem item1 = orderItem("item-1", 100.0, 1);
        OrderItem item2 = orderItem("item-2", 100.0, 1);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item1, item2));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(false), any()))
                .thenReturn(OperationResult.success());

        // when
        ordersRMAManager.acceptReturn(STORE_ID, rma(), List.of(rmaItemFor(item1), rmaItemFor(item2)), ItemCondition.Sealed);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalPrice()).isEqualTo(100.0);
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.Delivered);
        assertThat(item1.isReturned()).isTrue();
        assertThat(item2.isReturned()).isTrue();
        verify(orderItemsRepository).batchSave(List.of(item1, item2));
    }

    @Test
    @DisplayName("acceptReturn adds RMA document to order when goods-in service returns a document")
    void acceptReturnAddsRmaDocumentToOrderWhenOperationProducesPayload() {
        // given
        Order order = orderWithTotalPrice(100.0, OrderStatus.Completed);
        OrderItem item1 = orderItem("item-1", 100.0, 1);
        Document goodsInDocument = new Document("doc-1", "GI/1/2026", "https://example.com/gi/1", DocumentType.GoodsReceipt);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item1));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(false), any()))
                .thenReturn(OperationResult.success(goodsInDocument));

        // when
        ordersRMAManager.acceptReturn(STORE_ID, rma(), List.of(rmaItemFor(item1)), ItemCondition.Sealed);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getDocuments()).contains(goodsInDocument);
    }

    @Test
    @DisplayName("acceptReturn persists nothing when goods-in service operation fails")
    void acceptReturnDoesNothingWhenRmaGoodsInServiceFails() {
        // given
        Order order = orderWithTotalPrice(100.0, OrderStatus.Completed);
        OrderItem item1 = orderItem("item-1", 100.0, 1);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item1));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(false), any()))
                .thenReturn(OperationResult.failure("warehouse unavailable"));

        // when
        OperationResult<Document> result = ordersRMAManager.acceptReturn(STORE_ID, rma(), List.of(rmaItemFor(item1)), ItemCondition.Sealed);

        // then
        assertThat(result.isSuccess()).isFalse();
        verify(ordersRepository, never()).save(any());
        verify(orderItemsRepository, never()).batchSave(any());
    }

    @Test
    @DisplayName("createReplacementOrder saves new replacement order with items and triggers order lifecycle")
    void createReplacementOrderSavesReplacementOrderAndTriggersLifecycle() {
        // given
        Order order = orderWithTotalPrice(100.0, OrderStatus.Delivered);
        OrderItem item1 = orderItem("item-1", 100.0, 1);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item1));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(true), any()))
                .thenReturn(OperationResult.success());

        // when
        ordersRMAManager.createReplacementOrder(STORE_ID, rma(), List.of(rmaItemFor(item1)), true, ItemCondition.Damaged);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository, org.mockito.Mockito.atLeast(1)).save(orderCaptor.capture());
        Order replacementOrder = orderCaptor.getAllValues().stream()
                .filter(o -> !ORDER_ID.equals(o.getOrderId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a new replacement order to be saved"));
        verify(orderLifecycle).update(replacementOrder);
        assertThat(item1.getStatus()).isEqualTo(FulfilmentStatus.Replaced);
    }

    @Test
    @DisplayName("createReplacementOrder keeps the service flag on replacement items")
    void createReplacementOrderKeepsServiceFlagOnReplacementItems() {
        // given
        Order order = orderWithTotalPrice(100.0, OrderStatus.Delivered);
        OrderItem serviceItem = orderItem("item-1", 100.0, 1);
        serviceItem.setCategory("Usługi dodatkowe");
        serviceItem.setService(true);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(serviceItem));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(true), any()))
                .thenReturn(OperationResult.success());

        // when
        ordersRMAManager.createReplacementOrder(STORE_ID, rma(), List.of(rmaItemFor(serviceItem)), true, ItemCondition.Damaged);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderItemsRepository, org.mockito.Mockito.atLeast(1)).batchSave(itemsCaptor.capture());
        OrderItem replacement = itemsCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .filter(i -> !ORDER_ID.equals(i.getOrderId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected replacement item to be saved"));
        assertThat(replacement.isService()).isTrue();
    }

    @Test
    void acceptReturnResolvesAnRmaItemThatLivesOnASplitOffOrder() {
        // given: the importer matched the return against an item moved to a child order (OrderItemFamily)
        Order order = orderWithTotalPrice(100.0, OrderStatus.Completed);
        OrderItem moved = new OrderItem("order-2", "Other", "test-item", 1, 100.0, "SKU-moved", false);
        moved.setItemId("item-moved");
        Order child = orderWithTotalPrice(100.0, OrderStatus.Completed);
        child.setOrderId("order-2");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(child);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(orderItemFamily.siblingItems(order)).thenReturn(List.of(moved));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(false), any()))
                .thenReturn(OperationResult.success());

        // when
        OperationResult<Document> result = ordersRMAManager.acceptReturn(STORE_ID, rma(), List.of(rmaItemFor(moved)), ItemCondition.Sealed);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(moved.isReturned()).isTrue();
        verify(orderItemsRepository).batchSave(List.of(moved));
    }

    @Test
    void acceptReturnDoesNotReadTheSplitFamilyWhenEveryRmaItemIsOnTheOrderItself() {
        // given
        Order order = orderWithTotalPrice(100.0, OrderStatus.Completed);
        OrderItem item1 = orderItem("item-1", 100.0, 1);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item1));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(false), any()))
                .thenReturn(OperationResult.success());

        // when
        ordersRMAManager.acceptReturn(STORE_ID, rma(), List.of(rmaItemFor(item1)), ItemCondition.Sealed);

        // then
        verify(orderItemFamily, never()).siblingItems(any());
    }

    @Test
    void acceptReturnAdjustsTheChildOrderWhenTheReturnedItemLivesOnIt() {
        // given
        Order order = orderWithTotalPrice(100.0, OrderStatus.Completed);
        OrderItem item1 = orderItem("item-1", 100.0, 1);
        Order child = new Order(STORE_ID);
        child.setOrderId("order-2");
        child.setTotalPrice(50.0);
        child.setStatus(OrderStatus.Completed);
        child.setBillingDetails(BillingDetails._default());
        child.setShippingDetails(ShippingDetails._default());
        OrderItem moved = new OrderItem("order-2", "Other", "test-item", 1, 50.0, "SKU-moved", false);
        moved.setItemId("item-moved");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(child);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item1));
        when(orderItemFamily.siblingItems(order)).thenReturn(List.of(moved));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(false), any()))
                .thenReturn(OperationResult.success());

        // when
        ordersRMAManager.acceptReturn(STORE_ID, rma(), List.of(rmaItemFor(moved)), ItemCondition.Sealed);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository, org.mockito.Mockito.atLeast(1)).save(orderCaptor.capture());
        Order savedChild = orderCaptor.getAllValues().stream()
                .filter(o -> "order-2".equals(o.getOrderId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected the child order to be saved"));
        assertThat(savedChild.getTotalPrice()).isEqualTo(0.0);
        assertThat(savedChild.getStatus()).isEqualTo(OrderStatus.Delivered);
        Order savedParent = orderCaptor.getAllValues().stream()
                .filter(o -> ORDER_ID.equals(o.getOrderId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected the parent order to be saved"));
        assertThat(savedParent.getTotalPrice()).isEqualTo(100.0);
    }

    @Test
    void createReplacementOrderResolvesAnRmaItemThatLivesOnASplitOffOrder() {
        // given
        Order order = orderWithTotalPrice(100.0, OrderStatus.Delivered);
        OrderItem moved = new OrderItem("order-2", "Other", "test-item", 1, 100.0, "SKU-moved", false);
        moved.setItemId("item-moved");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(orderItemFamily.siblingItems(order)).thenReturn(List.of(moved));
        when(rmaGoodsInService.receive(eq(STORE_ID), any(), any(), any(), eq(true), any()))
                .thenReturn(OperationResult.success());

        // when
        ordersRMAManager.createReplacementOrder(STORE_ID, rma(), List.of(rmaItemFor(moved)), true, ItemCondition.Damaged);

        // then
        assertThat(moved.getStatus()).isEqualTo(FulfilmentStatus.Replaced);
    }

    private Order orderWithTotalPrice(double totalPrice, OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setTotalPrice(totalPrice);
        order.setStatus(status);
        order.setBillingDetails(BillingDetails._default());
        order.setShippingDetails(ShippingDetails._default());
        return order;
    }

    private OrderItem orderItem(String itemId, double price, int qty) {
        OrderItem item = new OrderItem(ORDER_ID, "Other", "test-item", qty, price, "SKU-" + itemId, false);
        item.setItemId(itemId);
        return item;
    }

    private RMA rma() {
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId(RMA_ID);
        rma.setOrderId(ORDER_ID);
        return rma;
    }

    private RMAItem rmaItemFor(OrderItem orderItem) {
        RMAItem rmaItem = new RMAItem();
        rmaItem.setRmaId(RMA_ID);
        rmaItem.setItemId(orderItem.getItemId());
        rmaItem.setQty(orderItem.getQty());
        return rmaItem;
    }
}
