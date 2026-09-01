package pl.commercelink.warehouse;

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
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.warehouse.api.GoodsOutHandler;
import pl.commercelink.warehouse.api.GoodsOutRequest;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsOutServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String CREATED_BY = "tester";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private Warehouse warehouse;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private Store store;
    @Mock
    private WarehouseConfiguration warehouseConfiguration;
    @Mock
    private InvoicingProvider invoicingProvider;
    @Mock
    private BillingParty issuer;
    @Mock
    private GoodsOutHandler goodsOutHandler;
    @Mock
    private DropshipItemLookup dropshipItemLookup;
    @Mock
    private OrderLifecycle orderLifecycle;

    @InjectMocks
    private GoodsOutService goodsOutService;

    @BeforeEach
    void setupExecutorPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
    }

    @Test
    @DisplayName("issueGoodsOut returns existing GoodsIssue document immediately when one already exists on the order")
    void issueGoodsOutReturnsExistingDocumentWhenGoodsIssueAlreadyExists() {
        // given
        Document existing = new Document("doc-1", "WZ/1/2026", "https://example.com/wz/1", DocumentType.GoodsIssue);
        Order order = orderWithDocument(existing);

        // when
        OperationResult<Document> result = goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPayload()).isEqualTo(existing);
        verify(storesRepository, never()).findById(any());
        verify(warehouse, never()).goodsOutHandler(any());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueGoodsOut fails when warehouse configuration is missing or not complete")
    void issueGoodsOutFailsWhenWarehouseConfigurationIsMissing() {
        // given
        Order order = orderWithoutDocuments();
        OrderItem item = productItem("item-1");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getWarehouseConfiguration()).thenReturn(warehouseConfiguration);
        when(warehouseConfiguration.isComplete()).thenReturn(false);

        // when
        OperationResult<Document> result = goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Warehouse configuration is missing");
        verify(warehouse, never()).goodsOutHandler(any());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueGoodsOut succeeds without payload when documents generation is disabled in store configuration")
    void issueGoodsOutSucceedsWithoutPayloadWhenDocumentsGenerationDisabled() {
        // given
        Order order = orderWithoutDocuments();
        OrderItem item = productItem("item-1");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getWarehouseConfiguration()).thenReturn(warehouseConfiguration);
        when(warehouseConfiguration.isComplete()).thenReturn(true);
        when(warehouseConfiguration.isDocumentsGenerationEnabled()).thenReturn(false);

        // when
        OperationResult<Document> result = goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasPayload()).isFalse();
        verify(warehouse, never()).goodsOutHandler(any());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueGoodsOut adds returned document to order when warehouse handler successfully issues a goods-out document")
    void issueGoodsOutAddsDocumentToOrderWhenWarehouseHandlerReturnsPayload() {
        // given
        Order order = orderWithoutDocuments();
        Document warehouseDocument = new Document("doc-2", "WZ/2/2026", "https://example.com/wz/2", DocumentType.GoodsIssue);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getWarehouseConfiguration()).thenReturn(warehouseConfiguration);
        when(warehouseConfiguration.isComplete()).thenReturn(true);
        when(warehouseConfiguration.isDocumentsGenerationEnabled()).thenReturn(true);
        when(warehouseConfiguration.getWarehouseId()).thenReturn("wh-main");
        when(warehouseConfiguration.getCostCenterId()).thenReturn("cc-1");
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        when(invoicingProvider.fetchCostCenterById("cc-1")).thenReturn(issuer);
        when(issuer.hasCompanyDetails()).thenReturn(true);
        OrderItem item = productItem("item-1");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(warehouse.goodsOutHandler(STORE_ID)).thenReturn(goodsOutHandler);
        when(goodsOutHandler.issue(any(GoodsOutRequest.class), anyBoolean()))
                .thenReturn(OperationResult.success(warehouseDocument));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        OperationResult<Document> result = goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPayload()).isEqualTo(warehouseDocument);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getDocuments()).contains(warehouseDocument);
    }

    @Test
    @DisplayName("issueGoodsOut creates no document when every product item sits in a dropship delivery")
    void issueGoodsOutCreatesNoDocumentWhenEveryProductItemIsDropship() {
        // given
        Order order = orderWithoutDocuments();
        OrderItem item = productItem("item-1");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(eq(STORE_ID), any())).thenReturn(Set.of("item-1"));

        // when
        OperationResult<Document> result = goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then: the store is never even looked up, so an incomplete warehouse setup cannot fail the order
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasPayload()).isFalse();
        verify(storesRepository, never()).findById(any());
        verify(warehouse, never()).goodsOutHandler(any());
        verifyNoInteractions(invoicingProviderFactory);
    }

    @Test
    @DisplayName("issueGoodsOut creates no document for an order with no product items at all")
    void issueGoodsOutCreatesNoDocumentForAnOrderWithoutProductItems() {
        // given
        Order order = orderWithoutDocuments();
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());

        // when
        OperationResult<Document> result = goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasPayload()).isFalse();
        verify(warehouse, never()).goodsOutHandler(any());
    }

    @Test
    @DisplayName("issueGoodsOut builds the goods-out request from warehouse items only")
    void issueGoodsOutBuildsTheDocumentFromWarehouseItemsOnly() {
        // given
        Order order = orderWithoutDocuments();
        Document warehouseDocument = new Document("doc-3", "WZ/3/2026", "https://example.com/wz/3", DocumentType.GoodsIssue);
        OrderItem dropshipItem = productItem("item-1");
        OrderItem warehouseItem = productItem("item-2");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(dropshipItem, warehouseItem));
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(eq(STORE_ID), any())).thenReturn(Set.of("item-1"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getWarehouseConfiguration()).thenReturn(warehouseConfiguration);
        when(warehouseConfiguration.isComplete()).thenReturn(true);
        when(warehouseConfiguration.isDocumentsGenerationEnabled()).thenReturn(true);
        when(warehouseConfiguration.getWarehouseId()).thenReturn("wh-main");
        when(warehouseConfiguration.getCostCenterId()).thenReturn("cc-1");
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        when(invoicingProvider.fetchCostCenterById("cc-1")).thenReturn(issuer);
        when(issuer.hasCompanyDetails()).thenReturn(true);
        when(warehouse.goodsOutHandler(STORE_ID)).thenReturn(goodsOutHandler);
        when(goodsOutHandler.issue(any(GoodsOutRequest.class), anyBoolean()))
                .thenReturn(OperationResult.success(warehouseDocument));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        ArgumentCaptor<GoodsOutRequest> request = ArgumentCaptor.forClass(GoodsOutRequest.class);
        verify(goodsOutHandler).issue(request.capture(), anyBoolean());
        assertThat(request.getValue().getItems()).hasSize(1);
    }

    @Test
    @DisplayName("issueGoodsOut re-evaluates the order lifecycle after attaching a new goods issue note")
    void issueGoodsOutReEvaluatesTheOrderLifecycleAfterAttachingANewDocument() {
        // given
        Order order = orderWithoutDocuments();
        Document warehouseDocument = new Document("doc-4", "WZ/4/2026", "https://example.com/wz/4", DocumentType.GoodsIssue);
        OrderItem item = productItem("item-1");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getWarehouseConfiguration()).thenReturn(warehouseConfiguration);
        when(warehouseConfiguration.isComplete()).thenReturn(true);
        when(warehouseConfiguration.isDocumentsGenerationEnabled()).thenReturn(true);
        when(warehouseConfiguration.getWarehouseId()).thenReturn("wh-main");
        when(warehouseConfiguration.getCostCenterId()).thenReturn("cc-1");
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        when(invoicingProvider.fetchCostCenterById("cc-1")).thenReturn(issuer);
        when(issuer.hasCompanyDetails()).thenReturn(true);
        when(warehouse.goodsOutHandler(STORE_ID)).thenReturn(goodsOutHandler);
        when(goodsOutHandler.issue(any(GoodsOutRequest.class), anyBoolean()))
                .thenReturn(OperationResult.success(warehouseDocument));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        verify(orderLifecycle).update(any(Order.class));
    }

    @Test
    @DisplayName("issueGoodsOut does not re-evaluate the lifecycle when the goods issue note already exists")
    void issueGoodsOutDoesNotReEvaluateTheLifecycleWhenTheDocumentAlreadyExists() {
        // given
        Document existing = new Document("doc-5", "WZ/5/2026", "https://example.com/wz/5", DocumentType.GoodsIssue);
        Order order = orderWithDocument(existing);

        // when
        OperationResult<Document> result = goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(orderLifecycle);
    }

    @Test
    @DisplayName("issueGoodsOut does not re-evaluate the lifecycle when a concurrent call already attached the document")
    void issueGoodsOutDoesNotReEvaluateTheLifecycleWhenAConcurrentCallAlreadyAttachedTheDocument() {
        // given: the order passed in has no GoodsIssue document yet, so the top-of-method check passes and the
        // handler runs, but by the time the mutator re-reads the order a concurrent call has already attached one.
        Order order = orderWithoutDocuments();
        Document existing = new Document("doc-6", "WZ/6/2026", "https://example.com/wz/6", DocumentType.GoodsIssue);
        Order concurrentlyUpdatedOrder = orderWithDocument(existing);
        Document warehouseDocument = new Document("doc-7", "WZ/7/2026", "https://example.com/wz/7", DocumentType.GoodsIssue);
        OrderItem item = productItem("item-1");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getWarehouseConfiguration()).thenReturn(warehouseConfiguration);
        when(warehouseConfiguration.isComplete()).thenReturn(true);
        when(warehouseConfiguration.isDocumentsGenerationEnabled()).thenReturn(true);
        when(warehouseConfiguration.getWarehouseId()).thenReturn("wh-main");
        when(warehouseConfiguration.getCostCenterId()).thenReturn("cc-1");
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        when(invoicingProvider.fetchCostCenterById("cc-1")).thenReturn(issuer);
        when(issuer.hasCompanyDetails()).thenReturn(true);
        when(warehouse.goodsOutHandler(STORE_ID)).thenReturn(goodsOutHandler);
        when(goodsOutHandler.issue(any(GoodsOutRequest.class), anyBoolean()))
                .thenReturn(OperationResult.success(warehouseDocument));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(concurrentlyUpdatedOrder);

        // when
        goodsOutService.issueGoodsOut(order, CREATED_BY);

        // then
        verifyNoInteractions(orderLifecycle);
    }

    private OrderItem productItem(String itemId) {
        OrderItem item = mock(OrderItem.class);
        when(item.isProduct()).thenReturn(true);
        when(item.getItemId()).thenReturn(itemId);
        return item;
    }

    private Order orderWithoutDocuments() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setBillingDetails(BillingDetails._default());
        return order;
    }

    private Order orderWithDocument(Document document) {
        Order order = orderWithoutDocuments();
        order.addDocument(document);
        return order;
    }
}
