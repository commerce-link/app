package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.ProductCategory;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreScopedFeedLoaderTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StoreFeedRepository storeFeedRepository;
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
        when(storeFeedRepository.canRead("store-1", "Action", "csv")).thenReturn(false);

        List<?> result = loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action", 0);

        assertTrue(result.isEmpty());
        verify(storeFeedRepository).canRead("store-1", "Action", "csv");
    }

    @Test
    void csvStoreOverloadReadsStoreScopedKey() throws Exception {
        when(storeFeedRepository.canRead("store-1", "Action", "csv")).thenReturn(true);
        Reader reader = new StringReader("");
        when(storeFeedRepository.read("store-1", "Action", "csv")).thenReturn(reader);
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action", 0);

        verify(storeFeedRepository).read("store-1", "Action", "csv");
    }

    @Test
    void csvStoreOverloadBumpsTaxonomyScoreByPenalty() throws Exception {
        when(storeFeedRepository.canRead("store-1", "Action", "csv")).thenReturn(true);
        when(storeFeedRepository.read("store-1", "Action", "csv")).thenReturn(new StringReader("header\nrow"));

        CsvRowParser parser = mock(CsvRowParser.class);
        InventoryItem item = new InventoryItem("5900000000001", "MFN1", 10.0, "PLN", 1, 1, "Action", true, true, false);
        Taxonomy taxonomy = new Taxonomy("5900000000001", "MFN1", "Brand", "Name", ProductCategory.CPU, 5);
        when(parser.tryParse(any())).thenReturn(Optional.of(new ParsedRow(item, taxonomy)));
        when(dataCorrection.run(item)).thenReturn(item);
        when(dataCorrection.run(taxonomy)).thenReturn(taxonomy);
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        loader.fetch(parser, ';', "store-1", "Action", 1000);

        ArgumentCaptor<Taxonomy> captor = ArgumentCaptor.forClass(Taxonomy.class);
        verify(taxonomyCache).add(captor.capture());
        assertEquals(1005, captor.getValue().dataAccuracyScore());
    }
}
