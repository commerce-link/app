package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.storage.FileStorage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRepositoryStoreKeyTest {

    private final FileStorage fileStorage = mock(FileStorage.class);
    private final InventoryRepository repository = new InventoryRepository(fileStorage, "feeds-bucket");

    @Test
    void storeForStoreUsesStorePrefixedKey() {
        byte[] data = "rows".getBytes();

        repository.store("store-1", "Wortmann", data, "csv");

        verify(fileStorage).put("feeds-bucket", "store-1-wortmann-feed.csv", data);
    }

    @Test
    void storeForStoreLowercasesSupplierNameAndKeepsStoreIdCase() {
        byte[] data = "rows".getBytes();

        repository.store("Store-1", "ACTION", data, "xml");

        verify(fileStorage).put("feeds-bucket", "Store-1-action-feed.xml", data);
    }

    @Test
    void canReadForStoreChecksStorePrefixedCsvKey() {
        when(fileStorage.canRead("feeds-bucket", "store-1-wortmann-feed.csv")).thenReturn(true);

        assertTrue(repository.canRead("store-1", "Wortmann"));
    }
}
