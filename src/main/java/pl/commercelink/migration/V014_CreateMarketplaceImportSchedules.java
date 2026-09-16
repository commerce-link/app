package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.marketplace.MarketplaceImportScheduler;
import pl.commercelink.marketplace.MarketplaceOrdersImportScheduler;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.MarketplaceReturnsImportScheduler;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "V014-create-marketplace-import-schedules", order = "014", author = "commercelink")
public class V014_CreateMarketplaceImportSchedules {

    private final StoresRepository storesRepository;
    private final MarketplaceProviderFactory providerFactory;
    private final MarketplaceOrdersImportScheduler ordersImportScheduler;
    private final MarketplaceReturnsImportScheduler returnsImportScheduler;

    private int created;
    private int present;
    private final List<String> failed = new ArrayList<>();

    @Execution
    public void createMissingSchedules() {
        for (Store store : storesRepository.findAll()) {
            for (MarketplaceIntegration integration : store.getMarketplaces()) {
                ensureScheduleExists(ordersImportScheduler, store.getStoreId(), integration.getName(), integration.getOrdersImportSchedule());
                if (supportsReturns(integration.getName())) {
                    ensureScheduleExists(returnsImportScheduler, store.getStoreId(), integration.getName(), integration.getReturnsImportSchedule());
                }
            }
        }
        log.info("Marketplace import schedules: created={} alreadyPresent={} failed={}", created, present, failed.size());
        if (!failed.isEmpty()) {
            throw new IllegalStateException("Marketplace import schedules could not be created for " + failed);
        }
    }

    private void ensureScheduleExists(MarketplaceImportScheduler scheduler, String storeId, String marketplace, String stored) {
        try {
            if (scheduler.snapshot(storeId, marketplace).isPresent()) {
                present++;
                return;
            }
            scheduler.apply(storeId, marketplace, stored);
            created++;
        } catch (RuntimeException e) {
            log.error("{} schedule for store {} marketplace {} could not be created",
                    scheduler.getClass().getSimpleName(), storeId, marketplace, e);
            failed.add(storeId + "/" + marketplace + "/" + scheduler.getClass().getSimpleName());
        }
    }

    private boolean supportsReturns(String marketplace) {
        MarketplaceProviderDescriptor descriptor = providerFactory.getDescriptor(marketplace);
        return descriptor != null && descriptor.supportsReturns();
    }

    @RollbackExecution
    public void rollback() {}
}
