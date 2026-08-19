package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.starter.storage.FileStorage;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceExportRunServiceTest {

    private static final String BUCKET = "stores";
    private static final String STORE_ID = "uma2dqukxr";
    private static final String CATALOG_ID = "catalog-1";
    private static final String MARKETPLACE = "allegro";
    private static final String STORE_PREFIX = "marketplace-export-runs/uma2dqukxr/";
    private static final String CATALOG_PREFIX = "marketplace-export-runs/uma2dqukxr/allegro/catalog-1/";
    private static final Instant RUN_FINISHED_AT = Instant.parse("2026-08-13T01:31:05Z");
    private static final String RUN_ID = "2026-08-13_01-31-05";

    @Mock
    private FileStorage fileStorage;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<byte[]> bytesCaptor;

    private MarketplaceExportRunService service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceExportRunService(
                fileStorage, BUCKET, Clock.fixed(RUN_FINISHED_AT, ZoneOffset.UTC));
    }

    @Test
    void saveRunWritesCsvRowsUnderTimestampedKey() {
        // given
        MarketplaceExportRun run = run();
        run.offers(List.of(MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L)));

        // when
        service.saveRun(run);

        // then
        verify(fileStorage).put(eq(BUCKET), keyCaptor.capture(), bytesCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(CATALOG_PREFIX + RUN_ID + ".csv");

        List<MarketplaceOfferSnapshot> written = MarketplaceExportRunCsv.parse(bytesCaptor.getValue());
        assertThat(written).hasSize(1);
        assertThat(written.get(0).pimId()).isEqualTo("pim-A");
        assertThat(written.get(0).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_PUBLISHED);
    }

    @Test
    void saveRunMarksAFailedRunWithTheFailedSuffixAndAppendsTheAbortedRow() {
        // given
        MarketplaceExportRun run = run();
        run.offers(List.of(MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L)));
        run.failed(new IllegalStateException("marketplace unavailable"));

        // when
        service.saveRun(run);

        // then
        verify(fileStorage).put(eq(BUCKET), keyCaptor.capture(), bytesCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(CATALOG_PREFIX + RUN_ID + "-failed.csv");

        List<MarketplaceOfferSnapshot> written = MarketplaceExportRunCsv.parse(bytesCaptor.getValue());
        assertThat(written).hasSize(2);
        assertThat(written.get(1).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_EXPORT_ABORTED);
        assertThat(written.get(1).message()).contains("marketplace unavailable");
    }

    @Test
    void loadPreviousExportPicksNewestByLastModified() {
        // given
        givenCatalogObjects(
                object(CATALOG_PREFIX + "2026-08-11_01-00-00.csv", "2026-08-11T01:00:00", offersCsv("pim-OLD")),
                object(CATALOG_PREFIX + "2026-08-13_01-31-05.csv", "2026-08-13T01:31:05", offersCsv("pim-NEW")));

        // when
        List<MarketplaceOfferSnapshot> offers = service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).pimId()).isEqualTo("pim-NEW");
    }

    @Test
    void loadPreviousExportIgnoresFailedRunsAndNonCsvObjects() {
        // given
        givenCatalogObjects(
                object(CATALOG_PREFIX + "2026-08-11_01-00-00.csv", "2026-08-11T01:00:00", offersCsv("pim-OK")),
                object(CATALOG_PREFIX + "2026-08-12_01-00-00-failed.csv", "2026-08-12T01:00:00", offersCsv("pim-FAILED")),
                object(CATALOG_PREFIX + "2026-08-13_01-31-05.json", "2026-08-13T01:31:05", offersCsv("pim-JSON")));

        // when
        List<MarketplaceOfferSnapshot> offers = service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).pimId()).isEqualTo("pim-OK");
    }

    @Test
    void loadPreviousExportDropsRowsWithoutPimId() {
        // given
        MarketplaceExportRun run = run();
        run.offers(List.of(MarketplaceOfferSnapshot.published("pim-A", 1999L, 7L)));
        run.failed(new IllegalStateException("marketplace unavailable"));
        givenCatalogObjects(object(CATALOG_PREFIX + "2026-08-13_01-31-05.csv", "2026-08-13T01:31:05",
                MarketplaceExportRunCsv.toBytes(run.toRows())));

        // when
        List<MarketplaceOfferSnapshot> offers = service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).pimId()).isEqualTo("pim-A");
    }

    @Test
    void loadPreviousExportReadsLegacyFourColumnFiles() {
        // given
        givenCatalogObjects(object(CATALOG_PREFIX + "2026-08-13_01-31-05.csv", "2026-08-13T01:31:05",
                legacyCsv("pim-CSV;1999;7;1")));

        // when
        List<MarketplaceOfferSnapshot> offers = service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).pimId()).isEqualTo("pim-CSV");
        assertThat(offers.get(0).price()).isEqualTo(1999L);
        assertThat(offers.get(0).removalAttempts()).isEqualTo(1);
    }

    @Test
    void loadPreviousExportReturnsEmptyListWhenPrefixHasNoObjects() {
        // given
        when(fileStorage.getAllObjectLastModified(BUCKET, CATALOG_PREFIX)).thenReturn(Map.of());

        // when / then
        assertThat(service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE)).isEmpty();
    }

    @Test
    void loadPreviousExportReturnsEmptyListWhenObjectCannotBeParsed() {
        // given
        givenCatalogObjects(object(CATALOG_PREFIX + "2026-08-13_01-31-05.csv", "2026-08-13T01:31:05",
                legacyCsv("pim-CSV;not-a-number;7;0")));

        // when / then
        assertThat(service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE)).isEmpty();
    }

    @Test
    void findRunsParsesMarketplaceCatalogRunIdAndFailureFromKeysNewestFirst() {
        // given
        Map<String, LocalDateTime> objects = new LinkedHashMap<>();
        objects.put(CATALOG_PREFIX + "2026-08-11_01-00-00.csv", LocalDateTime.parse("2026-08-11T01:00:00"));
        objects.put(CATALOG_PREFIX + "2026-08-13_01-31-05-failed.csv", LocalDateTime.parse("2026-08-13T01:31:05"));
        objects.put(STORE_PREFIX + "other/nested/deeper/key.csv", LocalDateTime.parse("2026-08-14T01:00:00"));
        when(fileStorage.getAllObjectLastModified(BUCKET, STORE_PREFIX)).thenReturn(objects);

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID);

        // then
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).runId()).isEqualTo("2026-08-13_01-31-05");
        assertThat(runs.get(0).marketplace()).isEqualTo(MARKETPLACE);
        assertThat(runs.get(0).catalogId()).isEqualTo(CATALOG_ID);
        assertThat(runs.get(0).failed()).isTrue();
        assertThat(runs.get(1).runId()).isEqualTo("2026-08-11_01-00-00");
        assertThat(runs.get(1).failed()).isFalse();
    }

    @Test
    void findRunReturnsParsedRowsAndRawBytes() {
        // given
        byte[] data = offersCsv("pim-A");
        String key = CATALOG_PREFIX + RUN_ID + ".csv";
        when(fileStorage.canRead(BUCKET, key)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, key)).thenReturn(data);

        // when
        Optional<MarketplaceExportRunFile> runFile = service.findRun(STORE_ID, MARKETPLACE, CATALOG_ID, RUN_ID);

        // then
        assertThat(runFile).isPresent();
        assertThat(runFile.get().runId()).isEqualTo(RUN_ID);
        assertThat(runFile.get().failed()).isFalse();
        assertThat(runFile.get().rows().get(0).pimId()).isEqualTo("pim-A");
        assertThat(runFile.get().raw()).isEqualTo(data);
    }

    @Test
    void findRunFallsBackToTheFailedFileWhenThereIsNoSucceededOne() {
        // given
        when(fileStorage.canRead(BUCKET, CATALOG_PREFIX + RUN_ID + ".csv")).thenReturn(false);
        when(fileStorage.canRead(BUCKET, CATALOG_PREFIX + RUN_ID + "-failed.csv")).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, CATALOG_PREFIX + RUN_ID + "-failed.csv"))
                .thenReturn(offersCsv("pim-CSV"));

        // when
        Optional<MarketplaceExportRunFile> runFile = service.findRun(STORE_ID, MARKETPLACE, CATALOG_ID, RUN_ID);

        // then
        assertThat(runFile).isPresent();
        assertThat(runFile.get().runId()).isEqualTo(RUN_ID);
        assertThat(runFile.get().failed()).isTrue();
        assertThat(runFile.get().rows().get(0).pimId()).isEqualTo("pim-CSV");
    }

    @Test
    void findRunReturnsEmptyWhenObjectDoesNotExist() {
        // given
        when(fileStorage.canRead(eq(BUCKET), anyString())).thenReturn(false);

        // when / then
        assertThat(service.findRun(STORE_ID, MARKETPLACE, CATALOG_ID, RUN_ID)).isEmpty();
    }

    private MarketplaceExportRun run() {
        return new MarketplaceExportRun(STORE_ID, MARKETPLACE, CATALOG_ID);
    }

    private Map.Entry<String, byte[]> object(String key, String lastModified, byte[] data) {
        when(fileStorage.getBytes(BUCKET, key)).thenReturn(data);
        return Map.entry(key, lastModified.getBytes(StandardCharsets.UTF_8));
    }

    @SafeVarargs
    private void givenCatalogObjects(Map.Entry<String, byte[]>... objects) {
        Map<String, LocalDateTime> lastModified = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> object : objects) {
            lastModified.put(object.getKey(),
                    LocalDateTime.parse(new String(object.getValue(), StandardCharsets.UTF_8)));
        }
        when(fileStorage.getAllObjectLastModified(BUCKET, CATALOG_PREFIX)).thenReturn(lastModified);
    }

    private byte[] legacyCsv(String csvRow) {
        return ("pimId;price;qty;removalAttempts\n" + csvRow + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] offersCsv(String pimId) {
        return MarketplaceExportRunCsv.toBytes(List.of(MarketplaceOfferSnapshot.published(pimId, 1999L, 7L)));
    }
}
