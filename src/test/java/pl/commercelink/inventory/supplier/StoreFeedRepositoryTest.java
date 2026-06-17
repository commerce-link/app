package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.storage.FileStorage;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreFeedRepositoryTest {

    private final FileStorage fileStorage = mock(FileStorage.class);
    private final StoreFeedRepository repository = new StoreFeedRepository(fileStorage, "commercelink-stores");

    @Test
    void storeUsesStoreNamespacedKey() {
        byte[] data = "rows".getBytes();

        repository.store("oh4d5y15it", "Acme", data, "csv");

        verify(fileStorage).put("commercelink-stores", "oh4d5y15it/supplier-feeds/acme-feed.csv", data);
    }

    @Test
    void storeLowercasesSupplierNameAndKeepsStoreIdCase() {
        byte[] data = "rows".getBytes();

        repository.store("Oh4D", "ACTION", data, "xml");

        verify(fileStorage).put("commercelink-stores", "Oh4D/supplier-feeds/action-feed.xml", data);
    }

    @Test
    void canReadChecksStoreNamespacedKey() {
        when(fileStorage.canRead("commercelink-stores", "oh4d5y15it/supplier-feeds/acme-feed.csv")).thenReturn(true);

        assertTrue(repository.canRead("oh4d5y15it", "Acme", "csv"));
    }

    @Test
    void deleteRemovesAllFeedExtensionsForSupplier() {
        repository.delete("oh4d5y15it", "Acme");

        verify(fileStorage).deleteAll("commercelink-stores", "oh4d5y15it/supplier-feeds/acme-feed.");
    }
}
