package pl.commercelink.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.MarketplaceOrdersImportScheduler;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V014_CreateOrdersImportSchedulesTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceOrdersImportScheduler ordersImportScheduler;
    @InjectMocks
    private V014_CreateOrdersImportSchedules migration;

    private static Store store(String storeId, MarketplaceIntegration... integrations) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.getMarketplaces().addAll(List.of(integrations));
        return store;
    }

    @Test
    void createsAScheduleOnlyWhereNoneExistsKeepingAStoredCron() {
        // given
        MarketplaceIntegration allegro = new MarketplaceIntegration("Allegro");
        MarketplaceIntegration empik = new MarketplaceIntegration("Empik");
        empik.setOrdersImportSchedule("0 9 * * ? *");
        MarketplaceIntegration alreadyScheduled = new MarketplaceIntegration("Morele");
        when(storesRepository.findAll()).thenReturn(List.of(store("store-1", allegro, empik, alreadyScheduled), store("store-2")));
        when(ordersImportScheduler.snapshot(anyString(), anyString())).thenReturn(Optional.empty());
        when(ordersImportScheduler.snapshot("store-1", "Morele")).thenReturn(Optional.of("cron(4/10 * * * ? *)"));

        // when
        migration.createMissingSchedules();

        // then
        verify(ordersImportScheduler).apply("store-1", "Allegro", null);
        verify(ordersImportScheduler).apply("store-1", "Empik", "0 9 * * ? *");
        verify(ordersImportScheduler, never()).apply(eq("store-1"), eq("Morele"), any());
    }

    @Test
    void aFailingIntegrationDoesNotStopTheOthersButFailsTheRunSoItIsRetried() {
        // given
        when(storesRepository.findAll()).thenReturn(List.of(
                store("store-1", new MarketplaceIntegration("Allegro")),
                store("store-2", new MarketplaceIntegration("Allegro"))));
        when(ordersImportScheduler.snapshot(anyString(), anyString())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("eventbridge down")).when(ordersImportScheduler).apply("store-1", "Allegro", null);

        // when / then
        assertThatThrownBy(migration::createMissingSchedules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store-1/Allegro");
        verify(ordersImportScheduler).apply("store-2", "Allegro", null);
    }

    @Test
    void aRerunTouchesNothingOnceEveryIntegrationHasItsSchedule() {
        // given
        when(storesRepository.findAll()).thenReturn(List.of(store("store-1", new MarketplaceIntegration("Allegro"))));
        when(ordersImportScheduler.snapshot(anyString(), anyString())).thenReturn(Optional.of("cron(4/10 * * * ? *)"));

        // when
        migration.createMissingSchedules();

        // then
        verify(ordersImportScheduler, never()).apply(any(), any(), any());
    }
}
