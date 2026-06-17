package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.api.SupplierConfigField;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

    private SupplierDescriptor descriptor(String name, boolean hasFields) {
        SupplierDescriptor descriptor = mock(SupplierDescriptor.class);
        SupplierInfo info = mock(SupplierInfo.class);
        when(info.name()).thenReturn(name);
        when(descriptor.supplierInfo()).thenReturn(info);
        when(descriptor.configurationFields())
                .thenReturn(hasFields ? List.of(SupplierConfigField.url()) : List.of());
        return descriptor;
    }

    @Test
    void superAdminControlsGlobalFlagWhileNonAdminKeepsStoredValue() {
        Store stored = storeWith(true);

        assertFalse(service.resolveCanUseGlobalSuppliers(stored, false, true));
        assertTrue(service.resolveCanUseGlobalSuppliers(stored, false, false));
    }

    @Test
    void buildsConnectionsForcingOwnWhenGlobalNotAllowed() {
        List<SupplierSelectionForm> selections = List.of(
                new SupplierSelectionForm("Acme", true, ConnectionMode.GLOBAL),
                new SupplierSelectionForm("Wortmann", false, ConnectionMode.OWN));

        List<StoreSupplierConnection> result = service.buildConnections(selections, false);

        assertEquals(1, result.size());
        assertEquals("Acme", result.get(0).getSupplierName());
        assertEquals(ConnectionMode.OWN, result.get(0).getMode());
    }

    @Test
    void buildsConnectionsHonouringSelectedModeWhenGlobalAllowed() {
        List<SupplierSelectionForm> selections = List.of(
                new SupplierSelectionForm("Acme", true, ConnectionMode.GLOBAL),
                new SupplierSelectionForm("Wortmann", true, ConnectionMode.OWN));

        List<StoreSupplierConnection> result = service.buildConnections(selections, true);

        assertEquals(2, result.size());
        assertEquals(ConnectionMode.GLOBAL, result.get(0).getMode());
        assertEquals(ConnectionMode.OWN, result.get(1).getMode());
    }

    @Test
    void persistSavesOwnConfigurationsWithFields() {
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        SupplierDescriptor acme = descriptor("Acme", true);
        when(supplierRegistry.getAllDescriptors()).thenReturn(List.of(acme));
        Map<String, Map<String, String>> submittedConfig = Map.of("Acme", Map.of("url", "https://feed"));

        service.persistConfigurations(existing, submitted, submittedConfig);

        verify(configurationManager).saveConfiguration(eq(existing), eq("Acme"), anyList(), eq(Map.of("url", "https://feed")));
        verify(configurationManager, never()).deleteConfiguration(any(), anyString());
    }

    @Test
    void persistDeletesOrphanedSecretWhenSupplierLeavesOwnMode() {
        Store existing = storeWith(true, new StoreSupplierConnection("Acme", ConnectionMode.OWN));
        FulfilmentConfiguration submitted = configWith(true, new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL));
        SupplierDescriptor acme = descriptor("Acme", true);
        when(supplierRegistry.getAllDescriptors()).thenReturn(List.of(acme));

        service.persistConfigurations(existing, submitted, Map.of());

        verify(configurationManager).deleteConfiguration(existing, "Acme");
        verify(configurationManager, never()).saveConfiguration(any(), anyString(), anyList(), any());
    }

    @Test
    void applyDoesNotPersistWhenValidationFails() {
        Store existing = storeWith(true);
        FulfilmentConfiguration submitted = configWith(true);
        List<SupplierSelectionForm> selections = List.of(new SupplierSelectionForm("Acme", true, ConnectionMode.OWN));
        when(validator.validate(anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(List.of("Supplier Acme requires field Feed URL."));

        List<String> errors = service.apply(existing, submitted, selections, Map.of(), true);

        assertFalse(errors.isEmpty());
        verify(configurationManager, never()).saveConfiguration(any(), anyString(), anyList(), any());
        verify(configurationManager, never()).deleteConfiguration(any(), anyString());
    }
}
