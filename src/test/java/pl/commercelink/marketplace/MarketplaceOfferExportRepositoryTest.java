package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.storage.FileStorage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketplaceOfferExportRepositoryTest {

    private static final String BUCKET = "stores";
    private static final String STORE_ID = "uma2dqukxr";
    private static final String CATALOG_ID = "catalog-1";
    private static final String MARKETPLACE = "allegro";
    private static final String LATEST_KEY = "uma2dqukxr/marketplace-exports/allegro/catalog-1/latest.csv";
    private static final String RUN_HISTORY_KEY =
            "marketplace-export-runs/uma2dqukxr/allegro/catalog-1/2026-08-11_01-53-12.csv";
    private static final Instant RUN_FINISHED_AT = Instant.parse("2026-08-11T01:53:12Z");

    @Mock
    private FileStorage fileStorage;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<byte[]> bytesCaptor;

    private MarketplaceOfferExportRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MarketplaceOfferExportRepository(
                fileStorage, BUCKET, Clock.fixed(RUN_FINISHED_AT, ZoneOffset.UTC));
    }

    @Test
    void saveCurrentExportWritesLatestAndRunHistory() {
        // given
        List<MarketplaceOfferSnapshot> snapshots = List.of(
                new MarketplaceOfferSnapshot("LEN-E14-G5-21JK", 3503, 7, 0));

        // when
        repository.saveCurrentExport(STORE_ID, CATALOG_ID, MARKETPLACE, snapshots);

        // then
        verify(fileStorage, times(2)).put(eq(BUCKET), keyCaptor.capture(), bytesCaptor.capture());
        assertEquals(List.of(LATEST_KEY, RUN_HISTORY_KEY), keyCaptor.getAllValues());
        assertArrayEquals(bytesCaptor.getAllValues().get(0), bytesCaptor.getAllValues().get(1));
        assertTrue(new String(bytesCaptor.getAllValues().get(0)).contains("LEN-E14-G5-21JK"));
    }

    @Test
    void saveCurrentExportKeepsLatestWhenRunHistoryWriteFails() {
        // given
        lenient().doThrow(new RuntimeException("s3 unavailable"))
                .when(fileStorage).put(eq(BUCKET), eq(RUN_HISTORY_KEY), any(byte[].class));

        // when
        repository.saveCurrentExport(STORE_ID, CATALOG_ID, MARKETPLACE, List.of());

        // then
        verify(fileStorage).put(eq(BUCKET), eq(LATEST_KEY), any(byte[].class));
        verify(fileStorage).put(eq(BUCKET), eq(RUN_HISTORY_KEY), any(byte[].class));
    }

    @Test
    void saveCurrentExportSkipsRunHistoryWhenLatestWriteFails() {
        // given
        doThrow(new RuntimeException("s3 unavailable"))
                .when(fileStorage).put(eq(BUCKET), eq(LATEST_KEY), any(byte[].class));

        // when
        repository.saveCurrentExport(STORE_ID, CATALOG_ID, MARKETPLACE, List.of());

        // then
        verify(fileStorage).put(eq(BUCKET), eq(LATEST_KEY), any(byte[].class));
        verify(fileStorage, times(0)).put(eq(BUCKET), eq(RUN_HISTORY_KEY), any(byte[].class));
    }
}
