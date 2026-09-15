package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.marketplace.MarketplaceOrdersImportScheduler;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "V013-create-orders-import-schedules", order = "013", author = "commercelink")
public class V013_CreateOrdersImportSchedules {

    private final StoresRepository storesRepository;
    private final MarketplaceOrdersImportScheduler ordersImportScheduler;

    @Execution
    public void createSchedules() {
        int created = 0;
        for (Store store : storesRepository.findAll()) {
            for (MarketplaceIntegration integration : store.getMarketplaces()) {
                ordersImportScheduler.apply(store.getStoreId(), integration.getName(), integration.getOrdersImportSchedule());
                created++;
            }
        }
        log.info("Ensured an orders import schedule for {} marketplace integrations", created);
    }

    @RollbackExecution
    public void rollback() {}
}
