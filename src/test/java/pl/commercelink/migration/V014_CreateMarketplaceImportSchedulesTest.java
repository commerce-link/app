package pl.commercelink.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.MarketplaceOrdersImportScheduler;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.MarketplaceReturnsImportScheduler;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;
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
class V014_CreateMarketplaceImportSchedulesTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private MarketplaceOrdersImportScheduler ordersImportScheduler;
    @Mock
    private MarketplaceReturnsImportScheduler returnsImportScheduler;
    @InjectMocks
    private V014_CreateMarketplaceImportSchedules migration;

    @BeforeEach
    void setUp() {
        when(providerFactory.getDescriptor("Allegro")).thenReturn(descriptor("Allegro", true));
        when(providerFactory.getDescriptor("Empik")).thenReturn(descriptor("Empik", false));
        when(ordersImportScheduler.snapshot(anyString(), anyString())).thenReturn(Optional.empty());
        when(returnsImportScheduler.snapshot(anyString(), anyString())).thenReturn(Optional.empty());
    }

    private static Store store(String storeId, MarketplaceIntegration... integrations) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.getMarketplaces().addAll(List.of(integrations));
        return store;
    }

    @Test
    void createsMissingOrdersSchedulesForEveryIntegrationAndReturnsSchedulesOnlyWhereSupported() {
        // given
        MarketplaceIntegration allegro = new MarketplaceIntegration("Allegro");
        MarketplaceIntegration empik = new MarketplaceIntegration("Empik");
        empik.setOrdersImportSchedule("0 9 * * ? *");
        when(storesRepository.findAll()).thenReturn(List.of(store("store-1", allegro, empik), store("store-2")));

        // when
        migration.createMissingSchedules();

        // then
        verify(ordersImportScheduler).apply("store-1", "Allegro", null);
        verify(ordersImportScheduler).apply("store-1", "Empik", "0 9 * * ? *");
        verify(returnsImportScheduler).apply("store-1", "Allegro", null);
        verify(returnsImportScheduler, never()).apply(eq("store-1"), eq("Empik"), any());
    }

    @Test
    void anExistingScheduleIsLeftAlone() {
        // given
        when(storesRepository.findAll()).thenReturn(List.of(store("store-1", new MarketplaceIntegration("Allegro"))));
        when(ordersImportScheduler.snapshot("store-1", "Allegro")).thenReturn(Optional.of("cron(4/10 * * * ? *)"));

        // when
        migration.createMissingSchedules();

        // then
        verify(ordersImportScheduler, never()).apply(any(), any(), any());
        verify(returnsImportScheduler).apply("store-1", "Allegro", null);
    }

    @Test
    void aFailingIntegrationDoesNotStopTheOthersButFailsTheRunSoItIsRetried() {
        // given
        when(storesRepository.findAll()).thenReturn(List.of(
                store("store-1", new MarketplaceIntegration("Empik")),
                store("store-2", new MarketplaceIntegration("Empik"))));
        doThrow(new RuntimeException("eventbridge down")).when(ordersImportScheduler).apply("store-1", "Empik", null);

        // when / then
        assertThatThrownBy(migration::createMissingSchedules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store-1/Empik");
        verify(ordersImportScheduler).apply("store-2", "Empik", null);
    }

    private static MarketplaceProviderDescriptor descriptor(String name, boolean supportsReturns) {
        return new MarketplaceProviderDescriptor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String displayName() {
                return name;
            }

            @Override
            public boolean supportsReturns() {
                return supportsReturns;
            }

            @Override
            public List<ProviderField> configurationFields() {
                return List.of();
            }

            @Override
            public MarketplaceProvider create(Map<String, String> configuration) {
                return null;
            }
        };
    }
}
