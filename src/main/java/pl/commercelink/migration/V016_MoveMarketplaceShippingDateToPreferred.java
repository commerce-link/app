package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "V016-move-marketplace-shipping-date-to-preferred", order = "016", author = "commercelink")
public class V016_MoveMarketplaceShippingDateToPreferred {

    static final LocalDate MARKETPLACE_SHIPPING_DATE_IMPORT_START = LocalDate.of(2026, 9, 3);

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;

    @Execution
    public void migrate() {
        int migrated = 0;
        for (Store store : storesRepository.findAll()) {
            for (Order order : ordersRepository.findAll(store.getStoreId())) {
                if (migrateOrder(order)) {
                    ordersRepository.save(order);
                    migrated++;
                }
            }
        }
        log.info("Marketplace shipping dates moved to preferredShippingAt: orders={}", migrated);
    }

    private boolean migrateOrder(Order order) {
        if (!isImportedWithMarketplaceShippingDate(order)
                || order.getPreferredShippingAt() != null
                || order.getEstimatedShippingAt() == null) {
            return false;
        }
        order.setPreferredShippingAt(order.getEstimatedShippingAt());
        if (order.getEstimatedAssemblyAt() == null) {
            order.setEstimatedShippingAt(null);
        }
        return true;
    }

    private boolean isImportedWithMarketplaceShippingDate(Order order) {
        return order.isMarketplaceOrder()
                && order.getOrderedAt() != null
                && !order.getOrderedAt().toLocalDate().isBefore(MARKETPLACE_SHIPPING_DATE_IMPORT_START);
    }

    @RollbackExecution
    public void rollback() {
    }
}
