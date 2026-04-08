package pl.commercelink.pricelist;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PricelistRepositoryTest {

    @Mock
    private FileStorage fileStorage;

    private String bucketName = "bucketName";

    private PricelistRepository pricelistRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pricelistRepository = new PricelistRepository(fileStorage, bucketName);
    }

    @Test
    void testFind() {
        String catalogId = "catalogId";
        String pricelistId = "pricelistId";
        String csvData = "CatalogId;PimId;ManufacturerCode;Brand;Label;Name;Category;Price;Qty\n" +
                "catalogId;pim2;mfc2;brand2;label2;name2;PSU;200;20\n" +
                "catalogId;pim3;mfc3;brand3;label3;name3;PSU;300;30";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));

        when(fileStorage.canRead(bucketName, "catalogId/pricelistId.csv")).thenReturn(true);
        when(fileStorage.get(bucketName, "catalogId/pricelistId.csv")).thenReturn(reader);

        Pricelist pricelist = pricelistRepository.find(catalogId, pricelistId);

        assertEquals(pricelistId, pricelist.getPricelistId());
        assertEquals(2, pricelist.getAvailabilityAndPrices().size());
    }

    @Test
    void testFindFileNotThere() {
        String catalogId = "catalogId";
        String pricelistId = "pricelistId";

        when(fileStorage.canRead(bucketName, "catalogId/pricelistId.csv")).thenReturn(false);
        Pricelist pricelist = pricelistRepository.find(catalogId, pricelistId);
        assertNull(pricelist);
    }


    @Test
    void testFindNewestPricelist() {
        String catalogId = "catalogId";
        String csvData = "CatalogId;PimId;ManufacturerCode;Brand;Label;Name;Category;Price;Qty\n" +
                "catalogId;pim2;mfc2;brand2;label2;name2;PSU;200;20\n" +
                "catalogId;pim3;mfc3;brand3;label3;name3;PSU;300;30";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));
        when(fileStorage.findNewest(anyString(), anyString())).thenReturn(Pair.of("newestPricelistId", reader));

        Pricelist pricelist = pricelistRepository.findNewestPricelist(catalogId);

        assertEquals("newestPricelistId", pricelist.getPricelistId());
        assertEquals(2, pricelist.getAvailabilityAndPrices().size());
    }

// Remove this method as it's no longer needed
}