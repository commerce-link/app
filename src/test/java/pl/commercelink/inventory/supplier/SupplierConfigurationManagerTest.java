package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.api.SupplierConfigField;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierConfigurationManagerTest {

    @Mock
    private SecretsManager secretsManager;

    @InjectMocks
    private SupplierConfigurationManager manager;

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
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);
        when(secretsManager.getSecret("store-1-wortmann", Map.class))
                .thenReturn(Map.of("host", "ftp.x", "password", "sekret"));

        // when
        Map<String, String> ui = manager.getConfigurationForUI(storeWithId("store-1"), "Wortmann", ftpFields());

        // then
        assertEquals("ftp.x", ui.get("host"));
        assertEquals("", ui.get("password"));
    }

    @Test
    void saveConfigurationPreservesExistingPasswordWhenBlank() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);
        when(secretsManager.getSecret("store-1-wortmann", Map.class))
                .thenReturn(Map.of("host", "ftp.old", "password", "kept"));

        // when
        manager.saveConfiguration(storeWithId("store-1"), "Wortmann", ftpFields(),
                Map.of("host", "ftp.new", "password", ""));

        // then
        verify(secretsManager).updateSecret(eq("store-1-wortmann"),
                eq(Map.of("host", "ftp.new", "password", "kept")));
    }

    @Test
    void snapshotCapturesExistingSecret() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);
        when(secretsManager.getSecret("store-1-wortmann", Map.class))
                .thenReturn(Map.of("host", "ftp.x", "password", "sekret"));

        // when
        SupplierConfigurationManager.SecretSnapshot snapshot =
                manager.snapshot(storeWithId("store-1"), "Wortmann");

        // then
        assertTrue(snapshot.existed());
        assertEquals(Map.of("host", "ftp.x", "password", "sekret"), snapshot.value());
    }

    @Test
    void snapshotMarksMissingSecretAsAbsent() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);

        // when
        SupplierConfigurationManager.SecretSnapshot snapshot =
                manager.snapshot(storeWithId("store-1"), "Wortmann");

        // then
        assertFalse(snapshot.existed());
        assertNull(snapshot.value());
    }

    @Test
    void restorePutsBackPreviouslyExistingSecret() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);
        SupplierConfigurationManager.SecretSnapshot snapshot =
                new SupplierConfigurationManager.SecretSnapshot(true, Map.of("host", "ftp.x", "password", "sekret"));

        // when
        manager.restore(storeWithId("store-1"), "Wortmann", snapshot);

        // then
        verify(secretsManager).updateSecret(eq("store-1-wortmann"),
                eq(Map.of("host", "ftp.x", "password", "sekret")));
    }

    @Test
    void restoreRecreatesPreviouslyExistingSecretWhenTargetNowMissing() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);
        SupplierConfigurationManager.SecretSnapshot snapshot =
                new SupplierConfigurationManager.SecretSnapshot(true, Map.of("host", "ftp.x", "password", "sekret"));

        // when
        manager.restore(storeWithId("store-1"), "Wortmann", snapshot);

        // then
        verify(secretsManager).createSecret(eq("store-1-wortmann"),
                eq(Map.of("host", "ftp.x", "password", "sekret")));
    }

    @Test
    void restoreDeletesSecretThatDidNotExistBefore() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);
        SupplierConfigurationManager.SecretSnapshot snapshot =
                new SupplierConfigurationManager.SecretSnapshot(false, null);

        // when
        manager.restore(storeWithId("store-1"), "Wortmann", snapshot);

        // then
        verify(secretsManager).deleteSecret("store-1-wortmann");
    }

    @Test
    void restoreDoesNothingWhenSecretNeverExistedAndIsStillAbsent() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);
        SupplierConfigurationManager.SecretSnapshot snapshot =
                new SupplierConfigurationManager.SecretSnapshot(false, null);

        // when
        manager.restore(storeWithId("store-1"), "Wortmann", snapshot);

        // then
        verify(secretsManager, never()).createSecret(anyString(), any());
        verify(secretsManager, never()).updateSecret(anyString(), any());
        verify(secretsManager, never()).deleteSecret(anyString());
    }

    @Test
    void saveConfigurationCreatesSecretWhenAllRequiredFieldsPresentAndNoneStored() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);

        // when
        manager.saveConfiguration(storeWithId("store-1"), "Wortmann", ftpFields(),
                Map.of("host", "ftp.new", "password", "pass"));

        // then
        verify(secretsManager).createSecret(eq("store-1-wortmann"),
                eq(Map.of("host", "ftp.new", "password", "pass")));
    }

    @Test
    void saveConfigurationSkipsWhenRequiredFieldsMissing() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);

        // when
        manager.saveConfiguration(storeWithId("store-1"), "Wortmann", ftpFields(),
                Map.of("host", "ftp.new", "password", ""));

        // then
        verify(secretsManager, never()).createSecret(anyString(), any());
    }

    @Test
    void deleteConfigurationRemovesStoredSecret() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(true);

        // when
        manager.deleteConfiguration(storeWithId("store-1"), "Wortmann");

        // then
        verify(secretsManager).deleteSecret("store-1-wortmann");
    }
}
