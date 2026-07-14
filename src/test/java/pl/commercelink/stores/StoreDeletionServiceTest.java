package pl.commercelink.stores;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.Basket;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.orders.rma.RMACentersRepository;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseDocumentItem;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.users.CognitoUserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreDeletionServiceTest {

    private static final String STORE_ID = "abc123def4";

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private OrderEventsRepository orderEventsRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RMACentersRepository rmaCentersRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private StoreWipeRepository wipeRepository;
    @Mock private FileStorage fileStorage;
    @Mock private StoreInventoryCache storeInventoryCache;
    @Mock private CognitoUserService cognitoUserService;

    private StoreDeletionService service;

    @BeforeEach
    void setUp() {
        service = new StoreDeletionService(storesRepository, ordersRepository, orderItemsRepository,
                orderEventsRepository, productCatalogRepository, productRepository, rmaCentersRepository,
                rmaItemsRepository, wipeRepository, fileStorage, storeInventoryCache, cognitoUserService);
        service.storesBucket = "stores";
    }

    private Store demoStore() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setDemo(new DemoStoreMetadata("user@example.com", "2026-07-01T00:00:00Z", "2026-07-15T00:00:00Z"));
        return store;
    }

    private Store regularStore() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private void stubEmptyCascade() {
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of());
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of());
        when(rmaCentersRepository.findByStoreId(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findRmas(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseDocuments(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseItems(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseDocumentSequences(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findEmailTemplates(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findBaskets(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findDeliveries(STORE_ID)).thenReturn(List.of());
    }

    @Test
    void refusesToDeleteStoreWithoutDemoMarker() {
        // given
        Store regular = regularStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(regular);

        // when / then
        assertThrows(IllegalStateException.class, () -> service.deleteDemoStore(STORE_ID));
        verifyNoInteractions(wipeRepository, fileStorage, storeInventoryCache);
    }

    @Test
    void ignoresMissingStore() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when
        service.deleteDemoStore(STORE_ID);

        // then
        verifyNoInteractions(wipeRepository, fileStorage, storeInventoryCache);
    }

    @Test
    void deletesAllStoreResourcesAndStoreRecordLast() {
        // given
        Store store = demoStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        Order order = new Order();
        order.setStoreId(STORE_ID);
        order.setOrderId("order-1");
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of(order));
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId("order-1");
        when(orderItemsRepository.findByOrderId("order-1")).thenReturn(List.of(orderItem));
        OrderEvent orderEvent = new OrderEvent();
        orderEvent.setOrderId("order-1");
        when(orderEventsRepository.findByOrderId("order-1")).thenReturn(List.of(orderEvent));
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Demo");
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));
        Product product = new Product();
        product.setCategoryId("cat-demo-" + STORE_ID);
        when(productRepository.findAll(catalog)).thenReturn(List.of(product));
        RMACenter sharedCenter = new RMACenter();
        sharedCenter.setStoreId("default");
        when(rmaCentersRepository.findByStoreId(STORE_ID)).thenReturn(List.of(sharedCenter));
        RMA rma = new RMA();
        rma.setStoreId(STORE_ID);
        rma.setRmaId("rma-1");
        when(wipeRepository.findRmas(STORE_ID)).thenReturn(List.of(rma));
        RMAItem rmaItem = new RMAItem();
        rmaItem.setRmaId("rma-1");
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(rmaItem));
        WarehouseDocument document = new WarehouseDocument();
        document.setStoreId(STORE_ID);
        document.setDocumentId("doc-1");
        when(wipeRepository.findWarehouseDocuments(STORE_ID)).thenReturn(List.of(document));
        WarehouseDocumentItem documentItem = new WarehouseDocumentItem();
        documentItem.setDocumentId("doc-1");
        when(wipeRepository.findWarehouseDocumentItems("doc-1")).thenReturn(List.of(documentItem));
        when(wipeRepository.findWarehouseItems(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseDocumentSequences(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findEmailTemplates(STORE_ID)).thenReturn(List.of());
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        when(wipeRepository.findBaskets(STORE_ID)).thenReturn(List.of(basket));
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        when(wipeRepository.findDeliveries(STORE_ID)).thenReturn(List.of(delivery));

        // when
        boolean deleted = service.deleteDemoStore(STORE_ID);

        // then
        assertTrue(deleted);
        verify(cognitoUserService).deleteUser("user@example.com");
        verify(wipeRepository).deleteAll(List.of(basket));
        verify(wipeRepository).deleteAll(List.of(delivery));
        verify(wipeRepository).deleteAll(List.of(orderItem));
        verify(wipeRepository).deleteAll(List.of(orderEvent));
        verify(wipeRepository).deleteAll(List.of(order));
        verify(wipeRepository).deleteAll(List.of(product));
        verify(wipeRepository).deleteAll(List.of(catalog));
        verify(wipeRepository).deleteAll(List.of(rmaItem));
        verify(wipeRepository).deleteAll(List.of(rma));
        verify(wipeRepository).deleteAll(List.of(documentItem));
        verify(wipeRepository).deleteAll(List.of(document));
        verify(wipeRepository, never()).deleteAll(List.of(sharedCenter));
        verify(fileStorage).deleteAll("stores", STORE_ID + "/");
        InOrder lastStep = inOrder(storeInventoryCache, storesRepository);
        lastStep.verify(storeInventoryCache).evict(STORE_ID);
        lastStep.verify(storesRepository).delete(store);
    }

    @Test
    void keepsStoreRecordWhenAnyStepFails() {
        // given
        Store store = demoStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        doThrow(new RuntimeException("cognito down")).when(cognitoUserService).deleteUser(any());
        stubEmptyCascade();

        // when
        boolean deleted = service.deleteDemoStore(STORE_ID);

        // then
        assertFalse(deleted);
        verify(storesRepository, never()).delete(any(Store.class));
        verify(fileStorage).deleteAll("stores", STORE_ID + "/");
        verify(storeInventoryCache).evict(STORE_ID);
    }

    @Test
    void anyGuardDeletesRegularStore() {
        // given
        Store store = regularStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        stubEmptyCascade();

        // when
        boolean result = service.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);

        // then
        assertTrue(result);
        verify(storesRepository).delete(store);
        verifyNoInteractions(cognitoUserService);
    }

    @Test
    void demoOnlyGuardRefusesRegularStore() {
        // given
        Store store = regularStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertThrows(IllegalStateException.class,
                () -> service.deleteStore(STORE_ID, StoreDeletionService.Guard.DEMO_ONLY));
        verifyNoInteractions(wipeRepository, fileStorage, storeInventoryCache);
    }

    @Test
    void anyGuardStillDeletesCognitoUserForDemoStore() {
        // given
        Store store = demoStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        stubEmptyCascade();

        // when
        service.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);

        // then
        verify(cognitoUserService).deleteUser("user@example.com");
    }
}
