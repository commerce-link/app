package pl.commercelink.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.orders.rma.RMACentersRepository;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoStoreDeletionServiceTest {

    private static final String STORE_ID = "abc123def4";

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private OrderEventsRepository orderEventsRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RMACentersRepository rmaCentersRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private DemoStoreWipeRepository wipeRepository;
    @Mock private FileStorage fileStorage;
    @Mock private StoreInventoryCache storeInventoryCache;
    @Mock private ObjectProvider<DemoUserService> demoUserServiceProvider;
    @Mock private DemoUserService demoUserService;

    private DemoStoreDeletionService service;

    @BeforeEach
    void setUp() {
        service = new DemoStoreDeletionService(storesRepository, ordersRepository, orderItemsRepository,
                orderEventsRepository, productCatalogRepository, productRepository, rmaCentersRepository,
                rmaItemsRepository, wipeRepository, fileStorage, storeInventoryCache, demoUserServiceProvider,
                "stores");
    }

    private Store demoStore() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setDemo(new DemoStoreMetadata("user@example.com", "2026-07-01T00:00:00Z", "2026-07-15T00:00:00Z"));
        return store;
    }

    @Test
    void refusesToDeleteStoreWithoutDemoMarker() {
        // given
        Store regular = new Store();
        regular.setStoreId(STORE_ID);
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
        when(demoUserServiceProvider.getIfAvailable()).thenReturn(demoUserService);
        Order order = new Order();
        order.setStoreId(STORE_ID);
        order.setOrderId("order-1");
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of(order));
        when(orderItemsRepository.findByOrderId("order-1")).thenReturn(List.of());
        when(orderEventsRepository.findByOrderId("order-1")).thenReturn(List.of());
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of());
        RMACenter sharedCenter = new RMACenter();
        sharedCenter.setStoreId("default");
        when(rmaCentersRepository.findByStoreId(STORE_ID)).thenReturn(List.of(sharedCenter));
        when(wipeRepository.findRmas(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseDocuments(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseItems(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseDocumentSequences(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findEmailTemplates(STORE_ID)).thenReturn(List.of());

        // when
        service.deleteDemoStore(STORE_ID);

        // then
        verify(demoUserService).deleteUser("user@example.com");
        verify(wipeRepository).deleteAll(List.of(order));
        verify(wipeRepository, never()).deleteAll(List.of(sharedCenter));
        verify(fileStorage).deleteAll("stores", STORE_ID + "/");
        verify(storeInventoryCache).evict(STORE_ID);
        verify(wipeRepository).deleteStore(store);
    }

    @Test
    void keepsStoreRecordWhenAnyStepFails() {
        // given
        Store store = demoStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(demoUserServiceProvider.getIfAvailable()).thenReturn(demoUserService);
        doThrow(new RuntimeException("cognito down")).when(demoUserService).deleteUser(any());
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of());
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of());
        when(rmaCentersRepository.findByStoreId(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findRmas(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseDocuments(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseItems(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findWarehouseDocumentSequences(STORE_ID)).thenReturn(List.of());
        when(wipeRepository.findEmailTemplates(STORE_ID)).thenReturn(List.of());

        // when
        service.deleteDemoStore(STORE_ID);

        // then
        verify(wipeRepository, never()).deleteStore(any());
        verify(fileStorage).deleteAll("stores", STORE_ID + "/");
        verify(storeInventoryCache).evict(STORE_ID);
    }
}
