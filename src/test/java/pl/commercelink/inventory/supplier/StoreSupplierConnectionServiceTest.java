package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierConnectionServiceTest {

    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private ProviderConfigurationManager configurationManager;
    @Mock
    private SupplierConnectionValidator validator;
    @Mock
    private StoreSupplierConnectionPersister persister;

    @InjectMocks
    private StoreSupplierConnectionService service;

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

    @Test
    void superAdminControlsGlobalFlagWhileNonAdminKeepsStoredValue() {
        // given
        Store stored = storeWith(true);

        // when / then
        assertFalse(service.resolveCanUseGlobalSuppliers(stored, false, true));
        assertTrue(service.resolveCanUseGlobalSuppliers(stored, false, false));
    }

    @Test
    void nonAdminWithoutStoredConfigurationDefaultsToNoGlobalSuppliers() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");

        // when / then
        assertFalse(service.resolveCanUseGlobalSuppliers(store, true, false));
    }

    @Test
    void superAdminCanSetInventoryCacheTtl() {
        // given
        Store store = new Store();
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());

        // when
        Integer resolved = service.resolveInventoryCacheTtlMinutes(store, 30, true);

        // then
        assertThat(resolved).isEqualTo(30);
    }

    @Test
    void superAdminCanClearInventoryCacheTtl() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setInventoryCacheTtlMinutes(45);
        Store store = new Store();
        store.setFulfilmentConfiguration(config);

        // when
        Integer resolved = service.resolveInventoryCacheTtlMinutes(store, null, true);

        // then
        assertThat(resolved).isNull();
    }

    @Test
    void nonSuperAdminPreservesExistingInventoryCacheTtl() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setInventoryCacheTtlMinutes(45);
        Store store = new Store();
        store.setFulfilmentConfiguration(config);

        // when
        Integer resolved = service.resolveInventoryCacheTtlMinutes(store, null, false);

        // then
        assertThat(resolved).isEqualTo(45);
    }

    @Test
    void connectOrUpdateKeepsEveryOtherConnectionIncludingManualOnes() {
        // given
        Store store = storeWith(true,
                new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("manual:Hurtownia X", ConnectionMode.MANUAL, true, true));
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Kosatec"), Set.of()));

        // when
        service.connectOrUpdate(store,
                new SupplierSelectionForm("Kosatec", ConnectionMode.OWN, true, true),
                Map.of("login", "u"));

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getSupplierConnections())
                .extracting(StoreSupplierConnection::getSupplierName)
                .containsExactlyInAnyOrder("Elko", "manual:Hurtownia X", "Kosatec");
    }

    @Test
    void connectOrUpdateReplacesTheEntryOfAnAlreadyConnectedSupplier() {
        // given
        Store store = storeWith(true, new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true));
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of()));

        // when
        service.connectOrUpdate(store,
                new SupplierSelectionForm("Elko", ConnectionMode.OWN, false, true),
                Map.of());

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertEquals(1, captor.getValue().getSupplierConnections().size());
        assertFalse(captor.getValue().getSupplierConnections().get(0).isIncludeInPricing());
    }

    @Test
    void connectOrUpdateForcesOwnModeWhenTheStoreCannotUseGlobalSuppliers() {
        // given
        Store store = storeWith(false);
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Elko"), Set.of()));

        // when
        service.connectOrUpdate(store,
                new SupplierSelectionForm("Elko", ConnectionMode.GLOBAL, true, true),
                Map.of("login", "u"));

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertEquals(ConnectionMode.OWN, captor.getValue().getSupplierConnections().get(0).getMode());
    }

    @Test
    void connectOrUpdateValidatesOnlyTheEditedSupplier() {
        // given a second supplier whose stored credentials are broken must not block this edit
        Store store = storeWith(true,
                new StoreSupplierConnection("Broken", ConnectionMode.OWN, true, true));
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Elko"), Set.of()));

        // when
        service.connectOrUpdate(store,
                new SupplierSelectionForm("Elko", ConnectionMode.OWN, true, true),
                Map.of("login", "u"));

        // then
        ArgumentCaptor<List<StoreSupplierConnection>> captor = ArgumentCaptor.forClass(List.class);
        verify(validator).validate(anyBoolean(), captor.capture(), anyMap(), anyMap(), anySet());
        assertThat(captor.getValue())
                .extracting(StoreSupplierConnection::getSupplierName)
                .containsExactly("Elko");
    }

    @Test
    void connectOrUpdateReturnsValidationErrorsWithoutPersisting() {
        // given
        Store store = storeWith(true);
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet()))
                .thenReturn(List.of(ErrorMessage.of("store.supplier.connection.error.requires.field", "Elko", "Login")));

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(store,
                new SupplierSelectionForm("Elko", ConnectionMode.OWN, true, true),
                Map.of());

        // then
        assertTrue(result.hasErrors());
        verify(persister, never()).persist(any(), any(), anyMap());
    }

    @Test
    void connectOrUpdateSucceedsWhenStoreHasNoFulfilmentConfigurationYet() {
        // given: connecting the very first supplier on a store that never had a fulfilment
        // configuration saved before must not throw a NullPointerException.
        Store store = new Store();
        store.setStoreId("store-1");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Elko"), Set.of()));

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(store,
                new SupplierSelectionForm("Elko", ConnectionMode.OWN, true, true),
                Map.of("login", "u"));

        // then
        assertFalse(result.hasErrors());
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getSupplierConnections())
                .extracting(StoreSupplierConnection::getSupplierName)
                .containsExactly("Elko");
    }

    @Test
    void disconnectRemovesOnlyTheNamedSupplier() {
        // given
        Store store = storeWith(true,
                new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("Kosatec", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("manual:Hurtownia X", ConnectionMode.MANUAL, true, true));
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of("Elko")));

        // when
        service.disconnect(store, "Elko");

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getSupplierConnections())
                .extracting(StoreSupplierConnection::getSupplierName)
                .containsExactlyInAnyOrder("Kosatec", "manual:Hurtownia X");
    }

    @Test
    void applyStoreSettingsKeepsTheExistingConnectionListUntouched() {
        // given
        Store store = storeWith(true,
                new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("manual:Hurtownia X", ConnectionMode.MANUAL, true, true));
        FulfilmentConfiguration submitted = configWith(true);
        submitted.setOrderAssemblyDays(7);
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of()));

        // when
        service.applyStoreSettings(store, submitted, true);

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertEquals(7, captor.getValue().getOrderAssemblyDays());
        assertThat(captor.getValue().getSupplierConnections())
                .extracting(StoreSupplierConnection::getSupplierName)
                .containsExactlyInAnyOrder("Elko", "manual:Hurtownia X");
    }

    @Test
    void applyStoreSettingsIgnoresGlobalSupplierFlagForNonSuperAdmins() {
        // given
        Store store = storeWith(false);
        FulfilmentConfiguration submitted = configWith(true);
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of()));

        // when
        service.applyStoreSettings(store, submitted, false);

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertFalse(captor.getValue().isCanUseGlobalSuppliers());
    }

    @Test
    void applyStoreSettingsAlwaysOverwritesEnabledProductGroupsFromTheExistingConfiguration() {
        // given: enabledProductGroups is a legacy field with no form control of its own on this
        // screen, so whatever the submitted form happens to carry must never win over storage.
        Store store = storeWith(true);
        store.getFulfilmentConfiguration().setEnabledProductGroups(List.of("Computers"));
        FulfilmentConfiguration submitted = configWith(true);
        submitted.setEnabledProductGroups(List.of("Ignored"));
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of()));

        // when
        service.applyStoreSettings(store, submitted, true);

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getEnabledProductGroups()).containsExactly("Computers");
    }

    @Test
    void applyStoreSettingsCarriesOverEnabledCategoriesWhenSubmittedDidNotProvideAny() {
        // given
        Store store = storeWith(true);
        store.getFulfilmentConfiguration().setEnabledCategories(List.of("Dom", "Biuro"));
        FulfilmentConfiguration submitted = configWith(true);
        submitted.setEnabledCategories(null);
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of()));

        // when
        service.applyStoreSettings(store, submitted, true);

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getEnabledCategories()).containsExactly("Dom", "Biuro");
    }

    @Test
    void applyStoreSettingsKeepsSubmittedEnabledCategoriesWhenTheFormProvidedThem() {
        // given: unlike enabledProductGroups, enabledCategories is only defaulted from storage
        // when the submitted configuration carries no value at all (null) — a non-null value,
        // even an emptied-out list, must not be clobbered by whatever is stored.
        Store store = storeWith(true);
        store.getFulfilmentConfiguration().setEnabledCategories(List.of("Dom"));
        FulfilmentConfiguration submitted = configWith(true);
        submitted.setEnabledCategories(new ArrayList<>());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of()));

        // when
        service.applyStoreSettings(store, submitted, true);

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getEnabledCategories()).isEmpty();
    }
}
