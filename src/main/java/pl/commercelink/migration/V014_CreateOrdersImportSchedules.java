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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "V014-create-orders-import-schedules", order = "014", author = "commercelink")
public class V014_CreateOrdersImportSchedules {

    private final StoresRepository storesRepository;
    private final MarketplaceOrdersImportScheduler ordersImportScheduler;

    @Execution
    public void createMissingSchedules() {
        int created = 0;
        int present = 0;
        List<String> failed = new ArrayList<>();
        for (Store store : storesRepository.findAll()) {
            for (MarketplaceIntegration integration : store.getMarketplaces()) {
                String storeId = store.getStoreId();
                String marketplace = integration.getName();
                try {
                    if (ordersImportScheduler.snapshot(storeId, marketplace).isPresent()) {
                        present++;
                        continue;
                    }
                    ordersImportScheduler.apply(storeId, marketplace, integration.getOrdersImportSchedule());
                    created++;
                } catch (RuntimeException e) {
                    log.error("Orders import schedule for store {} marketplace {} could not be created", storeId, marketplace, e);
                    failed.add(storeId + "/" + marketplace);
                }
            }
        }
        log.info("Orders import schedules: created={} alreadyPresent={} failed={}", created, present, failed.size());
        if (!failed.isEmpty()) {
            throw new IllegalStateException("Orders import schedules could not be created for " + failed);
        }
    }

    @RollbackExecution
    public void rollback() {}
}
