package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierFeedServiceTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private StoreFeedRepository storeFeedRepository;
    @Mock
    private StoreSupplierFeedService.Sleeper sleeper;

    @InjectMocks
    private StoreSupplierFeedService service;

    private Store storeWithId(String id) {
        Store store = new Store();
        store.setStoreId(id);
        return store;
    }

    private ProviderField requiredField() {
        return new ProviderField("apiKey", "API key", ProviderField.FieldType.PASSWORD, true, null);
    }

    @Test
    void downloadsFeedToPerStoreKey() throws ResourceDownloadException {
        // given
        Store store = storeWithId("store-1");
        byte[] data = "rows".getBytes();
        SupplierProvider supplier = () -> Optional.of(new FeedData(data, "csv"));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
    }

    @Test
    void doesNothingWhenStoreNotFound() throws ResourceDownloadException {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when
        service.loadStoreFeed("missing", "Wortmann");

        // then
        verifyNoInteractions(supplierProviderFactory);
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void doesNothingWhenNoSupplierResolved() throws ResourceDownloadException {
        // given
        Store store = storeWithId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(null);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void doesNotStoreWhenDownloadReturnsEmpty() throws ResourceDownloadException {
        // given
        Store store = storeWithId("store-1");
        SupplierProvider supplier = Optional::empty;
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void waitsUntilConfigurationBecomesReadable() throws Exception {
        // given
        Store store = storeWithId("store-1");
        byte[] data = "rows".getBytes();
        SupplierProvider supplier = () -> Optional.of(new FeedData(data, "csv"));
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(requiredField()));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(descriptor);
        when(supplierProviderFactory.loadConfiguration(store, "Wortmann"))
                .thenReturn(Map.of(), Map.of(), Map.of("apiKey", "secret"));
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(sleeper, times(2)).sleep(2000L);
        verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
    }

    @Test
    void failsWhenConfigurationNeverBecomesReadable() throws Exception {
        // given
        Store store = storeWithId("store-1");
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(requiredField()));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(descriptor);
        when(supplierProviderFactory.loadConfiguration(store, "Wortmann")).thenReturn(Map.of());

        // when / then
        assertThrows(ResourceDownloadException.class, () -> service.loadStoreFeed("store-1", "Wortmann"));
        verify(sleeper, times(4)).sleep(2000L);
        verify(supplierProviderFactory, never()).get(any(), anyString());
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void doesNotWaitWhenSupplierHasNoRequiredFields() throws Exception {
        // given
        Store store = storeWithId("store-1");
        byte[] data = "rows".getBytes();
        SupplierProvider supplier = () -> Optional.of(new FeedData(data, "csv"));
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of());
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(descriptor);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verifyNoInteractions(sleeper);
        verify(supplierProviderFactory, never()).loadConfiguration(any(), anyString());
        verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
    }

    @Test
    void doesNotWaitWhenDescriptorUnknown() throws Exception {
        // given
        Store store = storeWithId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(null);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(null);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verifyNoInteractions(sleeper);
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void failsWhenInterruptedWhileWaiting() throws Exception {
        // given
        Store store = storeWithId("store-1");
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(requiredField()));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(descriptor);
        when(supplierProviderFactory.loadConfiguration(store, "Wortmann")).thenReturn(Map.of());
        doThrow(new InterruptedException()).when(sleeper).sleep(2000L);

        // when / then
        assertThrows(ResourceDownloadException.class, () -> service.loadStoreFeed("store-1", "Wortmann"));
        assertTrue(Thread.interrupted());
    }
}
