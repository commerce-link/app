package pl.commercelink.pricelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellingPriceHistoryRepositoryTest {

    private static final String BUCKET = "stores";
    private static final String STORE_ID = "uma2dqukxr";
    private static final String CATALOG_ID = "catalog-1";
    private static final String EXPECTED_KEY = "uma2dqukxr/price-history/catalog-1/history.csv";

    @Mock
    private FileStorage fileStorage;

    @Captor
    private ArgumentCaptor<byte[]> bytesCaptor;

    private SellingPriceHistoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SellingPriceHistoryRepository(fileStorage, BUCKET);
    }

    @Test
    void loadReadsFromStoreScopedKey() {
        // given (row 0 "pim_id" jest nagłówkiem pomijanym przez CSVLoader)
        String csv = "pim_id\npim1;2026-06-01:100;2026-06-02:90\n";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csv.getBytes()));
        when(fileStorage.canRead(BUCKET, EXPECTED_KEY)).thenReturn(true);
        when(fileStorage.get(BUCKET, EXPECTED_KEY)).thenReturn(reader);

        // when
        Map<String, SellingPriceHistory> result = repository.load(STORE_ID, CATALOG_ID);

        // then
        verify(fileStorage).canRead(BUCKET, EXPECTED_KEY);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("pim1"));
    }

    @Test
    void saveWritesToStoreScopedKey() throws IOException {
        // given
        SellingPriceHistory history = new SellingPriceHistory("pim1");

        // when
        repository.save(STORE_ID, CATALOG_ID, List.of(history));

        // then
        verify(fileStorage).put(eq(BUCKET), eq(EXPECTED_KEY), bytesCaptor.capture());
        assertTrue(bytesCaptor.getValue().length > 0);
    }
}
