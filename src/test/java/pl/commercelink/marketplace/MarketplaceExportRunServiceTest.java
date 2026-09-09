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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceExportRunServiceTest {

    private static final String BUCKET = "stores";
    private static final String STORE_ID = "uma2dqukxr";
    private static final String CATALOG_ID = "catalog-1";
    private static final String MARKETPLACE = "allegro";
    private static final String MARKETPLACE_PREFIX = "uma2dqukxr/marketplace-exports/allegro/";
    private static final String CATALOG_PREFIX = "uma2dqukxr/marketplace-exports/allegro/catalog-1/";
    private static final String OTHER_CATALOG_PREFIX = "uma2dqukxr/marketplace-exports/allegro/catalog-2/";
    private static final int LIMIT = 25;
    private static final Instant RUN_FINISHED_AT = Instant.parse("2026-08-13T01:31:05Z");
    private static final String RUN_ID = "8213415334_2026-08-13_01-31-05";

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
    void saveRunWritesCsvRowsUnderAKeyPrefixedWithTheCountdownOfTheClockInstant() {
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
    void saveRunGivesANewerRunASmallerCountdownThanAnOlderRun() {
        // given
        MarketplaceExportRunService olderRun = serviceAt("2026-08-13T01:31:05Z");
        MarketplaceExportRunService newerRun = serviceAt("2026-09-05T01:00:00Z");

        // when
        olderRun.saveRun(run());
        newerRun.saveRun(run());

        // then
        verify(fileStorage, times(2)).put(eq(BUCKET), keyCaptor.capture(), bytesCaptor.capture());
        List<String> keys = keyCaptor.getAllValues();
        assertThat(keys.get(0)).isEqualTo(CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05.csv");
        assertThat(keys.get(1)).isEqualTo(CATALOG_PREFIX + "8211429999_2026-09-05_01-00-00.csv");
        assertThat(keys.get(1).compareTo(keys.get(0))).isNegative();
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
    void loadPreviousExportReadsTheRunThatComesFirstInKeyOrder() {
        // given
        givenCatalogObjects(
                object(CATALOG_PREFIX + "8213589999_2026-08-11_01-00-00.csv", offersCsv("pim-OLD")),
                object(CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05.csv", offersCsv("pim-NEW")),
                object(CATALOG_PREFIX + "8213503599_2026-08-12_01-00-00.csv", offersCsv("pim-MIDDLE")));

        // when
        List<MarketplaceOfferSnapshot> offers = service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).pimId()).isEqualTo("pim-NEW");
    }

    @Test
    void loadPreviousExportAsksForKeysInKeyOrderAndNeverForLastModifiedTimestamps() {
        // given
        givenCatalogObjects(object(CATALOG_PREFIX + RUN_ID + ".csv", offersCsv("pim-NEW")));

        // when
        service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        verify(fileStorage).findKeysByKeyOrder(eq(BUCKET), eq(CATALOG_PREFIX), anyInt());
        verify(fileStorage, never()).getAllObjectLastModified(anyString(), anyString());
    }

    @Test
    void loadPreviousExportSkipsFilesWhoseNameIsNotARunId() {
        // given
        givenCatalogObjects(
                object(CATALOG_PREFIX + "0000-report.csv", offersCsv("pim-REPORT")),
                object(CATALOG_PREFIX + "not-a-run-id.csv", offersCsv("pim-JUNK")),
                object(CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05.csv", offersCsv("pim-NEW")));

        // when
        List<MarketplaceOfferSnapshot> offers = service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).pimId()).isEqualTo("pim-NEW");
    }

    @Test
    void loadPreviousExportNeverPicksTheLatestFileLeftInTheExportsDirectory() {
        // given
        givenCatalogObjects(
                object(CATALOG_PREFIX + "latest.csv", offersCsv("pim-LATEST")),
                object(CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05.csv", offersCsv("pim-NEW")));

        // when
        List<MarketplaceOfferSnapshot> offers = service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE);

        // then
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).pimId()).isEqualTo("pim-NEW");
    }

    @Test
    void loadPreviousExportReturnsEmptyListWhenTheLatestFileIsTheOnlyObject() {
        // given
        givenCatalogObjects(object(CATALOG_PREFIX + "latest.csv", offersCsv("pim-LATEST")));

        // when / then
        assertThat(service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE)).isEmpty();
    }

    @Test
    void loadPreviousExportSkipsAFailedRunThatComesFirstInKeyOrder() {
        // given
        givenCatalogObjects(
                object(CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05-failed.csv", offersCsv("pim-FAILED")),
                object(CATALOG_PREFIX + "8213503599_2026-08-12_01-00-00.csv", offersCsv("pim-OK")),
                object(CATALOG_PREFIX + "8213330000_2026-08-14_01-00-00.json", offersCsv("pim-JSON")));

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
        givenCatalogObjects(object(CATALOG_PREFIX + RUN_ID + ".csv",
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
        givenCatalogObjects(object(CATALOG_PREFIX + RUN_ID + ".csv", legacyCsv("pim-CSV;1999;7;1")));

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
        when(fileStorage.findKeysByKeyOrder(eq(BUCKET), eq(CATALOG_PREFIX), anyInt())).thenReturn(List.of());

        // when / then
        assertThat(service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE)).isEmpty();
    }

    @Test
    void loadPreviousExportReturnsEmptyListWhenObjectCannotBeParsed() {
        // given
        givenCatalogObjects(object(CATALOG_PREFIX + RUN_ID + ".csv", legacyCsv("pim-CSV;not-a-number;7;0")));

        // when / then
        assertThat(service.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE)).isEmpty();
    }

    @Test
    void findRunsParsesMarketplaceCatalogRunIdAndFailureFromKeys() {
        // given
        givenMarketplaceObjects(
                CATALOG_PREFIX + "8213589999_2026-08-11_01-00-00.csv",
                CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05-failed.csv");

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID, MARKETPLACE, LIMIT);

        // then
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).runId()).isEqualTo("8213415334_2026-08-13_01-31-05");
        assertThat(runs.get(0).marketplace()).isEqualTo(MARKETPLACE);
        assertThat(runs.get(0).catalogId()).isEqualTo(CATALOG_ID);
        assertThat(runs.get(0).failed()).isTrue();
        assertThat(runs.get(1).runId()).isEqualTo("8213589999_2026-08-11_01-00-00");
        assertThat(runs.get(1).failed()).isFalse();
    }

    @Test
    void findRunsOrdersRunsOfEveryCatalogOfTheMarketplaceNewestFirst() {
        // given
        givenMarketplaceObjects(
                CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05.csv",
                CATALOG_PREFIX + "8213589999_2026-08-11_01-00-00.csv",
                OTHER_CATALOG_PREFIX + "8211429999_2026-09-05_01-00-00.csv",
                OTHER_CATALOG_PREFIX + "8213503599_2026-08-12_01-00-00.csv");

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID, MARKETPLACE, LIMIT);

        // then
        assertThat(runs).extracting(MarketplaceExportRunHeader::runId).containsExactly(
                "8211429999_2026-09-05_01-00-00",
                "8213415334_2026-08-13_01-31-05",
                "8213503599_2026-08-12_01-00-00",
                "8213589999_2026-08-11_01-00-00");
        assertThat(runs).extracting(MarketplaceExportRunHeader::catalogId)
                .containsExactly("catalog-2", CATALOG_ID, "catalog-2", CATALOG_ID);
    }

    @Test
    void findRunsListsOnlyThePrefixOfTheAskedMarketplace() {
        // given
        givenMarketplaceObjects(CATALOG_PREFIX + RUN_ID + ".csv");

        // when
        service.findRuns(STORE_ID, MARKETPLACE, LIMIT);

        // then
        verify(fileStorage).findAllKeysByKeyOrder(BUCKET, MARKETPLACE_PREFIX);
        verify(fileStorage, never()).getAllObjectLastModified(anyString(), anyString());
    }

    @Test
    void findRunsCutsTheListingToTheGivenLimit() {
        // given
        givenMarketplaceObjects(runKeysOf(CATALOG_PREFIX, 40));

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID, MARKETPLACE, LIMIT);

        // then
        assertThat(runs).hasSize(LIMIT);
    }

    @Test
    void findRunsKeepsTheNewestRunsWhenTheMarketplaceHasMoreThanOneCatalog() {
        // given
        List<String> keys = new ArrayList<>(List.of(
                CATALOG_PREFIX + "8213503599_2026-08-12_01-00-00.csv",
                CATALOG_PREFIX + "8213589999_2026-08-11_01-00-00.csv",
                OTHER_CATALOG_PREFIX + "8211429999_2026-09-05_01-00-00.csv",
                OTHER_CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05.csv"));
        givenMarketplaceObjects(keys.toArray(new String[0]));

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID, MARKETPLACE, 2);

        // then
        assertThat(runs).extracting(MarketplaceExportRunHeader::runId).containsExactly(
                "8211429999_2026-09-05_01-00-00",
                "8213415334_2026-08-13_01-31-05");
        assertThat(runs).extracting(MarketplaceExportRunHeader::catalogId)
                .containsOnly("catalog-2");
    }

    @Test
    void findRunsReturnsNothingWhenTheLimitIsNotPositive() {
        // given
        givenMarketplaceObjects(CATALOG_PREFIX + RUN_ID + ".csv");

        // when / then
        assertThat(service.findRuns(STORE_ID, MARKETPLACE, 0)).isEmpty();
        verify(fileStorage, never()).findAllKeysByKeyOrder(anyString(), anyString());
    }

    @Test
    void findRunsSkipsKeysWhoseNameIsNotARunId() {
        // given
        givenMarketplaceObjects(
                CATALOG_PREFIX + "0000-report.csv",
                CATALOG_PREFIX + "not-a-run-id.csv",
                CATALOG_PREFIX + RUN_ID + ".csv");

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID, MARKETPLACE, LIMIT);

        // then
        assertThat(runs).extracting(MarketplaceExportRunHeader::runId).containsExactly(RUN_ID);
    }

    @Test
    void findRunsSkipsTheLatestFileLeftInTheExportsDirectory() {
        // given
        givenMarketplaceObjects(CATALOG_PREFIX + "latest.csv", CATALOG_PREFIX + RUN_ID + ".csv");

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID, MARKETPLACE, LIMIT);

        // then
        assertThat(runs).extracting(MarketplaceExportRunHeader::runId).containsExactly(RUN_ID);
    }

    @Test
    void findRunsListsAFailedRunAlongsideTheSucceededOnes() {
        // given
        givenMarketplaceObjects(
                CATALOG_PREFIX + "8213415334_2026-08-13_01-31-05-failed.csv",
                CATALOG_PREFIX + "8213503599_2026-08-12_01-00-00.csv");

        // when
        List<MarketplaceExportRunHeader> runs = service.findRuns(STORE_ID, MARKETPLACE, LIMIT);

        // then
        assertThat(runs).extracting(MarketplaceExportRunHeader::runId, MarketplaceExportRunHeader::failed)
                .containsExactly(
                        tuple("8213415334_2026-08-13_01-31-05", true),
                        tuple("8213503599_2026-08-12_01-00-00", false));
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

    private MarketplaceExportRunService serviceAt(String instant) {
        return new MarketplaceExportRunService(
                fileStorage, BUCKET, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private MarketplaceExportRun run() {
        return new MarketplaceExportRun(STORE_ID, MARKETPLACE, CATALOG_ID);
    }

    private String object(String key, byte[] data) {
        when(fileStorage.getBytes(BUCKET, key)).thenReturn(data);
        return key;
    }

    private void givenCatalogObjects(String... keys) {
        when(fileStorage.findKeysByKeyOrder(eq(BUCKET), eq(CATALOG_PREFIX), anyInt())).thenReturn(inKeyOrder(keys));
    }

    private void givenMarketplaceObjects(String... keys) {
        when(fileStorage.findAllKeysByKeyOrder(BUCKET, MARKETPLACE_PREFIX)).thenReturn(inKeyOrder(keys));
    }

    private String[] runKeysOf(String prefix, int count) {
        List<String> keys = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            keys.add(String.format("%s82134153%02d_2026-08-13_01-31-05.csv", prefix, index));
        }
        return keys.toArray(new String[0]);
    }

    private List<String> inKeyOrder(String... keys) {
        return Stream.of(keys).sorted().toList();
    }

    private byte[] legacyCsv(String csvRow) {
        return ("pimId;price;qty;removalAttempts\n" + csvRow + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] offersCsv(String pimId) {
        return MarketplaceExportRunCsv.toBytes(List.of(MarketplaceOfferSnapshot.published(pimId, 1999L, 7L)));
    }
}
