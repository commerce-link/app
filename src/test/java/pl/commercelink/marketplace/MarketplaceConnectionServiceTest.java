package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceConnectionServiceTest {

    private static final MarketplaceProviderDescriptor ALLEGRO = descriptor("Allegro", "Allegro.pl",
            new ProviderField("clientId", "Client ID", ProviderField.FieldType.TEXT, true, ""),
            new ProviderField("clientSecret", "Client Secret", ProviderField.FieldType.PASSWORD, true, ""));
    private static final MarketplaceProviderDescriptor EMPIK = descriptor("Empik", "EmpikPlace",
            new ProviderField("apiKey", "API Key", ProviderField.FieldType.PASSWORD, true, ""));

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private ProviderConfigurationManager configurationManager;
    @Mock
    private MarketplaceOrdersImportScheduler ordersImportScheduler;

    private MarketplaceConnectionService service;
    private Store store;

    @BeforeEach
    void setUp() {
        service = new MarketplaceConnectionService(storesRepository, providerFactory, configurationManager,
                ordersImportScheduler, 5);
        store = new Store();
        store.setStoreId("store-1");
        when(providerFactory.availableProviders()).thenReturn(List.of(ALLEGRO, EMPIK));
        when(providerFactory.getDescriptor("Allegro")).thenReturn(ALLEGRO);
        when(providerFactory.getDescriptor("Empik")).thenReturn(EMPIK);
        when(providerFactory.deviceAuthProviders()).thenReturn(List.of("Allegro"));
        when(providerFactory.loadConfiguration(any(), anyString())).thenReturn(new HashMap<>());
        when(providerFactory.resolveCredentialName(ALLEGRO)).thenReturn("allegro_marketplace");
        when(providerFactory.resolveCredentialName(EMPIK)).thenReturn("empik_marketplace");
    }

    @Test
    void connectsANewMarketplaceWithItsCredentialsAndSchedule() {
        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Empik", Map.of("apiKey", "secret"), " 0/15 * * * ? * ");

        // then
        assertThat(result.hasErrors()).isFalse();
        verify(providerFactory).saveConfiguration(store, "Empik", Map.of("apiKey", "secret"));
        MarketplaceIntegration integration = store.getMarketplaceIntegration("Empik");
        assertThat(integration.isLoggedIn()).isTrue();
        assertThat(integration.getOrdersImportSchedule()).isEqualTo("0/15 * * * ? *");
        verify(ordersImportScheduler).apply("store-1", "Empik", "0/15 * * * ? *");
        verify(storesRepository).save(store);
    }

    @Test
    void aDeviceAuthMarketplaceStartsAsAwaitingAuthorization() {
        // when
        service.connectOrUpdate(store, "Allegro", Map.of("clientId", "id", "clientSecret", "s"), "");

        // then
        assertThat(store.getMarketplaceIntegration("Allegro").isLoggedIn()).isFalse();
        verify(ordersImportScheduler, never()).apply(any(), any(), any());
        verify(storesRepository).save(store);
    }

    @Test
    void anUnchangedScheduleIsNotSentToTheSchedulerAgain() {
        // given
        MarketplaceIntegration existing = new MarketplaceIntegration("Empik");
        existing.setOrdersImportSchedule("0/15 * * * ? *");
        store.getMarketplaces().add(existing);
        when(providerFactory.loadConfiguration(store, "Empik")).thenReturn(Map.of("apiKey", "stored"));

        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Empik", Map.of("apiKey", ""), "0/15  * * * ? *");

        // then
        assertThat(result.hasErrors()).isFalse();
        verify(ordersImportScheduler, never()).apply(any(), any(), any());
        verify(providerFactory).saveConfiguration(store, "Empik", Map.of("apiKey", ""));
        verify(storesRepository).save(store);
    }

    @Test
    void clearingTheScheduleRemovesItFromTheScheduler() {
        // given
        MarketplaceIntegration existing = new MarketplaceIntegration("Empik");
        existing.setOrdersImportSchedule("0/15 * * * ? *");
        store.getMarketplaces().add(existing);
        when(providerFactory.loadConfiguration(store, "Empik")).thenReturn(Map.of("apiKey", "stored"));

        // when
        service.connectOrUpdate(store, "Empik", Map.of(), "   ");

        // then
        verify(ordersImportScheduler).apply("store-1", "Empik", null);
        assertThat(existing.getOrdersImportSchedule()).isNull();
    }

    @Test
    void aBlankRequiredPasswordIsAnErrorOnlyWithoutAStoredSecret() {
        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Empik", Map.of("apiKey", ""), "");

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code)
                .containsExactly("store.marketplaces.error.requires.field");
        assertThat(result.errors().get(0).args()).containsExactly("Empik", "API Key");
        verify(providerFactory, never()).saveConfiguration(any(), any(), any());
        verify(storesRepository, never()).save(any());
    }

    @Test
    void rejectsAScheduleBelowTheFloorWithoutTouchingAnything() {
        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Empik", Map.of("apiKey", "secret"), "0/2 * * * ? *");

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code)
                .containsExactly("store.marketplaces.import.schedule.error.too.frequent");
        assertThat(result.errors().get(0).args()).containsExactly("Empik", "0/2 * * * ? *", 5);
        verify(providerFactory, never()).saveConfiguration(any(), any(), any());
        verify(ordersImportScheduler, never()).apply(any(), any(), any());
        verify(storesRepository, never()).save(any());
    }

    @Test
    void rejectsAnInvalidScheduleExpression() {
        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Empik", Map.of("apiKey", "secret"), "every 5 minutes");

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code)
                .containsExactly("store.marketplaces.import.schedule.error.invalid");
        verify(storesRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownMarketplace() {
        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Ebay", Map.of(), "");

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code).containsExactly("store.marketplaces.error.unknown");
        verify(storesRepository, never()).save(any());
    }

    @Test
    void disconnectingDropsCredentialsIntegrationAndSchedule() {
        // given
        store.getMarketplaces().add(new MarketplaceIntegration("Empik"));

        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.disconnect(store, "Empik");

        // then
        assertThat(result.hasErrors()).isFalse();
        verify(providerFactory).deleteConfiguration(store, "Empik");
        assertThat(store.getMarketplaceIntegration("Empik")).isNull();
        verify(ordersImportScheduler).delete("store-1", "Empik");
        verify(storesRepository).save(store);
    }

    @Test
    void disconnectingAMarketplaceThatIsNotConnectedIsAnError() {
        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.disconnect(store, "Empik");

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code)
                .containsExactly("store.marketplaces.import.schedule.error.missing");
        verify(storesRepository, never()).save(any());
    }

    @Test
    void aFailedStoreSaveRestoresTheSecretAndTheScheduleAndReportsTheFailure() {
        // given
        ProviderConfigurationManager.SecretSnapshot secretBefore =
                new ProviderConfigurationManager.SecretSnapshot(false, null);
        when(configurationManager.snapshot(store, "empik_marketplace")).thenReturn(secretBefore);
        when(ordersImportScheduler.snapshot("store-1", "Empik")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("dynamo down")).when(storesRepository).save(store);

        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Empik", Map.of("apiKey", "secret"), "0/15 * * * ? *");

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code).containsExactly("store.marketplaces.error.update.failed");
        var order = inOrder(providerFactory, ordersImportScheduler, storesRepository, configurationManager);
        order.verify(providerFactory).saveConfiguration(store, "Empik", Map.of("apiKey", "secret"));
        order.verify(ordersImportScheduler).apply("store-1", "Empik", "0/15 * * * ? *");
        order.verify(storesRepository).save(store);
        order.verify(ordersImportScheduler).restore("store-1", "Empik", Optional.empty());
        order.verify(configurationManager).restore(store, "empik_marketplace", secretBefore);
    }

    @Test
    void aFailedScheduleUpdateRestoresTheSecretButNotAScheduleItNeverTouched() {
        // given
        ProviderConfigurationManager.SecretSnapshot secretBefore =
                new ProviderConfigurationManager.SecretSnapshot(true, Map.of("apiKey", "old"));
        when(configurationManager.snapshot(store, "empik_marketplace")).thenReturn(secretBefore);
        when(ordersImportScheduler.snapshot("store-1", "Empik")).thenReturn(Optional.of("cron(0 9 * * ? *)"));
        doThrow(new RuntimeException("eventbridge down")).when(ordersImportScheduler).apply(any(), any(), any());

        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(
                store, "Empik", Map.of("apiKey", "new"), "0/15 * * * ? *");

        // then
        assertThat(result.hasErrors()).isTrue();
        verify(ordersImportScheduler).restore("store-1", "Empik", Optional.of("cron(0 9 * * ? *)"));
        verify(configurationManager).restore(store, "empik_marketplace", secretBefore);
        verify(storesRepository, never()).save(any());
    }

    @Test
    void aSuccessfulSaveRestoresNothing() {
        // given
        when(configurationManager.snapshot(any(), anyString()))
                .thenReturn(new ProviderConfigurationManager.SecretSnapshot(false, null));

        // when
        service.connectOrUpdate(store, "Empik", Map.of("apiKey", "secret"), "0/15 * * * ? *");

        // then
        verify(configurationManager, never()).restore(any(), anyString(), any());
        verify(ordersImportScheduler, never()).restore(anyString(), anyString(), any());
    }

    @Test
    void aFailedDisconnectPutsTheSecretAndTheScheduleBack() {
        // given
        store.getMarketplaces().add(new MarketplaceIntegration("Empik"));
        ProviderConfigurationManager.SecretSnapshot secretBefore =
                new ProviderConfigurationManager.SecretSnapshot(true, Map.of("apiKey", "old"));
        when(configurationManager.snapshot(store, "empik_marketplace")).thenReturn(secretBefore);
        when(ordersImportScheduler.snapshot("store-1", "Empik")).thenReturn(Optional.of("cron(0/15 * * * ? *)"));
        doThrow(new RuntimeException("dynamo down")).when(storesRepository).save(store);

        // when
        MarketplaceConnectionService.ConnectionUpdateResult result = service.disconnect(store, "Empik");

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code).containsExactly("store.marketplaces.error.update.failed");
        verify(ordersImportScheduler).restore("store-1", "Empik", Optional.of("cron(0/15 * * * ? *)"));
        verify(configurationManager).restore(store, "empik_marketplace", secretBefore);
    }

    @Test
    void viewsDescribeEveryConnectedMarketplace() {
        // given
        MarketplaceIntegration allegro = new MarketplaceIntegration("Allegro");
        allegro.setLoggedIn(false);
        MarketplaceIntegration empik = new MarketplaceIntegration("Empik");
        empik.setOrdersImportSchedule("0/15 * * * ? *");
        store.getMarketplaces().addAll(List.of(allegro, empik));

        // when
        List<MarketplaceIntegrationView> views = service.views(store);

        // then
        assertThat(views).extracting(MarketplaceIntegrationView::name).containsExactly("Allegro", "Empik");
        assertThat(views.get(0).displayName()).isEqualTo("Allegro.pl");
        assertThat(views.get(0).deviceAuth()).isTrue();
        assertThat(views.get(0).connected()).isFalse();
        assertThat(views.get(0).hasOwnSchedule()).isFalse();
        assertThat(views.get(1).hasOwnSchedule()).isTrue();
        assertThat(views.get(1).scheduleDescription().code()).isEqualTo("store.supplier.schedule.summary.every.minutes");
    }

    @Test
    void availableMarketplacesLeaveOutTheConnectedOnes() {
        // given
        store.getMarketplaces().add(new MarketplaceIntegration("Allegro"));

        // when / then
        assertThat(service.availableMarketplaces(store)).containsExactly(EMPIK);
    }

    @Test
    void marketplacesWithStoredConfigurationComeFromTheSecretsNotTheIntegrationList() {
        // given
        when(providerFactory.loadConfiguration(store, "Allegro")).thenReturn(Map.of("clientId", "id"));

        // when / then
        assertThat(service.marketplacesWithStoredConfiguration(store)).containsExactly("Allegro");
    }

    private static MarketplaceProviderDescriptor descriptor(String name, String displayName, ProviderField... fields) {
        return new MarketplaceProviderDescriptor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public List<ProviderField> configurationFields() {
                return List.of(fields);
            }

            @Override
            public MarketplaceProvider create(Map<String, String> configuration) {
                return null;
            }
        };
    }
}
