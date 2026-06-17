package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreScopedFeedLoaderTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private DataCorrection dataCorrection;
    @Mock
    private DataCleanup dataCleanup;
    @Mock
    private pl.commercelink.taxonomy.TaxonomyCache taxonomyCache;

    @InjectMocks
    private CsvProductFeedLoader loader;

    @Test
    void csvStoreOverloadReturnsEmptyWhenStoreFeedNotReadable() {
        when(inventoryRepository.canRead("store-1", "Action")).thenReturn(false);

        List<?> result = loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action");

        assertTrue(result.isEmpty());
        verify(inventoryRepository).canRead("store-1", "Action");
    }

    @Test
    void csvStoreOverloadReadsStoreScopedKey() throws Exception {
        when(inventoryRepository.canRead("store-1", "Action")).thenReturn(true);
        Reader reader = new StringReader("");
        when(inventoryRepository.read(eq("store-1"), eq("Action"), eq("csv"))).thenReturn(reader);
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action");

        verify(inventoryRepository).read("store-1", "Action", "csv");
    }
}
