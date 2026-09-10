package pl.commercelink.pricelist;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.storage.TimeOrderedFileName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricelistRepositoryTest {

    @Mock
    private FileStorage fileStorage;

    private final String bucketName = "stores";
    private final String storeId = "uma2dqukxr";
    private final String catalogId = "catalogId";
    private final String prefix = "uma2dqukxr/pricelists/catalogId/";

    private final Instant now = Instant.parse("2025-03-04T05:06:07Z");
    private final String timeOrderedPart = TimeOrderedFileName.of(now);

    private PricelistRepository pricelistRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pricelistRepository = new PricelistRepository(fileStorage, bucketName, Clock.fixed(now, ZoneOffset.UTC));
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
        when(fileStorage.findNewestByLastModified(bucketName, prefix)).thenReturn(Pair.of("newestPricelistId", reader));

        Pricelist pricelist = pricelistRepository.findNewestPricelist(storeId, catalogId);

        assertEquals("newestPricelistId", pricelist.getPricelistId());
        assertEquals(2, pricelist.getAvailabilityAndPrices().size());
    }

    @Test
    void findNewestReturnsNullWhenPrefixEmpty() {
        when(fileStorage.findNewestByLastModified(bucketName, prefix)).thenReturn(null);

        Pricelist pricelist = pricelistRepository.findNewestPricelist(storeId, catalogId);

        assertNull(pricelist);
    }

    @Test
    void savesUnderTimeOrderedFileName() throws IOException {
        String pricelistId = pricelistRepository.save(storeId, catalogId, List.of());

        assertTrue(pricelistId.startsWith(timeOrderedPart + "_"), "id should carry the time ordered part: " + pricelistId);
        assertEquals(Optional.of(now), TimeOrderedFileName.instantOf(pricelistId));
    }

    @Test
    void newerPricelistIdSortsBeforeOlderOne() throws IOException {
        String older = pricelistRepository.save(storeId, catalogId, List.of());
        PricelistRepository later = new PricelistRepository(fileStorage, bucketName,
                Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC));

        String newer = later.save(storeId, catalogId, List.of());

        assertTrue(newer.compareTo(older) < 0, newer + " should sort before " + older);
    }

    @Test
    void findsNewestFileNameByKeyOrderWhenNewestNameIsTimeOrdered() {
        when(fileStorage.findNewestFileNameByKeyOrder(bucketName, prefix))
                .thenReturn(Optional.of(timeOrderedPart + "_abc.csv"));

        String id = pricelistRepository.findNewestPricelistId(storeId, catalogId);

        assertEquals(timeOrderedPart + "_abc", id);
        verify(fileStorage, never()).findNewestFileNameByLastModified(bucketName, prefix);
    }

    @Test
    void fallsBackToLastModifiedWhenNewestKeyIsLegacyUuid() {
        when(fileStorage.findNewestFileNameByKeyOrder(bucketName, prefix))
                .thenReturn(Optional.of("1cb4e2b6-4a2d-4c6e-9a0f-8b0f0f2b1d55.csv"));
        when(fileStorage.findNewestFileNameByLastModified(bucketName, prefix))
                .thenReturn(Optional.of(timeOrderedPart + "_abc.csv"));

        String id = pricelistRepository.findNewestPricelistId(storeId, catalogId);

        assertEquals(timeOrderedPart + "_abc", id);
    }

    @Test
    void readsNewestPricelistByKeyOrderWhenNewestNameIsTimeOrdered() {
        String csvData = "CatalogId;PimId;ManufacturerCode;Brand;Label;Name;Category;Price;Qty\n" +
                "catalogId;pim2;mfc2;brand2;label2;name2;PSU;200;20";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));
        when(fileStorage.findNewestFileNameByKeyOrder(bucketName, prefix))
                .thenReturn(Optional.of(timeOrderedPart + "_abc.csv"));
        when(fileStorage.findNewestByKeyOrder(bucketName, prefix))
                .thenReturn(Pair.of(timeOrderedPart + "_abc.csv", reader));

        Pricelist pricelist = pricelistRepository.findNewestPricelist(storeId, catalogId);

        assertEquals(timeOrderedPart + "_abc", pricelist.getPricelistId());
        verify(fileStorage, never()).findNewestByLastModified(bucketName, prefix);
    }

    @Test
    void readsNewestBytesByKeyOrderWhenNewestNameIsTimeOrdered() {
        when(fileStorage.findNewestFileNameByKeyOrder(bucketName, prefix))
                .thenReturn(Optional.of(timeOrderedPart + "_abc.csv"));
        when(fileStorage.findNewestAsBytesByKeyOrder(bucketName, prefix)).thenReturn("data".getBytes());

        assertArrayEquals("data".getBytes(), pricelistRepository.findNewestPricelistAsBytes(storeId, catalogId));
        verify(fileStorage, never()).findNewestAsBytesByLastModified(bucketName, prefix);
    }

    @Test
    void topNUsesKeyOrderWhenEveryNameIsTimeOrdered() {
        when(fileStorage.findTopNByKeyOrder(bucketName, prefix, 2)).thenReturn(List.of(
                Pair.of(timeOrderedPart + "_newer.csv", "04 Mar 2025 05:06:07"),
                Pair.of(TimeOrderedFileName.of(now.minusSeconds(60)) + "_older.csv", "04 Mar 2025 05:05:07")));

        List<Pricelist> pricelists = pricelistRepository.findTopNPricelist(storeId, catalogId, 2);

        assertEquals(List.of(timeOrderedPart + "_newer", TimeOrderedFileName.of(now.minusSeconds(60)) + "_older"),
                pricelists.stream().map(Pricelist::getPricelistId).toList());
        verify(fileStorage, never()).findTopNByLastModified(bucketName, prefix, 2);
    }

    @Test
    void topNFallsBackToLastModifiedWhenAnyNameIsLegacyUuid() {
        when(fileStorage.findTopNByKeyOrder(bucketName, prefix, 2)).thenReturn(List.of(
                Pair.of(timeOrderedPart + "_newer.csv", "04 Mar 2025 05:06:07"),
                Pair.of("1cb4e2b6-4a2d-4c6e-9a0f-8b0f0f2b1d55.csv", "01 Mar 2025 05:05:07")));
        when(fileStorage.findTopNByLastModified(bucketName, prefix, 2)).thenReturn(List.of(
                Pair.of(timeOrderedPart + "_newer.csv", "04 Mar 2025 05:06:07")));

        List<Pricelist> pricelists = pricelistRepository.findTopNPricelist(storeId, catalogId, 2);

        assertEquals(List.of(timeOrderedPart + "_newer"), pricelists.stream().map(Pricelist::getPricelistId).toList());
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
        when(fileStorage.findNewestFileNameByLastModified(bucketName, prefix)).thenReturn(Optional.of("abc.csv"));

        String id = pricelistRepository.findNewestPricelistId(storeId, catalogId);

        assertEquals("abc", id);
        verify(fileStorage).findNewestFileNameByLastModified(bucketName, prefix);
    }

    @Test
    void topNUsesStorePrefix() {
        when(fileStorage.findTopNByKeyOrder(bucketName, prefix, 3)).thenReturn(List.of());

        pricelistRepository.findTopNPricelist(storeId, catalogId, 3);

        verify(fileStorage).findTopNByKeyOrder(bucketName, prefix, 3);
    }

    @Test
    void rowsWithoutServiceColumnDefaultToFalse() {
        // given — old-format CSV row, no trailing service column
        String pricelistId = "pricelistId";
        String key = prefix + "pricelistId.csv";
        String csvData = "PimId;EAN;Mfn;Brand;Label;Name;Category;Price;Qty;Estimated Delivery Days;Lowest 30 Days Price\n" +
                "pim2;ean2;mfc2;brand2;label2;name2;PSU;200;20;1;200";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));
        when(fileStorage.canRead(bucketName, key)).thenReturn(true);
        when(fileStorage.get(bucketName, key)).thenReturn(reader);

        // when
        Pricelist pricelist = pricelistRepository.find(storeId, catalogId, pricelistId);

        // then
        assertFalse(pricelist.getAvailabilityAndPrices().get(0).isService());
    }

    @Test
    void rowsWithServiceColumnRoundTripTheFlag() {
        // given — new-format CSV row with trailing service column set to true
        String pricelistId = "pricelistId";
        String key = prefix + "pricelistId.csv";
        String csvData = "PimId;EAN;Mfn;Brand;Label;Name;Category;Price;Qty;Estimated Delivery Days;Lowest 30 Days Price;service\n" +
                "pim2;ean2;mfc2;brand2;label2;name2;Usługi dodatkowe;200;20;1;200;true";
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(csvData.getBytes()));
        when(fileStorage.canRead(bucketName, key)).thenReturn(true);
        when(fileStorage.get(bucketName, key)).thenReturn(reader);

        // when
        Pricelist pricelist = pricelistRepository.find(storeId, catalogId, pricelistId);

        // then
        assertTrue(pricelist.getAvailabilityAndPrices().get(0).isService());
    }
}
