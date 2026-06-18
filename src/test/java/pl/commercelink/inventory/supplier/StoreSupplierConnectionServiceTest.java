package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierConnectionServiceTest {

    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierConfigurationManager configurationManager;
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
    void buildsConnectionsForcingOwnWhenGlobalNotAllowed() {
        // given
        List<SupplierSelectionForm> selections = List.of(
                new SupplierSelectionForm("Acme", true, ConnectionMode.GLOBAL),
                new SupplierSelectionForm("Wortmann", false, ConnectionMode.OWN));

        // when
        List<StoreSupplierConnection> result = service.buildConnections(selections, false);

        // then
        assertEquals(1, result.size());
        assertEquals("Acme", result.get(0).getSupplierName());
        assertEquals(ConnectionMode.OWN, result.get(0).getMode());
    }

    @Test
    void buildsConnectionsHonouringSelectedModeWhenGlobalAllowed() {
        // given
        List<SupplierSelectionForm> selections = List.of(
                new SupplierSelectionForm("Acme", true, ConnectionMode.GLOBAL),
                new SupplierSelectionForm("Wortmann", true, ConnectionMode.OWN));

        // when
        List<StoreSupplierConnection> result = service.buildConnections(selections, true);

        // then
        assertEquals(2, result.size());
        assertEquals(ConnectionMode.GLOBAL, result.get(0).getMode());
        assertEquals(ConnectionMode.OWN, result.get(1).getMode());
    }

    @Test
    void applyReturnsValidationErrorsWithoutDelegatingToPersister() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true);
        List<SupplierSelectionForm> selections = List.of(new SupplierSelectionForm("Acme", true, ConnectionMode.OWN));
        when(validator.validate(anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(List.of("Supplier Acme requires field Feed URL."));

        // when
        List<String> errors = service.apply(existing, submitted, selections, Map.of(), true);

        // then
        assertFalse(errors.isEmpty());
        verify(persister, never()).persist(any(), any(), any());
    }

    @Test
    void applyDelegatesToPersisterAndSucceeds() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true);
        List<SupplierSelectionForm> selections = List.of(new SupplierSelectionForm("Acme", true, ConnectionMode.OWN));
        when(validator.validate(anyBoolean(), anyList(), any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), any())).thenReturn(true);

        // when
        List<String> errors = service.apply(existing, submitted, selections, Map.of("Acme", Map.of("url", "https://feed")), true);

        // then
        assertTrue(errors.isEmpty());
        verify(persister).persist(existing, submitted, Map.of("Acme", Map.of("url", "https://feed")));
    }

    @Test
    void applyReturnsFailureWhenPersisterFails() {
        // given
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true);
        List<SupplierSelectionForm> selections = List.of(new SupplierSelectionForm("Acme", true, ConnectionMode.OWN));
        when(validator.validate(anyBoolean(), anyList(), any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), any())).thenReturn(false);

        // when
        List<String> errors = service.apply(existing, submitted, selections, Map.of("Acme", Map.of("url", "https://feed")), true);

        // then
        assertFalse(errors.isEmpty());
    }
}
