package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// KNOWN LIMITS of the name matching (services-as-a-flag release): items whose service definition
// was RENAMED or DELETED before this migration keep the old name and stay unflagged; a product
// definition sharing its name with a service definition in the same store gets its items
// over-flagged. Both are inherent to matching by definition NAME, not a stable id.
@ChangeUnit(id = "V007-service-flag-migration", order = "007", author = "commercelink")
@RequiredArgsConstructor
public class V007_ServiceFlagMigration {

    private static final String LEGACY_SERVICES_CATEGORY = "Services";

    private final StoresRepository storesRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final BasketsRepository basketsRepository;

    @Execution
    public void migrate() {
        for (Store store : storesRepository.findAll()) {
            migrateStore(store.getStoreId());
        }
    }

    private void migrateStore(String storeId) {
        Set<String> serviceNames = new HashSet<>();
        List<CategoryDefinition> serviceDefinitions = new ArrayList<>();

        List<ProductCatalog> catalogs = productCatalogRepository.findAll(storeId);

        int migratedDefinitions = 0;
        for (ProductCatalog catalog : catalogs) {
            boolean dirty = false;
            for (CategoryDefinition definition : catalog.getCategories()) {
                boolean legacy = LEGACY_SERVICES_CATEGORY.equals(definition.getCategory());
                if (legacy) {
                    serviceDefinitions.add(definition);
                    if (StringUtils.isNotBlank(definition.getName())) {
                        serviceNames.add(definition.getName());
                    }
                    definition.setCategory(null);
                    dirty = true;
                    migratedDefinitions++;
                }
            }
            if (dirty) {
                productCatalogRepository.save(catalog);
            }
        }

        int migratedProducts = 0;
        for (CategoryDefinition definition : serviceDefinitions) {
            for (Product product : productRepository.findAll(definition.getCategoryId())) {
                if (!product.isService()) {
                    product.setService(true);
                    migratedProducts++;
                    productRepository.save(product);
                }
            }
        }

        int migratedOrderItems = 0;
        for (Order order : ordersRepository.findAll(storeId)) {
            for (OrderItem item : orderItemsRepository.findByOrderId(order.getOrderId())) {
                if (migrateItem(item, serviceNames)) {
                    migratedOrderItems++;
                    orderItemsRepository.save(item);
                }
            }
        }

        int migratedBasketItems = 0;
        for (Basket basket : basketsRepository.findAll(storeId)) {
            if (basket.getBasketItems() == null) {
                continue;
            }
            boolean dirty = false;
            for (BasketItem item : basket.getBasketItems()) {
                if (migrateItem(item, serviceNames)) {
                    migratedBasketItems++;
                    dirty = true;
                }
            }
            if (dirty) {
                basketsRepository.save(basket);
            }
        }

        System.out.println(storeId + ": definitions=" + migratedDefinitions + ", products=" + migratedProducts
                + ", orderItems=" + migratedOrderItems + ", basketItems=" + migratedBasketItems);
    }

    private boolean migrateItem(OrderItem item, Set<String> serviceNames) {
        boolean changed = false;
        if (!item.isService() && isLegacyService(item.getCategory(), serviceNames)) {
            item.setService(true);
            changed = true;
        }
        if (LEGACY_SERVICES_CATEGORY.equals(item.getCategory())) {
            item.setCategory(null);
            changed = true;
        }
        return changed;
    }

    private boolean migrateItem(BasketItem item, Set<String> serviceNames) {
        boolean changed = false;
        if (!item.isService() && isLegacyService(item.getCategory(), serviceNames)) {
            item.setService(true);
            changed = true;
        }
        if (LEGACY_SERVICES_CATEGORY.equals(item.getCategory())) {
            item.setCategory(null);
            changed = true;
        }
        return changed;
    }

    private boolean isLegacyService(String category, Set<String> serviceNames) {
        return LEGACY_SERVICES_CATEGORY.equals(category)
                || (category != null && serviceNames.contains(category));
    }

    @RollbackExecution
    public void rollback() {
    }
}
