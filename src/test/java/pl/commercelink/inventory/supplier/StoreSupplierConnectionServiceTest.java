package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // Registers the type both bare (for create/GLOBAL) and with any token suffix (for the
    // freshly minted "Type-xxxxxxxx" identities that OWN connections get on create).
    private void registryHas(String type) {
        SupplierProviderDescriptor descriptor = new StubSupplierDescriptor();
        when(supplierProviderFactory.getDescriptor(type)).thenReturn(descriptor);
        when(supplierProviderFactory.getDescriptor(argThat(name -> name != null && name.startsWith(type + "-")))).thenReturn(descriptor);
    }

    private SupplierSelectionForm ownSelection(String type, String label) {
        SupplierSelectionForm form = new SupplierSelectionForm(type, ConnectionMode.OWN, true, true);
        form.setLabel(label);
        return form;
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
        // given: connecting "Kosatec" now mints a tokened identity instead of reusing the bare type
        Store store = storeWith(true,
                new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("manual:Hurtownia X", ConnectionMode.MANUAL, true, true));
        registryHas("Kosatec");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Kosatec"), Set.of(), Set.of()));
        SupplierSelectionForm form = ownSelection("Kosatec", "Kosatec");

        // when
        service.connectOrUpdate(store, form, Map.of("login", "u"));

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getSupplierConnections())
                .extracting(StoreSupplierConnection::getSupplierName)
                .hasSize(3)
                .contains("Elko", "manual:Hurtownia X")
                .anyMatch(name -> name.startsWith("Kosatec-"));
    }

    @Test
    void connectOrUpdateReplacesTheEntryOfAnAlreadyConnectedSupplier() {
        // given: editing an existing connection carries its identity, so it is replaced in place
        // instead of minting a second instance
        Store store = storeWith(true, new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true));
        registryHas("Elko");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));
        SupplierSelectionForm edit = new SupplierSelectionForm("Elko", ConnectionMode.OWN, false, true);
        edit.setIdentity("Elko");
        edit.setLabel("Elko");

        // when
        service.connectOrUpdate(store, edit, Map.of());

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
        registryHas("Elko");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Elko"), Set.of(), Set.of()));
        SupplierSelectionForm form = new SupplierSelectionForm("Elko", ConnectionMode.GLOBAL, true, true);
        form.setLabel("Elko");

        // when
        service.connectOrUpdate(store, form, Map.of("login", "u"));

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertEquals(ConnectionMode.OWN, captor.getValue().getSupplierConnections().get(0).getMode());
    }

    @Test
    void connectOrUpdateStoresTheTrimmedExternalSupplierIdAndDropsABlankOne() {
        // given
        Store store = storeWith(true);
        registryHas("Elko");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Elko"), Set.of(), Set.of()));
        SupplierSelectionForm withId = new SupplierSelectionForm("Elko", ConnectionMode.OWN, true, true);
        withId.setExternalSupplierId(" 2 ");
        withId.setLabel("Elko");
        SupplierSelectionForm blankId = new SupplierSelectionForm("Elko", ConnectionMode.OWN, true, true);
        blankId.setExternalSupplierId("   ");
        blankId.setLabel("Elko");

        // when
        service.connectOrUpdate(store, withId, Map.of("login", "u"));
        service.connectOrUpdate(store, blankId, Map.of("login", "u"));

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister, times(2)).persist(any(), captor.capture(), anyMap());
        assertEquals("2", captor.getAllValues().get(0).getSupplierConnections().get(0).getExternalSupplierId());
        assertNull(captor.getAllValues().get(1).getSupplierConnections().get(0).getExternalSupplierId());
    }

    @Test
    void connectOrUpdateValidatesOnlyTheEditedSupplier() {
        // given a second supplier whose stored credentials are broken must not block this edit
        Store store = storeWith(true,
                new StoreSupplierConnection("Broken", ConnectionMode.OWN, true, true));
        registryHas("Elko");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Elko"), Set.of(), Set.of()));

        // when
        service.connectOrUpdate(store, ownSelection("Elko", "Elko"), Map.of("login", "u"));

        // then
        ArgumentCaptor<List<StoreSupplierConnection>> captor = ArgumentCaptor.forClass(List.class);
        verify(validator).validate(anyBoolean(), captor.capture(), anyMap(), anyMap(), anySet());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSupplierName()).startsWith("Elko-");
    }

    @Test
    void connectOrUpdateReturnsValidationErrorsWithoutPersisting() {
        // given
        Store store = storeWith(true);
        registryHas("Elko");
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet()))
                .thenReturn(List.of(ErrorMessage.of("store.supplier.connection.error.requires.field", "Elko", "Login")));

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                service.connectOrUpdate(store, ownSelection("Elko", "Elko"), Map.of());

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
        registryHas("Elko");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of("Elko"), Set.of(), Set.of()));

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                service.connectOrUpdate(store, ownSelection("Elko", "Elko"), Map.of("login", "u"));

        // then
        assertFalse(result.hasErrors());
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getSupplierConnections()).hasSize(1);
        assertThat(captor.getValue().getSupplierConnections().get(0).getSupplierName()).startsWith("Elko-");
    }

    @Test
    void connectOrUpdateReportsTheSupplierAsHavingStoredConfigurationWhenEditingAnOwnConnection() {
        // given: an OWN connection whose provider already saved configuration -- the contract
        // that a blank password on edit means "keep the current secret" depends on the validator
        // being told this supplier has one
        Store store = storeWith(true, new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true));
        when(configurationManager.loadConfiguration(store, "Elko")).thenReturn(Map.of("login", "u"));
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));
        SupplierSelectionForm edit = ownSelection("Elko", "Elko");
        edit.setIdentity("Elko");

        // when: re-saving with a blank password, as the "leave blank to keep the current value" field does
        service.connectOrUpdate(store, edit, Map.of("password", ""));

        // then
        ArgumentCaptor<Set<String>> storedConfigCaptor = ArgumentCaptor.forClass(Set.class);
        verify(validator).validate(anyBoolean(), anyList(), anyMap(), anyMap(), storedConfigCaptor.capture());
        assertThat(storedConfigCaptor.getValue()).contains("Elko");
    }

    @Test
    void connectOrUpdateReportsNoStoredConfigurationWhenEditingAGlobalConnection() {
        // given: GLOBAL mode never keeps its own stored credentials, regardless of what the
        // provider's configuration manager holds for this supplier
        Store store = storeWith(true, new StoreSupplierConnection("Elko", ConnectionMode.GLOBAL, true, true));
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));
        SupplierSelectionForm edit = new SupplierSelectionForm("Elko", ConnectionMode.GLOBAL, true, true);
        edit.setIdentity("Elko");

        // when
        service.connectOrUpdate(store, edit, Map.of("password", ""));

        // then
        ArgumentCaptor<Set<String>> storedConfigCaptor = ArgumentCaptor.forClass(Set.class);
        verify(validator).validate(anyBoolean(), anyList(), anyMap(), anyMap(), storedConfigCaptor.capture());
        assertThat(storedConfigCaptor.getValue()).isEmpty();
    }

    @Test
    void creatingAnOwnConnectionGeneratesATokenedIdentityAndKeepsTheLabel() {
        // given
        Store store = storeWith(true, new StoreSupplierConnection("Stub", ConnectionMode.OWN, true, true));
        registryHas("Stub");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                service.connectOrUpdate(store, ownSelection("Stub", "Stub konto B"), Map.of("url", "u"));

        // then
        assertFalse(result.hasErrors());
        assertThat(result.identity()).matches("^Stub-[a-z0-9]{8}$");
        ArgumentCaptor<FulfilmentConfiguration> saved = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        ArgumentCaptor<Map<String, Map<String, String>>> config = ArgumentCaptor.forClass(Map.class);
        verify(persister).persist(eq(store), saved.capture(), config.capture());
        assertThat(saved.getValue().getSupplierConnections()).extracting(StoreSupplierConnection::getSupplierName)
                .containsExactlyInAnyOrder("Stub", result.identity());
        StoreSupplierConnection created = saved.getValue().getSupplierConnections().stream()
                .filter(c -> c.getSupplierName().equals(result.identity())).findFirst().orElseThrow();
        assertThat(created.getLabel()).isEqualTo("Stub konto B");
        assertThat(config.getValue()).containsOnlyKeys(result.identity());
    }

    @Test
    void creatingAGlobalConnectionUsesTheTypeAsIdentityAndRefusesADuplicate() {
        // given
        Store store = storeWith(true, new StoreSupplierConnection("Stub", ConnectionMode.GLOBAL, true, true));
        registryHas("Stub");

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(store,
                new SupplierSelectionForm("Stub", ConnectionMode.GLOBAL, true, true), Map.of());

        // then
        assertTrue(result.hasErrors());
        assertThat(result.errors().get(0).code()).isEqualTo("store.supplier.connection.error.global.duplicate");
        verify(persister, never()).persist(any(), any(), anyMap());
    }

    @Test
    void globalAndOwnOfTheSameTypeMayCoexist() {
        // given
        Store store = storeWith(true, new StoreSupplierConnection("Stub", ConnectionMode.GLOBAL, true, true));
        registryHas("Stub");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                service.connectOrUpdate(store, ownSelection("Stub", "Własne konto"), Map.of("url", "u"));

        // then
        assertFalse(result.hasErrors());
        ArgumentCaptor<FulfilmentConfiguration> saved = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(eq(store), saved.capture(), anyMap());
        assertThat(saved.getValue().getSupplierConnections()).hasSize(2);
    }

    @Test
    void editingKeepsTheIdentityAndUpdatesTheLabel() {
        // given
        StoreSupplierConnection existing = new StoreSupplierConnection("Stub-k7f3a9c2", ConnectionMode.OWN, true, true);
        existing.setLabel("Stare");
        Store store = storeWith(true, existing);
        registryHas("Stub");
        when(validator.validate(anyBoolean(), anyList(), anyMap(), anyMap(), anySet())).thenReturn(List.of());
        when(validator.validateLabel(any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));
        SupplierSelectionForm edit = ownSelection("Stub", "Nowe");
        edit.setIdentity("Stub-k7f3a9c2");

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(store, edit, Map.of("url", "u"));

        // then
        assertThat(result.identity()).isEqualTo("Stub-k7f3a9c2");
        ArgumentCaptor<FulfilmentConfiguration> saved = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(eq(store), saved.capture(), anyMap());
        assertThat(saved.getValue().getSupplierConnections()).hasSize(1);
        assertThat(saved.getValue().getSupplierConnections().get(0).getLabel()).isEqualTo("Nowe");
    }

    @Test
    void editingATokenedConnectionIntoGlobalIsRefused() {
        // given
        Store store = storeWith(true, new StoreSupplierConnection("Stub-k7f3a9c2", ConnectionMode.OWN, true, true));
        registryHas("Stub");
        SupplierSelectionForm edit = new SupplierSelectionForm("Stub", ConnectionMode.GLOBAL, true, true);
        edit.setIdentity("Stub-k7f3a9c2");

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(store, edit, Map.of());

        // then
        assertThat(result.errors().get(0).code()).isEqualTo("store.supplier.connection.error.mode.locked");
    }

    @Test
    void editingAnUnknownIdentityIsRefused() {
        // given
        Store store = storeWith(true);
        registryHas("Stub");
        SupplierSelectionForm edit = ownSelection("Stub", "x");
        edit.setIdentity("Stub-nope0000");

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result = service.connectOrUpdate(store, edit, Map.of());

        // then
        assertThat(result.errors().get(0).code()).isEqualTo("store.supplier.connection.error.not.found");
    }

    @Test
    void creatingAnUnknownTypeIsRefused() {
        // given
        Store store = storeWith(true);

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                service.connectOrUpdate(store, ownSelection("Nope", "x"), Map.of());

        // then
        assertThat(result.errors().get(0).code()).isEqualTo("store.supplier.connection.error.unknown.supplier");
    }

    @Test
    void labelErrorsFromTheValidatorBlockTheSave() {
        // given
        Store store = storeWith(true);
        registryHas("Stub");
        when(validator.validateLabel(eq("x"), eq(ConnectionMode.OWN), any()))
                .thenReturn(List.of(ErrorMessage.of("store.supplier.connection.error.label.taken", "x")));

        // when
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                service.connectOrUpdate(store, ownSelection("Stub", "x"), Map.of());

        // then
        assertTrue(result.hasErrors());
        verify(persister, never()).persist(any(), any(), anyMap());
    }

    @Test
    void storedConfigurationSetIsKeyedByConnectionIdentity() {
        // given: an OWN connection whose type also has a GLOBAL connection, plus a second OWN
        // instance of the same type -- the stored-configuration set must be keyed by connection
        // identity, not by supplier type, so each instance's secret is looked up separately
        Store store = storeWith(true,
                new StoreSupplierConnection("Stub", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("Stub-k7f3a9c2", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("Stub", ConnectionMode.GLOBAL, true, true));
        when(configurationManager.loadConfiguration(store, "Stub")).thenReturn(Map.of("url", "a"));
        when(configurationManager.loadConfiguration(store, "Stub-k7f3a9c2")).thenReturn(Map.of());

        // when / then
        assertThat(service.suppliersWithStoredConfiguration(store)).containsExactly("Stub");
    }

    @Test
    void disconnectRemovesOnlyTheNamedSupplier() {
        // given
        Store store = storeWith(true,
                new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("Kosatec", ConnectionMode.OWN, true, true),
                new StoreSupplierConnection("manual:Hurtownia X", ConnectionMode.MANUAL, true, true));
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of("Elko"), Set.of()));

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
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));

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
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));

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
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));

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
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));

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
                .thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(Set.of(), Set.of(), Set.of()));

        // when
        service.applyStoreSettings(store, submitted, true);

        // then
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        assertThat(captor.getValue().getEnabledCategories()).isEmpty();
    }

    @Test
    void connectOrUpdateKeepsNormalizedScheduleForOwnMode() {
        // given
        Store store = storeWith(true);
        registryHas("Acme");
        SupplierSelectionForm selection = new SupplierSelectionForm("Acme", ConnectionMode.OWN, true, true, "  0/30  9-17 * * ? * ");
        selection.setLabel("Acme");
        whenPersistSucceeds();

        // when
        service.connectOrUpdate(store, selection, Map.of());

        // then
        assertEquals("0/30 9-17 * * ? *", persistedConnection("Acme").getFeedSchedule());
    }

    @Test
    void connectOrUpdateDropsScheduleForGlobalMode() {
        // given
        Store store = storeWith(true);
        registryHas("Acme");
        SupplierSelectionForm selection = new SupplierSelectionForm("Acme", ConnectionMode.GLOBAL, true, true, "0 5 * * ? *");
        whenPersistSucceeds();

        // when
        service.connectOrUpdate(store, selection, Map.of());

        // then
        assertThat(persistedConnection("Acme").getFeedSchedule()).isNull();
    }

    @Test
    void connectOrUpdateStoresNullForBlankSchedule() {
        // given
        Store store = storeWith(true);
        registryHas("Acme");
        SupplierSelectionForm selection = new SupplierSelectionForm("Acme", ConnectionMode.OWN, true, true, "   ");
        selection.setLabel("Acme");
        whenPersistSucceeds();

        // when
        service.connectOrUpdate(store, selection, Map.of());

        // then
        assertThat(persistedConnection("Acme").getFeedSchedule()).isNull();
    }

    @Test
    void changingOnlyTheScheduleStillReachesThePersisterAsAChange() {
        // given -- the persister works out what to reschedule by diffing the store's current
        // configuration against the submitted one, so the submitted copy must carry the new
        // expression while the store still holds the old one
        StoreSupplierConnection stored = new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true);
        stored.setFeedSchedule("0 5 * * ? *");
        Store store = storeWith(true, stored);
        registryHas("Elko");
        SupplierSelectionForm selection = new SupplierSelectionForm("Elko", ConnectionMode.OWN, true, true, "0 7 * * ? *");
        selection.setIdentity("Elko");
        selection.setLabel("Elko");
        whenPersistSucceeds();

        // when
        service.connectOrUpdate(store, selection, Map.of());

        // then
        assertEquals("0 7 * * ? *", persistedConnection("Elko").getFeedSchedule());
        assertEquals("0 5 * * ? *",
                store.getFulfilmentConfiguration().getSupplierConnections().get(0).getFeedSchedule());
    }

    private void whenPersistSucceeds() {
        when(persister.persist(any(), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionPersister.PersistOutcome(true, Set.of(), Set.of(), Set.of()));
    }

    // Matched by type rather than exact identity: a freshly created OWN connection carries a
    // tokened identity ("Acme-xxxxxxxx"), not the bare type the test submitted.
    private StoreSupplierConnection persistedConnection(String supplierType) {
        ArgumentCaptor<FulfilmentConfiguration> captor = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(persister).persist(any(), captor.capture(), anyMap());
        return captor.getValue().getSupplierConnections().stream()
                .filter(connection -> SupplierIdentity.typeOf(connection.getSupplierName()).equals(supplierType))
                .findFirst()
                .orElseThrow();
    }
}
