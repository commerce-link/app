package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.CsvRowParser;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreScopedFeedLoaderTest {

    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final DataCorrection dataCorrection = mock(DataCorrection.class);
    private final DataCleanup dataCleanup = mock(DataCleanup.class);
    private final pl.commercelink.taxonomy.TaxonomyCache taxonomyCache = mock(pl.commercelink.taxonomy.TaxonomyCache.class);

    @Test
    void csvStoreOverloadReturnsEmptyWhenStoreFeedNotReadable() {
        CsvProductFeedLoader loader = new CsvProductFeedLoader(inventoryRepository, dataCorrection, dataCleanup, taxonomyCache);
        when(inventoryRepository.canRead("store-1", "Action")).thenReturn(false);

        List<?> result = loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action");

        assertTrue(result.isEmpty());
        verify(inventoryRepository).canRead("store-1", "Action");
    }

    @Test
    void csvStoreOverloadReadsStoreScopedKey() throws Exception {
        CsvProductFeedLoader loader = new CsvProductFeedLoader(inventoryRepository, dataCorrection, dataCleanup, taxonomyCache);
        when(inventoryRepository.canRead("store-1", "Action")).thenReturn(true);
        Reader reader = new StringReader("");
        when(inventoryRepository.read(eq("store-1"), eq("Action"), eq("csv"))).thenReturn(reader);
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action");

        verify(inventoryRepository).read("store-1", "Action", "csv");
    }
}
