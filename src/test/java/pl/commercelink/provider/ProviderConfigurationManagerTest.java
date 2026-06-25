package pl.commercelink.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderConfigurationManagerTest {

    @Mock
    private SecretsManager secretsManager;

    @InjectMocks
    private ProviderConfigurationManager manager;

    private Store storeWithId(String id) {
        Store store = new Store();
        store.setStoreId(id);
        return store;
    }

    @Test
    void snapshotCapturesExistingSecretAndRestoreRewritesIt() {
        // given
        Store store = storeWithId("store-1");
        when(secretsManager.exists("store-1-acme")).thenReturn(true);
        when(secretsManager.getSecret("store-1-acme", Map.class)).thenReturn(Map.of("url", "u"));

        // when
        ProviderConfigurationManager.SecretSnapshot snap = manager.snapshot(store, "Acme");

        // then
        assertThat(snap.existed()).isTrue();
        assertThat(snap.value()).containsEntry("url", "u");

        // when
        manager.restore(store, "Acme", snap);

        // then
        verify(secretsManager).updateSecret("store-1-acme", Map.of("url", "u"));
    }

    @Test
    void snapshotMarksMissingSecretAsAbsent() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);

        // when
        ProviderConfigurationManager.SecretSnapshot snapshot =
                manager.snapshot(storeWithId("store-1"), "Wortmann");

        // then
        assertFalse(snapshot.existed());
        assertNull(snapshot.value());
    }

    @Test
    void restoreRecreatesPreviouslyExistingSecretWhenTargetNowMissing() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);
        ProviderConfigurationManager.SecretSnapshot snapshot =
                new ProviderConfigurationManager.SecretSnapshot(true, Map.of("host", "ftp.x", "password", "sekret"));

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
        ProviderConfigurationManager.SecretSnapshot snapshot =
                new ProviderConfigurationManager.SecretSnapshot(false, null);

        // when
        manager.restore(storeWithId("store-1"), "Wortmann", snapshot);

        // then
        verify(secretsManager).deleteSecret("store-1-wortmann");
    }

    @Test
    void restoreDoesNothingWhenSecretNeverExistedAndIsStillAbsent() {
        // given
        when(secretsManager.exists("store-1-wortmann")).thenReturn(false);
        ProviderConfigurationManager.SecretSnapshot snapshot =
                new ProviderConfigurationManager.SecretSnapshot(false, null);

        // when
        manager.restore(storeWithId("store-1"), "Wortmann", snapshot);

        // then
        verify(secretsManager, never()).createSecret(anyString(), any());
        verify(secretsManager, never()).updateSecret(anyString(), any());
        verify(secretsManager, never()).deleteSecret(anyString());
    }

    @Test
    void getConfigurationForUiMasksPasswordFields() {
        // given
        Store store = storeWithId("store-1");
        ProviderDescriptor<?> descriptor = mock(ProviderDescriptor.class);
        when(descriptor.name()).thenReturn("acme");
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("password", "Password", ProviderField.FieldType.PASSWORD, false, ""),
                new ProviderField("host", "Host", ProviderField.FieldType.TEXT, true, "")
        ));
        when(secretsManager.exists("store-1-acme")).thenReturn(true);
        when(secretsManager.getSecret("store-1-acme", Map.class))
                .thenReturn(Map.of("password", "s3cr3t", "host", "ftp.example.com"));

        // when
        Map<String, String> result = manager.getConfigurationForUI(store, "acme", descriptor);

        // then
        assertThat(result).containsEntry("password", "").containsEntry("host", "ftp.example.com");
    }

    @Test
    void saveConfigurationPreservesExistingPasswordWhenBlank() {
        // given
        Store store = storeWithId("store-1");
        ProviderDescriptor<?> descriptor = mock(ProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("password", "Password", ProviderField.FieldType.PASSWORD, false, ""),
                new ProviderField("host", "Host", ProviderField.FieldType.TEXT, true, "")
        ));
        when(secretsManager.exists("store-1-acme")).thenReturn(true);
        when(secretsManager.getSecret("store-1-acme", Map.class))
                .thenReturn(Map.of("password", "stored-password", "host", "ftp.example.com"));

        // when
        manager.saveConfiguration(store, "acme", descriptor, Map.of("host", "ftp.example.com", "password", ""));

        // then
        verify(secretsManager).updateSecret(
                eq("store-1-acme"),
                eq(Map.of("host", "ftp.example.com", "password", "stored-password")));
    }

    @Test
    void saveConfigurationCreatesSecretWhenAllRequiredFieldsPresentAndNoneStored() {
        // given
        Store store = storeWithId("store-1");
        ProviderDescriptor<?> descriptor = mock(ProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("password", "Password", ProviderField.FieldType.PASSWORD, false, ""),
                new ProviderField("host", "Host", ProviderField.FieldType.TEXT, true, "")
        ));
        when(secretsManager.exists("store-1-acme")).thenReturn(false);

        // when
        manager.saveConfiguration(store, "acme", descriptor,
                Map.of("host", "ftp.example.com", "password", "s3cr3t"));

        // then
        verify(secretsManager).createSecret(
                eq("store-1-acme"),
                eq(Map.of("host", "ftp.example.com", "password", "s3cr3t")));
    }

    @Test
    void saveConfigurationSkipsWhenRequiredFieldsMissing() {
        // given
        Store store = storeWithId("store-1");
        ProviderDescriptor<?> descriptor = mock(ProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("password", "Password", ProviderField.FieldType.PASSWORD, false, ""),
                new ProviderField("host", "Host", ProviderField.FieldType.TEXT, true, "")
        ));
        when(secretsManager.exists("store-1-acme")).thenReturn(false);

        // when
        manager.saveConfiguration(store, "acme", descriptor, Map.of("host", "", "password", "s3cr3t"));

        // then
        verify(secretsManager, never()).createSecret(anyString(), any());
        verify(secretsManager, never()).updateSecret(anyString(), any());
    }

    @Test
    void deleteConfigurationRemovesStoredSecret() {
        // given
        Store store = storeWithId("store-1");
        when(secretsManager.exists("store-1-acme")).thenReturn(true);

        // when
        manager.deleteConfiguration(store, "acme");

        // then
        verify(secretsManager).deleteSecret("store-1-acme");
    }
}
