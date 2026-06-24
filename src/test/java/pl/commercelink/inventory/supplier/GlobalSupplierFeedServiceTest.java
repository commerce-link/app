package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalSupplierFeedServiceTest {

    private GlobalSupplierFeedService serviceFor(SecretsManager secrets, InventoryRepository inventory) {
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));
        return new GlobalSupplierFeedService(factory, secrets, inventory);
    }

    @Test
    void loadFeedDecodesSingleValueGlobalSecretAndStores() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("feed");
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when
        service.loadFeed("Stub");

        // then
        ArgumentCaptor<byte[]> data = ArgumentCaptor.forClass(byte[].class);
        verify(inventory).store(eq("Stub"), data.capture(), eq("csv"));
        assertThat(new String(data.getValue())).isEqualTo("feed");
    }

    @Test
    void loadFeedDecodesJsonGlobalSecretAndStores() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("{\"url\":\"feed\"}");
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when
        service.loadFeed("Stub");

        // then
        ArgumentCaptor<byte[]> data = ArgumentCaptor.forClass(byte[].class);
        verify(inventory).store(eq("Stub"), data.capture(), eq("csv"));
        assertThat(new String(data.getValue())).isEqualTo("feed");
    }

    @Test
    void loadFeedDoesNothingForUnknownSupplier() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when
        service.loadFeed("Unknown");

        // then
        verify(inventory, never()).store(anyString(), any(byte[].class), anyString());
    }
}
