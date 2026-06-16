package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierConfigField;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SupplierConfigurationManagerTest {

    private final SecretsManager secretsManager = mock(SecretsManager.class);
    private final SupplierConfigurationManager manager = new SupplierConfigurationManager(secretsManager);

    private Store storeWithId(String id) {
        Store store = new Store();
        store.setStoreId(id);
        return store;
    }

    private List<SupplierConfigField> ftpFields() {
        return List.of(
                new SupplierConfigField("host", "Host", SupplierConfigField.FieldType.TEXT, true, ""),
                new SupplierConfigField("password", "Password", SupplierConfigField.FieldType.PASSWORD, true, "")
        );
    }

    @Test
    void getConfigurationForUiMasksPasswordFields() {
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);
        when(secretsManager.getSecret("store-1-wortmann", Map.class))
                .thenReturn(Map.of("host", "ftp.x", "password", "sekret"));

        Map<String, String> ui = manager.getConfigurationForUI(storeWithId("store-1"), "Wortmann", ftpFields());

        assertEquals("ftp.x", ui.get("host"));
        assertEquals("", ui.get("password"));
    }

    @Test
    void saveConfigurationPreservesExistingPasswordWhenBlank() {
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);
        when(secretsManager.getSecret("store-1-wortmann", Map.class))
                .thenReturn(Map.of("host", "ftp.old", "password", "kept"));

        manager.saveConfiguration(storeWithId("store-1"), "Wortmann", ftpFields(),
                Map.of("host", "ftp.new", "password", ""));

        verify(secretsManager).updateSecret(eq("store-1-wortmann"),
                eq(Map.of("host", "ftp.new", "password", "kept")));
    }
}
