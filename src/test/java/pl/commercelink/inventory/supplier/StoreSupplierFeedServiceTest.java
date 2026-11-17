package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.StoreInventoryProvider;
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
    private StoreInventoryProvider storeInventoryProvider;

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

    private void supplierReturns(String storeId, byte[] data) {
        Store store = storeWithId(storeId);
        SupplierProvider supplier = () -> Optional.of(new FeedData(data, "csv"));
        when(storesRepository.findById(storeId)).thenReturn(store);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);
    }

    @Test
    void rebuildsTheStoreInventoryOnceTheFeedIsStored() throws ResourceDownloadException {
        // given
        byte[] data = "rows".getBytes();
        supplierReturns("store-1", data);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        org.mockito.InOrder order = inOrder(storeFeedRepository, storeInventoryProvider);
        order.verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
        order.verify(storeInventoryProvider).refresh("store-1");
    }

    @Test
    void firstImportStoresTheHashAfterTheRebuild() throws ResourceDownloadException {
        // given
        byte[] data = "rows".getBytes();
        supplierReturns("store-1", data);
        when(storeFeedRepository.storedHash("store-1", "Wortmann")).thenReturn(Optional.empty());

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        org.mockito.InOrder order = inOrder(storeFeedRepository, storeInventoryProvider);
        order.verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
        order.verify(storeInventoryProvider).refresh("store-1");
        order.verify(storeFeedRepository).storeHash("store-1", "Wortmann", FeedHash.of(data, "csv"));
    }

    @Test
    void unchangedFeedIsNotStoredAndTheSnapshotIsOnlyKept() throws ResourceDownloadException {
        // given
        byte[] data = "rows".getBytes();
        supplierReturns("store-1", data);
        when(storeFeedRepository.storedHash("store-1", "Wortmann")).thenReturn(Optional.of(FeedHash.of(data, "csv")));

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
        verify(storeInventoryProvider).keepOrRefresh("store-1");
        verify(storeInventoryProvider, never()).refresh(anyString());
        verify(storeFeedRepository).storeHash("store-1", "Wortmann", FeedHash.of(data, "csv"));
    }

    @Test
    void changedFeedIsStoredAndTheInventoryRebuilt() throws ResourceDownloadException {
        // given
        byte[] data = "new rows".getBytes();
        supplierReturns("store-1", data);
        when(storeFeedRepository.storedHash("store-1", "Wortmann")).thenReturn(Optional.of(FeedHash.of("rows".getBytes(), "csv")));

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
        verify(storeInventoryProvider).refresh("store-1");
        verify(storeInventoryProvider, never()).keepOrRefresh(anyString());
        verify(storeFeedRepository).storeHash("store-1", "Wortmann", FeedHash.of(data, "csv"));
    }

    @Test
    void failedRebuildLeavesTheHashUnwritten() {
        // given
        supplierReturns("store-1", "rows".getBytes());
        doThrow(new IllegalStateException("rates unavailable")).when(storeInventoryProvider).refresh("store-1");

        // when / then
        assertThrows(IllegalStateException.class, () -> service.loadStoreFeed("store-1", "Wortmann"));
        verify(storeFeedRepository, never()).storeHash(anyString(), anyString(), anyString());
    }

    @Test
    void sameBytesUnderAnotherExtensionAreAChangedFeed() {
        // when / then
        org.junit.jupiter.api.Assertions.assertNotEquals(FeedHash.of("rows".getBytes(), "csv"), FeedHash.of("rows".getBytes(), "xml"));
    }

    @Test
    void failedRebuildFailsTheImport() {
        // given
        supplierReturns("store-1", "rows".getBytes());
        doThrow(new IllegalStateException("rates unavailable")).when(storeInventoryProvider).refresh("store-1");

        // when / then
        assertThrows(IllegalStateException.class, () -> service.loadStoreFeed("store-1", "Wortmann"));
    }

    @Test
    void supplierWithoutAFeedLeavesTheInventoryAlone() throws ResourceDownloadException {
        // given
        Store store = storeWithId("store-1");
        SupplierProvider supplier = Optional::empty;
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verifyNoInteractions(storeInventoryProvider);
    }

    @Test
    void failsWhenTheStoreIsGone() throws ResourceDownloadException {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when / then
        assertThrows(SupplierFeedTargetMissingException.class,
                () -> service.loadStoreFeed("missing", "Wortmann"));
        verifyNoInteractions(supplierProviderFactory);
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void failsWhenNoProviderIsRegistered() throws ResourceDownloadException {
        // given
        Store store = storeWithId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(null);

        // when / then
        assertThrows(SupplierFeedTargetMissingException.class,
                () -> service.loadStoreFeed("store-1", "Wortmann"));
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
    void throwsWhenRequiredConfigurationNotReadable() {
        // given
        Store store = storeWithId("store-1");
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(requiredField()));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(descriptor);
        when(supplierProviderFactory.loadConfiguration(store, "Wortmann")).thenReturn(Map.of());

        // when / then
        assertThrows(SupplierConfigurationNotReadyException.class,
                () -> service.loadStoreFeed("store-1", "Wortmann"));
        verify(supplierProviderFactory, never()).get(any(), anyString());
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void downloadsWhenRequiredConfigurationReadable() throws Exception {
        // given
        Store store = storeWithId("store-1");
        byte[] data = "rows".getBytes();
        SupplierProvider supplier = () -> Optional.of(new FeedData(data, "csv"));
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(requiredField()));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(descriptor);
        when(supplierProviderFactory.loadConfiguration(store, "Wortmann")).thenReturn(Map.of("apiKey", "secret"));
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
    }

    @Test
    void doesNotCheckConfigurationWhenNoRequiredFields() throws Exception {
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
        verify(supplierProviderFactory, never()).loadConfiguration(any(), anyString());
        verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
    }

    @Test
    void doesNotCheckConfigurationWhenDescriptorUnknown() throws Exception {
        // given
        Store store = storeWithId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(null);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(null);

        // when / then
        assertThrows(SupplierFeedTargetMissingException.class,
                () -> service.loadStoreFeed("store-1", "Wortmann"));
        verify(supplierProviderFactory, never()).loadConfiguration(any(), anyString());
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void loadsATokenedConnectionThroughTheTypeDescriptorAndStoresUnderTheIdentity() throws Exception {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of());
        when(supplierProviderFactory.getDescriptor("Stub-k7f3a9c2")).thenReturn(descriptor);
        SupplierProvider provider = () -> Optional.of(FeedData.csv("rows".getBytes()));
        when(supplierProviderFactory.get(store, "Stub-k7f3a9c2")).thenReturn(provider);

        // when
        service.loadStoreFeed("store-1", "Stub-k7f3a9c2");

        // then
        verify(storeFeedRepository).store("store-1", "Stub-k7f3a9c2", "rows".getBytes(), "csv");
    }
}
