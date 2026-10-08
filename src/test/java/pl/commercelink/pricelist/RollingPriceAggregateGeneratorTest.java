package pl.commercelink.pricelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.StoreActivity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollingPriceAggregateGeneratorTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final String STORE_MESSAGE = "{\"storeId\":\"store-1\",\"date\":\"2026-10-07\"}";

    @Mock
    private FileStorage fileStorage;
    @Mock
    private PriceDataFanOut fanOut;
    @Mock
    private StoreActivity storeActivity;

    private RollingPriceAggregateGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RollingPriceAggregateGenerator(fileStorage, fanOut, storeActivity, "datalake", "stores");
        lenient().when(fileStorage.canRead(anyString(), anyString())).thenReturn(false);
    }

    @Test
    void globalRunQueuesTheStoresBeforeItsOwnWork() throws Exception {
        // given
        snapshotExists("datalake", "daily-price-snapshot/2026-10-07.csv");

        // when
        generator.handleMessage("{\"date\":\"2026-10-07\"}");

        // then
        InOrder inOrder = inOrder(fanOut, fileStorage);
        inOrder.verify(fanOut).toActiveStores(RollingPriceAggregateGenerator.QUEUE, DAY);
        inOrder.verify(fileStorage).put(eq("datalake"), eq("rolling-price-aggregate/2026-10-07.csv"), any(byte[].class));
    }

    @Test
    void storeRunAggregatesTheStoresOwnSnapshots() throws Exception {
        // given
        when(storeActivity.isActive(STORE_ID)).thenReturn(true);
        snapshotExists("stores", "store-1/daily-price-snapshot/2026-10-06.csv");

        // when
        generator.handleMessage(STORE_MESSAGE);

        // then
        verify(fileStorage).put(eq("stores"), eq("store-1/rolling-price-aggregate/2026-10-07.csv"), any(byte[].class));
        verify(fileStorage, never()).put(eq("datalake"), anyString(), any(byte[].class));
        verifyNoInteractions(fanOut);
    }

    @Test
    void storeRunRemovesOnlyItsSnapshotsOlderThanTheWindow() throws Exception {
        // given
        when(storeActivity.isActive(STORE_ID)).thenReturn(true);
        snapshotExists("stores", "store-1/daily-price-snapshot/2026-10-06.csv");
        Map<String, LocalDateTime> stored = new LinkedHashMap<>();
        stored.put("store-1/daily-price-snapshot/2026-09-08.csv", LocalDateTime.now());
        stored.put("store-1/daily-price-snapshot/2026-09-07.csv", LocalDateTime.now());
        stored.put("store-1/daily-price-snapshot/readme.txt", LocalDateTime.now());
        when(fileStorage.getAllObjectLastModified("stores", "store-1/daily-price-snapshot/")).thenReturn(stored);

        // when
        generator.handleMessage(STORE_MESSAGE);

        // then
        verify(fileStorage).delete("stores", "store-1/daily-price-snapshot/2026-09-07.csv");
        verify(fileStorage, never()).delete("stores", "store-1/daily-price-snapshot/2026-09-08.csv");
        verify(fileStorage, never()).delete("stores", "store-1/daily-price-snapshot/readme.txt");
    }

    @Test
    void storeRunWithoutSnapshotsWritesNothing() {
        // given
        when(storeActivity.isActive(STORE_ID)).thenReturn(true);

        // when
        generator.handleMessage(STORE_MESSAGE);

        // then
        verify(fileStorage, never()).put(anyString(), anyString(), any(byte[].class));
        verify(fileStorage, never()).delete(anyString(), anyString());
    }

    @Test
    void storeRunOfInactiveStoreDoesNothing() {
        // given
        when(storeActivity.isActive(STORE_ID)).thenReturn(false);

        // when
        generator.handleMessage(STORE_MESSAGE);

        // then
        verifyNoInteractions(fanOut);
        verify(fileStorage, never()).put(anyString(), anyString(), any(byte[].class));
    }

    private void snapshotExists(String bucket, String key) throws IOException {
        DailyPriceSnapshot snapshot = new DailyPriceSnapshot("pim-1", 100, 110, 120, 5, 8, "Alpha",
                "Alpha|Beta", "100.0|110.0", "Alpha|Beta", LocalDate.of(2026, 10, 6));
        byte[] csv = new CSVWriter().writeAllRowsToBytes(List.of(snapshot), DailyPriceSnapshot.COLUMNS);
        when(fileStorage.canRead(bucket, key)).thenReturn(true);
        when(fileStorage.get(bucket, key)).thenReturn(new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8));
    }
}
