package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierConnectionPersisterTest {

    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private ProviderConfigurationManager configurationManager;
    @Mock
    private StoreSupplierFeedScheduler feedScheduler;
    @Mock
    private StoreFeedRepository storeFeedRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreInventoryCache storeInventoryCache;

    @InjectMocks
    private StoreSupplierConnectionPersister persister;

    private Store storeWith(boolean canUseGlobal, StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setCanUseGlobalSuppliers(canUseGlobal);
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private FulfilmentConfiguration configWith(boolean canUseGlobal, StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setCanUseGlobalSuppliers(canUseGlobal);
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        return config;
    }

    private SupplierProviderDescriptor descriptor(String name, boolean hasFields) {
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        SupplierInfo info = mock(SupplierInfo.class);
        when(info.name()).thenReturn(name);
        when(descriptor.supplierInfo()).thenReturn(info);
        when(descriptor.configurationFields())
                .thenReturn(hasFields
                        ? List.of(new ProviderField("url", "Feed URL", ProviderField.FieldType.URL, true, "https://..."))
                        : List.of());
        return descriptor;
    }

    @Test
    void createsScheduleSavesAndTriggersImportForAddedOwnSupplier() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of("Acme", Map.of("url", "https://feed")));

        // then
        assertTrue(outcome.success());
        verify(feedScheduler).schedule("store-1", "Acme", null);
        verify(configurationManager).saveConfiguration(eq(existing), eq("Acme"), eq(acme), any());
        verify(storesRepository).save(existing);
        verify(feedScheduler).triggerImmediateImport("store-1", "Acme");
        verify(feedScheduler, never()).deleteSchedule(anyString(), anyString());
        verify(storeFeedRepository, never()).delete(anyString(), anyString());
    }

    @Test
    void deletesScheduleForRemovedOwnSupplierWithoutTriggeringImport() {
        // given
        Store existing = storeWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        FulfilmentConfiguration submitted = configWith(true);
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of());

        // then
        assertTrue(outcome.success());
        verify(feedScheduler).deleteSchedule("store-1", "Acme");
        verify(configurationManager).deleteConfiguration(existing, "Acme");
        verify(storeFeedRepository).delete("store-1", "Acme");
        verify(storesRepository).save(existing);
        verify(feedScheduler, never()).triggerImmediateImport(anyString(), anyString());
    }

    @Test
    void rollsBackAndReturnsFalseWhenSchedulerFails() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));
        when(configurationManager.snapshot(existing, "Acme"))
                .thenReturn(new ProviderConfigurationManager.SecretSnapshot(false, null));
        doThrow(new RuntimeException("eventbridge down")).when(feedScheduler).schedule("store-1", "Acme", null);

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of("Acme", Map.of("url", "https://feed")));

        // then
        assertFalse(outcome.success());
        verify(storesRepository, never()).save(any());
        verify(configurationManager, never()).saveConfiguration(any(), anyString(), any(SupplierProviderDescriptor.class), any());
        verify(configurationManager).restore(eq(existing), eq("Acme"), any());
        verify(feedScheduler, never()).triggerImmediateImport(anyString(), anyString());
        verify(storeFeedRepository, never()).delete(anyString(), anyString());
    }

    @Test
    void persistConfigurationsSavesOwnConfigurationsWithFields() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));
        Map<String, Map<String, String>> submittedConfig = Map.of("Acme", Map.of("url", "https://feed"));

        // when
        persister.persistConfigurations(existing, submitted, submittedConfig);

        // then
        verify(configurationManager).saveConfiguration(eq(existing), eq("Acme"), eq(acme), eq(Map.of("url", "https://feed")));
        verify(configurationManager, never()).deleteConfiguration(any(), anyString());
    }

    @Test
    void persistConfigurationsDeletesOrphanedSecretWhenSupplierLeavesOwnMode() {
        // given
        Store existing = storeWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL));
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));

        // when
        persister.persistConfigurations(existing, submitted, Map.of());

        // then
        verify(configurationManager).deleteConfiguration(existing, "Acme");
        verify(configurationManager, never()).saveConfiguration(any(), anyString(), any(SupplierProviderDescriptor.class), any());
    }

    @Test
    void rollsBackInLifoOrderWhenSaveStoreFails() {
        // given
        Store existing = storeWith(true, new StoreSupplierConnection("Old", ConnectionMode.OWN));
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("New", ConnectionMode.OWN));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());
        when(feedScheduler.snapshot("store-1", "Old")).thenReturn(Optional.of("cron(37 2 * * ? *)"));
        when(feedScheduler.snapshot("store-1", "New")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("dynamo down")).when(storesRepository).save(any());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of());

        // then
        assertFalse(outcome.success());
        InOrder inOrder = inOrder(feedScheduler);
        inOrder.verify(feedScheduler).schedule("store-1", "New", null);
        inOrder.verify(feedScheduler).deleteSchedule("store-1", "Old");
        inOrder.verify(feedScheduler).restore("store-1", "Old", Optional.of("cron(37 2 * * ? *)"));
        inOrder.verify(feedScheduler).restore("store-1", "New", Optional.empty());
        verify(configurationManager).restore(eq(existing), eq("New"), any());
        verify(configurationManager).restore(eq(existing), eq("Old"), any());
        verify(feedScheduler, never()).triggerImmediateImport(any(), any());
        verify(storeFeedRepository, never()).delete(any(), any());
    }

    @Test
    void returnsTrueAndDoesNotCompensateWhenPostCommitStepFails() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));
        doThrow(new RuntimeException("import boom")).when(feedScheduler).triggerImmediateImport(any(), any());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of("Acme", Map.of("url", "https://feed")));

        // then
        assertTrue(outcome.success());
        verify(storesRepository).save(existing);
        verify(configurationManager, never()).restore(any(), any(), any());
        verify(feedScheduler, never()).deleteSchedule("store-1", "Acme");
    }

    @Test
    void computesAddedAndRemovedTogetherInSingleApply() {
        // given
        Store existing = storeWith(true, new StoreSupplierConnection("Old", ConnectionMode.OWN));
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("New", ConnectionMode.OWN));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of());

        // then
        assertTrue(outcome.success());
        verify(feedScheduler).schedule("store-1", "New", null);
        verify(feedScheduler).deleteSchedule("store-1", "Old");
        verify(storeFeedRepository).delete("store-1", "Old");
        verify(feedScheduler).triggerImmediateImport("store-1", "New");
        verify(feedScheduler, never()).triggerImmediateImport("store-1", "Old");
    }

    @Test
    void evictsStoreInventoryCacheOnSuccessfulPersist() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));

        // when
        persister.persist(existing, submitted, Map.of("Acme", Map.of("url", "https://feed")));

        // then
        verify(storeInventoryCache).evict("store-1");
    }

    @Test
    void doesNotEvictStoreInventoryCacheWhenPersistFails() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        SupplierProviderDescriptor acme = descriptor("Acme", true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(acme));
        when(configurationManager.snapshot(existing, "Acme"))
                .thenReturn(new ProviderConfigurationManager.SecretSnapshot(false, null));
        doThrow(new RuntimeException("eventbridge down")).when(feedScheduler).schedule("store-1", "Acme", null);

        // when
        persister.persist(existing, submitted, Map.of("Acme", Map.of("url", "https://feed")));

        // then
        verify(storeInventoryCache, never()).evict(anyString());
    }

    @Test
    void persistReturnsAddedAndRemovedSuppliers() {
        // given
        Store store = storeWith(true, new StoreSupplierConnection("A", ConnectionMode.OWN));
        FulfilmentConfiguration submitted = configWith(true,
                new StoreSupplierConnection("A", ConnectionMode.OWN),
                new StoreSupplierConnection("B", ConnectionMode.OWN));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome =
                persister.persist(store, submitted, Map.of());

        // then
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.added()).containsExactly("B");
        assertThat(outcome.removed()).isEmpty();
    }

    private StoreSupplierConnection ownWithSchedule(String name, String schedule) {
        StoreSupplierConnection connection = new StoreSupplierConnection(name, ConnectionMode.OWN);
        connection.setFeedSchedule(schedule);
        return connection;
    }

    @Test
    void createsScheduleWithTheSubmittedCronForAddedOwnSupplier() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, ownWithSchedule("Acme", "0/30 9-17 * * ? *"));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of());

        // then
        assertTrue(outcome.success());
        verify(feedScheduler).schedule("store-1", "Acme", "0/30 9-17 * * ? *");
        assertThat(outcome.rescheduled()).isEmpty();
    }

    @Test
    void updatesScheduleWhenKeptOwnSupplierChangesCron() {
        // given
        Store existing = storeWith(true, ownWithSchedule("Acme", "0 5 * * ? *"));
        FulfilmentConfiguration submitted = configWith(true, ownWithSchedule("Acme", "0/30 * * * ? *"));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of());

        // then
        assertTrue(outcome.success());
        assertThat(outcome.rescheduled()).containsExactly("Acme");
        assertThat(outcome.added()).isEmpty();
        verify(feedScheduler).schedule("store-1", "Acme", "0/30 * * * ? *");
        verify(feedScheduler, times(1)).schedule(anyString(), anyString(), any());
        verify(feedScheduler, never()).deleteSchedule(anyString(), anyString());
        verify(feedScheduler, never()).triggerImmediateImport(anyString(), anyString());
    }

    @Test
    void clearingTheCronRestoresTheDefaultSchedule() {
        // given
        Store existing = storeWith(true, ownWithSchedule("Acme", "0 5 * * ? *"));
        FulfilmentConfiguration submitted = configWith(true, ownWithSchedule("Acme", null));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of());

        // then
        assertThat(outcome.rescheduled()).containsExactly("Acme");
        verify(feedScheduler).schedule("store-1", "Acme", null);
    }

    @Test
    void restoresPreviousCronWhenSaveFailsAfterReschedule() {
        // given
        Store existing = storeWith(true, ownWithSchedule("Acme", "0 5 * * ? *"));
        FulfilmentConfiguration submitted = configWith(true, ownWithSchedule("Acme", "0/30 * * * ? *"));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());
        when(feedScheduler.snapshot("store-1", "Acme")).thenReturn(Optional.of("cron(0 5 * * ? *)"));
        doThrow(new RuntimeException("dynamo down")).when(storesRepository).save(any());

        // when
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existing, submitted, Map.of());

        // then
        assertFalse(outcome.success());
        InOrder inOrder = inOrder(feedScheduler);
        inOrder.verify(feedScheduler).schedule("store-1", "Acme", "0/30 * * * ? *");
        inOrder.verify(feedScheduler).restore("store-1", "Acme", Optional.of("cron(0 5 * * ? *)"));
    }

    @Test
    void restoresRemovedSupplierScheduleExactlyOnRollback() {
        // given
        Store existing = storeWith(true, new StoreSupplierConnection("Old", ConnectionMode.OWN));
        FulfilmentConfiguration submitted = configWith(true);
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of());
        when(feedScheduler.snapshot("store-1", "Old")).thenReturn(Optional.of("cron(12 3 * * ? *)"));
        doThrow(new RuntimeException("dynamo down")).when(storesRepository).save(any());

        // when
        persister.persist(existing, submitted, Map.of());

        // then
        verify(feedScheduler).restore("store-1", "Old", Optional.of("cron(12 3 * * ? *)"));
        verify(feedScheduler, never()).schedule(anyString(), anyString(), any());
    }
}
