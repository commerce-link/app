package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.StoreInventoryProvider;
import pl.commercelink.inventory.StoreInventoryRefreshPublisher;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierFeedServiceTest {

    private static final String BUCKET = "commercelink-stores";
    private static final String FEED_KEY = "store-1/supplier-feeds/wortmann-feed.csv";
    private static final String HASH_KEY = "store-1/supplier-feeds/wortmann-feed.sha256";
    private static final Instant STORED_AT = Instant.parse("2026-11-14T02:00:00Z");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private StoreInventoryProvider storeInventoryProvider;
    @Mock
    private StoreInventoryRefreshPublisher storeInventoryRefresh;

    private StoreSupplierFeedService service;

    @BeforeEach
    void setUp() {
        StoreFeedRepository storeFeedRepository = new StoreFeedRepository(fileStorage, BUCKET);
        service = new StoreSupplierFeedService(storesRepository, supplierProviderFactory, storeFeedRepository,
                storeInventoryProvider, storeInventoryRefresh);
    }

    private void lastImportStored(byte[] data) {
        when(fileStorage.findLastModified(BUCKET, HASH_KEY)).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.findLastModified(BUCKET, FEED_KEY)).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.getBytes(BUCKET, HASH_KEY)).thenReturn(FeedHash.of(data, "csv").getBytes(StandardCharsets.UTF_8));
    }

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
        verify(fileStorage).put(BUCKET, FEED_KEY, data);
    }

    private void supplierReturns(String storeId, byte[] data) {
        Store store = storeWithId(storeId);
        SupplierProvider supplier = () -> Optional.of(new FeedData(data, "csv"));
        when(storesRepository.findById(storeId)).thenReturn(store);
        when(supplierProviderFactory.get(store, "Wortmann")).thenReturn(supplier);
    }

    @Test
    void unchangedFeedOnlyKeepsARecentSnapshot() throws ResourceDownloadException {
        // given
        byte[] data = "rows".getBytes();
        supplierReturns("store-1", data);
        lastImportStored(data);
        when(storeInventoryProvider.keepIfRecent("store-1")).thenReturn(true);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeInventoryProvider).keepIfRecent("store-1");
        verify(storeInventoryRefresh, never()).request(anyString());
    }

    @Test
    void unchangedFeedWithAnOldOrMissingSnapshotAsksForARebuild() throws ResourceDownloadException {
        // given
        byte[] data = "rows".getBytes();
        supplierReturns("store-1", data);
        lastImportStored(data);
        when(storeInventoryProvider.keepIfRecent("store-1")).thenReturn(false);

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeInventoryRefresh).request("store-1");
    }

    @Test
    void changedFeedAsksForARebuildWithoutCheckingTheSnapshot() throws ResourceDownloadException {
        // given
        byte[] data = "new rows".getBytes();
        supplierReturns("store-1", data);
        lastImportStored("rows".getBytes());

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeInventoryRefresh).request("store-1");
        verify(storeInventoryProvider, never()).keepIfRecent(anyString());
    }

    @Test
    void requestThatCannotBeSentLeavesTheHashUnwritten() {
        // given
        supplierReturns("store-1", "rows".getBytes());
        doThrow(new IllegalStateException("queue unavailable")).when(storeInventoryRefresh).request("store-1");

        // when / then
        assertThrows(IllegalStateException.class, () -> service.loadStoreFeed("store-1", "Wortmann"));
        verify(fileStorage, never()).put(eq(BUCKET), eq(HASH_KEY), any(byte[].class));
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
        verifyNoInteractions(storeInventoryProvider, storeInventoryRefresh);
    }

    @Test
    void failsWhenTheStoreIsGone() throws ResourceDownloadException {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when / then
        assertThrows(SupplierFeedTargetMissingException.class,
                () -> service.loadStoreFeed("missing", "Wortmann"));
        verifyNoInteractions(supplierProviderFactory);
        verifyNoInteractions(fileStorage);
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
        verifyNoInteractions(fileStorage);
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
        verifyNoInteractions(fileStorage);
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
        verifyNoInteractions(fileStorage);
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
        verify(fileStorage).put(BUCKET, FEED_KEY, data);
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
        verify(fileStorage).put(BUCKET, FEED_KEY, data);
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
        verifyNoInteractions(fileStorage);
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
        verify(fileStorage).put(BUCKET, "store-1/supplier-feeds/stub-k7f3a9c2-feed.csv", "rows".getBytes());
    }
}
