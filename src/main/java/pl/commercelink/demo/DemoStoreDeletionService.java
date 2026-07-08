package pl.commercelink.demo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.orders.rma.RMACentersRepository;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.builtin.WarehouseDocument;

import java.util.List;

@Service
public class DemoStoreDeletionService {

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final RMACentersRepository rmaCentersRepository;
    private final RMAItemsRepository rmaItemsRepository;
    private final DemoStoreWipeRepository wipeRepository;
    private final FileStorage fileStorage;
    private final StoreInventoryCache storeInventoryCache;
    private final ObjectProvider<DemoUserService> demoUserService;
    private final String storesBucket;

    public DemoStoreDeletionService(StoresRepository storesRepository,
                                    OrdersRepository ordersRepository,
                                    OrderItemsRepository orderItemsRepository,
                                    OrderEventsRepository orderEventsRepository,
                                    ProductCatalogRepository productCatalogRepository,
                                    ProductRepository productRepository,
                                    RMACentersRepository rmaCentersRepository,
                                    RMAItemsRepository rmaItemsRepository,
                                    DemoStoreWipeRepository wipeRepository,
                                    FileStorage fileStorage,
                                    StoreInventoryCache storeInventoryCache,
                                    ObjectProvider<DemoUserService> demoUserService,
                                    @Value("${s3.bucket.stores}") String storesBucket) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.orderItemsRepository = orderItemsRepository;
        this.orderEventsRepository = orderEventsRepository;
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.rmaCentersRepository = rmaCentersRepository;
        this.rmaItemsRepository = rmaItemsRepository;
        this.wipeRepository = wipeRepository;
        this.fileStorage = fileStorage;
        this.storeInventoryCache = storeInventoryCache;
        this.demoUserService = demoUserService;
        this.storesBucket = storesBucket;
    }

    public void deleteDemoStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return;
        }
        if (store.getDemo() == null) {
            throw new IllegalStateException("Refusing to delete non-demo store: " + storeId);
        }

        boolean allSucceeded = true;
        allSucceeded &= step(storeId, "cognito user", () -> deleteCognitoUser(store));
        allSucceeded &= step(storeId, "orders", () -> deleteOrders(storeId));
        allSucceeded &= step(storeId, "catalogs and products", () -> deleteCatalogsAndProducts(storeId));
        allSucceeded &= step(storeId, "warehouse", () -> deleteWarehouse(storeId));
        allSucceeded &= step(storeId, "rma", () -> deleteRma(storeId));
        allSucceeded &= step(storeId, "email templates", () -> wipeRepository.deleteAll(wipeRepository.findEmailTemplates(storeId)));
        allSucceeded &= step(storeId, "s3 objects", () -> fileStorage.deleteAll(storesBucket, storeId + "/"));
        allSucceeded &= step(storeId, "inventory cache", () -> storeInventoryCache.evict(storeId));

        if (allSucceeded) {
            wipeRepository.deleteStore(store);
            System.out.println("[DemoStoreDeletion] Deleted demo store " + storeId);
        } else {
            System.err.println("[DemoStoreDeletion] Store " + storeId + " kept for retry after failed steps");
        }
    }

    private void deleteCognitoUser(Store store) {
        DemoUserService userService = demoUserService.getIfAvailable();
        if (userService == null) {
            throw new IllegalStateException("Demo user service unavailable, enable app.demo.registration.enabled");
        }
        userService.deleteUser(store.getDemo().getOwnerEmail());
    }

    private void deleteOrders(String storeId) {
        List<Order> orders = ordersRepository.findAll(storeId);
        for (Order order : orders) {
            wipeRepository.deleteAll(orderItemsRepository.findByOrderId(order.getOrderId()));
            wipeRepository.deleteAll(orderEventsRepository.findByOrderId(order.getOrderId()));
        }
        wipeRepository.deleteAll(orders);
    }

    private void deleteCatalogsAndProducts(String storeId) {
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(storeId);
        for (ProductCatalog catalog : catalogs) {
            wipeRepository.deleteAll(productRepository.findAll(catalog));
        }
        wipeRepository.deleteAll(catalogs);
    }

    private void deleteWarehouse(String storeId) {
        List<WarehouseDocument> documents = wipeRepository.findWarehouseDocuments(storeId);
        for (WarehouseDocument document : documents) {
            wipeRepository.deleteAll(wipeRepository.findWarehouseDocumentItems(document.getDocumentId()));
        }
        wipeRepository.deleteAll(documents);
        wipeRepository.deleteAll(wipeRepository.findWarehouseItems(storeId));
        wipeRepository.deleteAll(wipeRepository.findWarehouseDocumentSequences(storeId));
    }

    private void deleteRma(String storeId) {
        List<RMA> rmas = wipeRepository.findRmas(storeId);
        for (RMA rma : rmas) {
            wipeRepository.deleteAll(rmaItemsRepository.findByRmaId(rma.getRmaId()));
        }
        wipeRepository.deleteAll(rmas);
        List<RMACenter> ownCenters = rmaCentersRepository.findByStoreId(storeId).stream()
                .filter(center -> storeId.equals(center.getStoreId()))
                .toList();
        wipeRepository.deleteAll(ownCenters);
    }

    private boolean step(String storeId, String name, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            System.err.println("[DemoStoreDeletion] Step '" + name + "' failed for store " + storeId + ": " + e.getMessage());
            return false;
        }
    }
}
