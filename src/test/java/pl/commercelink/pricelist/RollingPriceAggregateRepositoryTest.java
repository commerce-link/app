package pl.commercelink.pricelist;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollingPriceAggregateRepositoryTest {

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private RollingPriceAggregateRepository repository;

    @BeforeEach
    void buckets() {
        ReflectionTestUtils.setField(repository, "bucketName", "datalake");
        ReflectionTestUtils.setField(repository, "storesBucketName", "stores");
    }

    @Test
    void storeAggregateOverridesTheGlobalOneProductByProduct() throws IOException {
        // given
        globalAggregate(aggregate("pim-1", 100), aggregate("pim-2", 200));
        storeAggregate("store-1", aggregate("pim-1", 90));

        // when
        Map<String, RollingPriceAggregate> result = repository.loadForStore("store-1");

        // then
        assertEquals(90, result.get("pim-1").getMedianLowestPrice30d());
        assertEquals(200, result.get("pim-2").getMedianLowestPrice30d());
    }

    @Test
    void storeWithoutItsOwnAggregateGetsTheGlobalOne() throws IOException {
        // given
        globalAggregate(aggregate("pim-1", 100));
        when(fileStorage.findNewestByLastModified("stores", "store-1/rolling-price-aggregate/")).thenReturn(null);

        // when
        Map<String, RollingPriceAggregate> result = repository.loadForStore("store-1");

        // then
        assertEquals(100, result.get("pim-1").getMedianLowestPrice30d());
    }

    @Test
    void globalLoadNeverTouchesAStore() throws IOException {
        // given
        globalAggregate(aggregate("pim-1", 100));

        // when
        Map<String, RollingPriceAggregate> result = repository.loadAll();

        // then
        assertTrue(result.containsKey("pim-1"));
        verify(fileStorage, never()).findNewestByLastModified("stores", "store-1/rolling-price-aggregate/");
    }

    private void globalAggregate(RollingPriceAggregate... aggregates) throws IOException {
        when(fileStorage.findNewestByLastModified("datalake", "rolling-price-aggregate/"))
                .thenReturn(csv("rolling-price-aggregate/2026-10-07.csv", aggregates));
    }

    private void storeAggregate(String storeId, RollingPriceAggregate... aggregates) throws IOException {
        when(fileStorage.findNewestByLastModified("stores", storeId + "/rolling-price-aggregate/"))
                .thenReturn(csv(storeId + "/rolling-price-aggregate/2026-10-07.csv", aggregates));
    }

    private Pair<String, InputStreamReader> csv(String key, RollingPriceAggregate... aggregates) throws IOException {
        byte[] bytes = new CSVWriter().writeAllRowsToBytes(List.of(aggregates), RollingPriceAggregate.COLUMNS);
        return Pair.of(key, new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
    }

    private RollingPriceAggregate aggregate(String pimId, double lowestPrice) {
        DailyPriceSnapshot snapshot = new DailyPriceSnapshot(pimId, lowestPrice, lowestPrice + 10, lowestPrice + 20, 5, 8,
                "Alpha", "Alpha|Beta", lowestPrice + "|" + (lowestPrice + 10), "Alpha|Beta", LocalDate.of(2026, 10, 6));
        return new RollingPriceAggregateCalculator(pimId, new java.util.ArrayList<>(List.of(snapshot))).aggregate();
    }
}
