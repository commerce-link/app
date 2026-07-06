package pl.commercelink.pricelist;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricelistRepositoryTest {

    @Mock
    private FileStorage fileStorage;

    private final String bucketName = "stores";
    private final String storeId = "uma2dqukxr";
    private final String catalogId = "catalogId";
    private final String prefix = "uma2dqukxr/pricelists/catalogId/";

    private PricelistRepository pricelistRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pricelistRepository = new PricelistRepository(fileStorage, bucketName);
    }

    @Test
    void testFind() {
        String pricelistId = "pricelistId";
        String key = prefix + "pricelistId.csv";
        String csvData = "CatalogId;PimId;ManufacturerCode;Brand;Label;Name;Category;Price;Qty\n" +
                "catalogId;pim2;mfc2;brand2;label2;name2;PSU;200;20\n" +
                "catalogId;pim3;mfc3;brand3;label3;name3;PSU;300;30";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));

        when(fileStorage.canRead(bucketName, key)).thenReturn(true);
        when(fileStorage.get(bucketName, key)).thenReturn(reader);

        Pricelist pricelist = pricelistRepository.find(storeId, catalogId, pricelistId);

        assertEquals(pricelistId, pricelist.getPricelistId());
        assertEquals(2, pricelist.getAvailabilityAndPrices().size());
    }

    @Test
    void keepsUnknownCategoryFromCsv() {
        // given
        String pricelistId = "pricelistId";
        String key = prefix + "pricelistId.csv";
        String csvData = "CatalogId;PimId;ManufacturerCode;Brand;Label;Name;Category;Price;Qty\n" +
                "catalogId;pim2;mfc2;brand2;label2;name2;Smartwatches;200;20";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));
        when(fileStorage.canRead(bucketName, key)).thenReturn(true);
        when(fileStorage.get(bucketName, key)).thenReturn(reader);

        // when
        Pricelist pricelist = pricelistRepository.find(storeId, catalogId, pricelistId);

        // then
        assertEquals("Smartwatches", pricelist.getAvailabilityAndPrices().get(0).getCategory());
    }

    @Test
    void mapsLegacyOsCategoryToSoftware() {
        // given
        String pricelistId = "pricelistId";
        String key = prefix + "pricelistId.csv";
        String csvData = "CatalogId;PimId;ManufacturerCode;Brand;Label;Name;Category;Price;Qty\n" +
                "catalogId;pim2;mfc2;brand2;label2;name2;OS;200;20";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));
        when(fileStorage.canRead(bucketName, key)).thenReturn(true);
        when(fileStorage.get(bucketName, key)).thenReturn(reader);

        // when
        Pricelist pricelist = pricelistRepository.find(storeId, catalogId, pricelistId);

        // then
        assertEquals("Software", pricelist.getAvailabilityAndPrices().get(0).getCategory());
    }

    @Test
    void testFindFileNotThere() {
        String pricelistId = "pricelistId";
        String key = prefix + "pricelistId.csv";

        when(fileStorage.canRead(bucketName, key)).thenReturn(false);
        Pricelist pricelist = pricelistRepository.find(storeId, catalogId, pricelistId);
        assertNull(pricelist);
    }

    @Test
    void testFindNewestPricelist() {
        String csvData = "CatalogId;PimId;ManufacturerCode;Brand;Label;Name;Category;Price;Qty\n" +
                "catalogId;pim2;mfc2;brand2;label2;name2;PSU;200;20\n" +
                "catalogId;pim3;mfc3;brand3;label3;name3;PSU;300;30";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));
        when(fileStorage.findNewest(bucketName, prefix)).thenReturn(Pair.of("newestPricelistId", reader));

        Pricelist pricelist = pricelistRepository.findNewestPricelist(storeId, catalogId);

        assertEquals("newestPricelistId", pricelist.getPricelistId());
        assertEquals(2, pricelist.getAvailabilityAndPrices().size());
    }

    @Test
    void findNewestReturnsNullWhenPrefixEmpty() {
        when(fileStorage.findNewest(bucketName, prefix)).thenReturn(null);

        Pricelist pricelist = pricelistRepository.findNewestPricelist(storeId, catalogId);

        assertNull(pricelist);
    }

    @Test
    void savesToStoreScopedKey() throws IOException {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        String pricelistId = pricelistRepository.save(storeId, catalogId, List.of());

        verify(fileStorage).put(eq(bucketName), keyCaptor.capture(), any());
        String key = keyCaptor.getValue();
        assertTrue(key.startsWith(prefix), "key should start with store prefix: " + key);
        assertEquals(prefix + pricelistId + ".csv", key);
    }

    @Test
    void findsNewestUsesStorePrefix() {
        when(fileStorage.findNewestFileName(bucketName, prefix)).thenReturn(Optional.of("abc.csv"));

        String id = pricelistRepository.findNewestPricelistId(storeId, catalogId);

        assertEquals("abc", id);
        verify(fileStorage).findNewestFileName(bucketName, prefix);
    }

    @Test
    void topNUsesStorePrefix() {
        when(fileStorage.findTopN(bucketName, prefix, 3)).thenReturn(List.of());

        pricelistRepository.findTopNPricelist(storeId, catalogId, 3);

        verify(fileStorage).findTopN(bucketName, prefix, 3);
    }
}
