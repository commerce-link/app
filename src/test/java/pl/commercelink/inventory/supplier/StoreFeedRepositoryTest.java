package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.storage.FileStorage;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreFeedRepositoryTest {

    private final FileStorage fileStorage = mock(FileStorage.class);
    private final StoreFeedRepository repository = new StoreFeedRepository(fileStorage, "commercelink-stores");

    @Test
    void storeUsesStoreNamespacedKey() {
        // given
        byte[] data = "rows".getBytes();

        // when
        repository.store("oh4d5y15it", "Acme", data, "csv");

        // then
        verify(fileStorage).put("commercelink-stores", "oh4d5y15it/supplier-feeds/acme-feed.csv", data);
    }

    @Test
    void storeLowercasesSupplierNameAndKeepsStoreIdCase() {
        // given
        byte[] data = "rows".getBytes();

        // when
        repository.store("Oh4D", "ACTION", data, "xml");

        // then
        verify(fileStorage).put("commercelink-stores", "Oh4D/supplier-feeds/action-feed.xml", data);
    }

    @Test
    void canReadChecksStoreNamespacedKey() {
        // given
        when(fileStorage.canRead("commercelink-stores", "oh4d5y15it/supplier-feeds/acme-feed.csv")).thenReturn(true);

        // when / then
        assertTrue(repository.canRead("oh4d5y15it", "Acme", "csv"));
    }

    @Test
    void deleteRemovesAllFeedExtensionsForSupplier() {
        // when
        repository.delete("oh4d5y15it", "Acme");

        // then
        verify(fileStorage).deleteAll("commercelink-stores", "oh4d5y15it/supplier-feeds/acme-feed.");
    }

    @Test
    void readUsesStoreNamespacedKey() throws IOException {
        // given
        InputStreamReader content = new InputStreamReader(new ByteArrayInputStream("rows".getBytes()));
        when(fileStorage.get("commercelink-stores", "store-1/supplier-feeds/acme-feed.csv")).thenReturn(content);

        // when
        String result = new BufferedReader(repository.read("store-1", "Acme", "csv")).readLine();

        // then
        assertEquals("rows", result);
        verify(fileStorage).get("commercelink-stores", "store-1/supplier-feeds/acme-feed.csv");
    }

    @Test
    void readPropagatesWhenObjectMissing() {
        // given
        when(fileStorage.get("commercelink-stores", "store-1/supplier-feeds/acme-feed.csv"))
                .thenThrow(NoSuchKeyException.builder().build());

        // when / then
        assertThrows(NoSuchKeyException.class, () -> repository.read("store-1", "Acme", "csv"));
    }
}
