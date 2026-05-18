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
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
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
