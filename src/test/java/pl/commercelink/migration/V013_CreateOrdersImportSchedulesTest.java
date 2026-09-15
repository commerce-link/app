package pl.commercelink.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.MarketplaceOrdersImportScheduler;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V013_CreateOrdersImportSchedulesTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceOrdersImportScheduler ordersImportScheduler;
    @InjectMocks
    private V013_CreateOrdersImportSchedules migration;

    private static Store store(String storeId, MarketplaceIntegration... integrations) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.getMarketplaces().addAll(List.of(integrations));
        return store;
    }

    @Test
    void appliesAScheduleForEveryMarketplaceIntegrationKeepingAStoredCron() {
        // given
        MarketplaceIntegration allegro = new MarketplaceIntegration("Allegro");
        MarketplaceIntegration empik = new MarketplaceIntegration("Empik");
        empik.setOrdersImportSchedule("0 9 * * ? *");
        when(storesRepository.findAll()).thenReturn(List.of(store("store-1", allegro, empik), store("store-2")));

        // when
        migration.createSchedules();

        // then
        verify(ordersImportScheduler).apply("store-1", "Allegro", null);
        verify(ordersImportScheduler).apply("store-1", "Empik", "0 9 * * ? *");
        verifyNoMoreInteractions(ordersImportScheduler);
    }
}
