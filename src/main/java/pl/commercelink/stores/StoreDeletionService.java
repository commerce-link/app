package pl.commercelink.stores;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import pl.commercelink.inventory.supplier.StoreSupplierFeedScheduler;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.marketplace.MarketplaceOrdersImportScheduler;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.MarketplaceReturnsImportScheduler;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.pricelist.PricelistEventScheduler;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.receipts.ReceiptProviderFactory;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.users.CognitoUserService;
import pl.commercelink.warehouse.builtin.WarehouseDocument;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDeletionService {

    public enum Guard { DEMO_ONLY, TRIAL_ONLY, ANY }

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final RMACentersRepository rmaCentersRepository;
    private final RMAItemsRepository rmaItemsRepository;
    private final StoreWipeRepository wipeRepository;
    private final StoreFilesWipe storeFilesWipe;
    private final StoreInventoryCache storeInventoryCache;
    private final CognitoUserService cognitoUserService;
    private final SupplierProviderFactory supplierProviderFactory;
    private final ShippingProviderFactory shippingProviderFactory;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final ReceiptProviderFactory receiptProviderFactory;
    private final MarketplaceProviderFactory marketplaceProviderFactory;
    private final PaymentProviderFactory paymentProviderFactory;
    private final MarketplaceOrdersImportScheduler ordersImportScheduler;
    private final MarketplaceReturnsImportScheduler returnsImportScheduler;
    private final StoreSupplierFeedScheduler feedScheduler;
    private final PricelistEventScheduler pricelistEventScheduler;

    @Value("${s3.bucket.stores}")
    String storesBucket;


    public boolean deleteDemoStore(String storeId) {
        return deleteStore(storeId, Guard.DEMO_ONLY);
    }

    public boolean deleteStore(String storeId, Guard guard) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return true;
        }
        if (guard == Guard.DEMO_ONLY && store.getDemo() == null) {
            throw new IllegalStateException("Refusing to delete non-demo store " + storeId);
        }
        if (guard == Guard.TRIAL_ONLY && store.getTrial() == null) {
            throw new IllegalStateException("Refusing to delete non-trial store " + storeId);
        }

        boolean allSucceeded = true;
        if (store.getDemo() != null) {
            allSucceeded &= step(storeId, "cognito user", () -> deleteCognitoUser(store));
        }
        if (store.getTrial() != null) {
            allSucceeded &= step(storeId, "cognito user", () -> deleteTrialOwner(store));
        }
        allSucceeded &= step(storeId, "schedules", () -> deleteSchedules(store));
        allSucceeded &= step(storeId, "orders", () -> deleteOrders(storeId));
        allSucceeded &= step(storeId, "baskets", () -> wipeRepository.deleteAll(wipeRepository.findBaskets(storeId)));
        allSucceeded &= step(storeId, "deliveries", () -> wipeRepository.deleteAll(wipeRepository.findDeliveries(storeId)));
        allSucceeded &= step(storeId, "catalogs and products", () -> deleteCatalogsAndProducts(storeId));
        allSucceeded &= step(storeId, "warehouse", () -> deleteWarehouse(storeId));
        allSucceeded &= step(storeId, "rma", () -> deleteRma(storeId));
        allSucceeded &= step(storeId, "email templates", () -> wipeRepository.deleteAll(wipeRepository.findEmailTemplates(storeId)));
        allSucceeded &= step(storeId, "receipt attempts", () -> wipeRepository.deleteAll(wipeRepository.findReceiptAttempts(storeId)));
        allSucceeded &= step(storeId, "store notifications", () -> wipeRepository.deleteAll(wipeRepository.findStoreNotifications(storeId)));
        allSucceeded &= step(storeId, "order filters", () -> wipeRepository.deleteAll(wipeRepository.findOrderFilters(storeId)));
        allSucceeded &= step(storeId, "shipment trackings", () -> wipeRepository.deleteAll(wipeRepository.findShipmentTrackings(storeId)));
        if (store.getTrial() != null) {
            // The counts are billing data that outlive a deleted paying store; a trial is never billed.
            allSucceeded &= step(storeId, "schedule execution counts",
                    () -> wipeRepository.deleteAll(wipeRepository.findScheduleExecutionCounts(storeId)));
        }
        allSucceeded &= step(storeId, "s3 objects", () -> storeFilesWipe.deleteAllVersions(storesBucket, storeId + "/"));
        allSucceeded &= step(storeId, "inventory cache", () -> storeInventoryCache.evict(storeId));
        allSucceeded &= step(storeId, "supplier secrets", () -> deleteOwnSupplierSecrets(store));
        allSucceeded &= step(storeId, "integration secrets", () -> deleteIntegrationSecrets(store));

        if (allSucceeded) {
            storesRepository.delete(store);
        }
        return allSucceeded;
    }

    private void deleteSchedules(Store store) {
        String storeId = store.getStoreId();
        for (MarketplaceIntegration integration : store.getMarketplaces()) {
            ordersImportScheduler.delete(storeId, integration.getName());
            returnsImportScheduler.delete(storeId, integration.getName());
        }
        FulfilmentConfiguration fulfilment = store.getFulfilmentConfiguration();
        if (fulfilment != null && fulfilment.getSupplierConnections() != null) {
            fulfilment.getSupplierConnections()
                    .forEach(connection -> feedScheduler.deleteSchedule(storeId, connection.getSupplierName()));
        }
        productCatalogRepository.findAll(storeId)
                .forEach(catalog -> pricelistEventScheduler.deleteSchedule(storeId, catalog.getCatalogId()));
    }

    /** OWN supplier connections keep their credentials in per-store secrets that nothing else cleans up. */
    private void deleteOwnSupplierSecrets(Store store) {
        FulfilmentConfiguration fulfilment = store.getFulfilmentConfiguration();
        if (fulfilment == null || fulfilment.getSupplierConnections() == null) {
            return;
        }
        fulfilment.getSupplierConnections().stream()
                .filter(connection -> connection.getMode() == ConnectionMode.OWN)
                .forEach(connection -> supplierProviderFactory.deleteConfiguration(store, connection.getSupplierName()));
    }

    private void deleteIntegrationSecrets(Store store) {
        deleteConfiguration(store, IntegrationType.SHIPPING_PROVIDER, shippingProviderFactory);
        deleteConfiguration(store, IntegrationType.INVOICING_PROVIDER, invoicingProviderFactory);
        deleteConfiguration(store, IntegrationType.RECEIPT_PROVIDER, receiptProviderFactory);
        store.getMarketplaces()
                .forEach(integration -> marketplaceProviderFactory.deleteConfiguration(store, integration.getName()));
        store.getPayments()
                .forEach(integration -> paymentProviderFactory.deleteConfiguration(store, integration.getName()));
    }

    private void deleteConfiguration(Store store, IntegrationType type, ProviderFactory<?, ?> factory) {
        String providerName = store.getConfigurationValue(type);
        if (providerName != null) {
            factory.deleteConfiguration(store, providerName);
        }
    }

    private void deleteCognitoUser(Store store) {
        cognitoUserService.deleteUser(store.getDemo().getOwnerEmail());
    }

    private void deleteTrialOwner(Store store) {
        String ownerEmail = store.getTrial().getOwnerEmail();
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            cognitoUserService.deleteStoreOwner(ownerEmail, store.getStoreId());
        }
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
            log.error("Deletion step '{}' failed for store {}, the store is kept for a retry", name, storeId, e);
            return false;
        }
    }
}
