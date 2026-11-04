package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.storage.FileStorage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferMatchResultStoreTest {

    private static final String BUCKET = "stores";
    private static final String KEY = "s1/marketplace-offer-matches/Empik/latest.json";

    @Mock
    private FileStorage fileStorage;

    private OfferMatchResultStore store() {
        return new OfferMatchResultStore(fileStorage, BUCKET);
    }

    @Test
    void readsAFileSavedBeforeRowsCarriedTheActiveFlagAsActive() {
        // given
        String old = "{\"finishedAt\":\"2026-11-02T10:00:00Z\",\"total\":1,\"linked\":["
                + "{\"sellerOfferKey\":\"PS-3\",\"title\":\"Puzzle\",\"eans\":[\"5900000000000\"],"
                + "\"reason\":null}],\"toReview\":[],\"ownOffers\":0,\"alreadyLinked\":0}";
        when(fileStorage.canRead(BUCKET, KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, KEY)).thenReturn(old.getBytes(StandardCharsets.UTF_8));

        // when
        Optional<OfferMatchResult> latest = store().latest("s1", "Empik");

        // then
        assertThat(latest).isPresent();
        assertThat(latest.get().linked()).extracting(OfferMatchResult.Row::active).containsExactly(true);
    }

    @Test
    void readsAFileSavedWhenOffersWereReportedAgainstTheCatalogs() {
        // given a result of the catalog-based matching: a product per row, a group of offers not in a catalog and the
        // EAN to add to one
        String old = "{\"finishedAt\":\"2026-11-02T10:00:00Z\",\"total\":3,"
                + "\"linked\":[{\"sellerOfferKey\":\"PS-1\",\"title\":\"Klocki\",\"eans\":[\"2000000001012\"],"
                + "\"product\":\"Klocki Classic\",\"reason\":null,\"active\":true,\"catalogEan\":null}],"
                + "\"notInCatalog\":[{\"sellerOfferKey\":\"PS-3\",\"title\":\"Puzzle\",\"eans\":[\"5900000000000\"],"
                + "\"product\":null,\"reason\":null,\"active\":true,\"catalogEan\":\"5900000000000\"}],"
                + "\"toReview\":[{\"sellerOfferKey\":\"PS-4\",\"title\":\"Gra\",\"eans\":[\"5900000000017\"],"
                + "\"product\":null,\"reason\":\"ambiguous\",\"active\":true,\"catalogEan\":null}],"
                + "\"ownOffers\":0,\"alreadyLinked\":0}";
        when(fileStorage.canRead(BUCKET, KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, KEY)).thenReturn(old.getBytes(StandardCharsets.UTF_8));

        // when
        Optional<OfferMatchResult> latest = store().latest("s1", "Empik");

        // then the page still shows it, without the dropped group and fields
        assertThat(latest).isPresent();
        assertThat(latest.get().total()).isEqualTo(3);
        assertThat(latest.get().linked()).containsExactly(new OfferMatchResult.Row("PS-1", "Klocki",
                List.of("2000000001012"), null, true));
        assertThat(latest.get().toReview()).extracting(OfferMatchResult.Row::reason).containsExactly("ambiguous");
    }

    private static OfferMatchResult result() {
        return new OfferMatchResult(Instant.parse("2026-11-02T10:00:00Z"), 6,
                List.of(new OfferMatchResult.Row("PS-1", "Klocki", List.of("2000000001012"), null, true),
                        new OfferMatchResult.Row("PS-2", "Lalka", List.of("2000000001029"), null, false)),
                List.of(new OfferMatchResult.Row("PS-4", "Gra", List.of(), "no-ean", true)),
                1, 1);
    }

    @Test
    void savesTheResultAsTheMarketplacesLatest() {
        // given
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);

        // when
        store().save("s1", "Empik", result());

        // then
        verify(fileStorage).put(eq(BUCKET), eq(KEY), bytes.capture());
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).contains("\"finishedAt\":\"2026-11-02T10:00:00Z\"");
    }

    @Test
    void readsBackWhatWasSaved() {
        // given
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        store().save("s1", "Empik", result());
        verify(fileStorage).put(eq(BUCKET), eq(KEY), bytes.capture());
        when(fileStorage.canRead(BUCKET, KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, KEY)).thenReturn(bytes.getValue());

        // when
        Optional<OfferMatchResult> latest = store().latest("s1", "Empik");

        // then
        assertThat(latest).contains(result());
    }

    @Test
    void hasNoResultBeforeTheFirstRun() {
        // given
        when(fileStorage.canRead(BUCKET, KEY)).thenReturn(false);

        // when
        Optional<OfferMatchResult> latest = store().latest("s1", "Empik");

        // then
        assertThat(latest).isEmpty();
    }

    @Test
    void treatsAnUnreadableResultAsMissing() {
        // given
        when(fileStorage.canRead(BUCKET, KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, KEY)).thenReturn("{broken".getBytes(StandardCharsets.UTF_8));

        // when
        Optional<OfferMatchResult> latest = store().latest("s1", "Empik");

        // then
        assertThat(latest).isEmpty();
    }

    private static final String PROGRESS_KEY = "s1/marketplace-offer-matches/Empik/in-progress.json";

    private static OfferMatchProgress progress() {
        return new OfferMatchProgress("run-1", Instant.parse("2026-11-02T10:00:00Z"),
                Instant.parse("2026-11-02T10:01:00Z"), OfferMatchProgress.Status.RUNNING, "200", 2, 6150, 1, 0,
                List.of(new OfferMatchResult.Row("PS-1", "Klocki", List.of("2000000001012"), null, true),
                        new OfferMatchResult.Row("PS-3", "Puzzle", List.of("5900000000000"), null, false)),
                List.of(), java.util.Set.of("PS-1", "PS-3"), 2, null, null);
    }

    @Test
    void savesProgressNextToTheLatestResult() {
        // given
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);

        // when
        store().saveProgress("s1", "Empik", progress());

        // then
        verify(fileStorage).put(eq(BUCKET), eq(PROGRESS_KEY), bytes.capture());
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8))
                .contains("\"runId\":\"run-1\"").contains("\"nextCursor\":\"200\"").contains("\"status\":\"RUNNING\"");
    }

    @Test
    void readsBackSavedProgress() {
        // given
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        store().saveProgress("s1", "Empik", progress());
        verify(fileStorage).put(eq(BUCKET), eq(PROGRESS_KEY), bytes.capture());
        when(fileStorage.canRead(BUCKET, PROGRESS_KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, PROGRESS_KEY)).thenReturn(bytes.getValue());

        // when
        Optional<OfferMatchProgress> read = store().progress("s1", "Empik");

        // then
        assertThat(read).contains(progress());
    }

    @Test
    void hasNoProgressWithoutARun() {
        // given
        when(fileStorage.canRead(BUCKET, PROGRESS_KEY)).thenReturn(false);

        // when
        Optional<OfferMatchProgress> read = store().progress("s1", "Empik");

        // then
        assertThat(read).isEmpty();
    }

    @Test
    void treatsUnreadableProgressAsMissing() {
        // given
        when(fileStorage.canRead(BUCKET, PROGRESS_KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, PROGRESS_KEY)).thenReturn("{broken".getBytes(StandardCharsets.UTF_8));

        // when
        Optional<OfferMatchProgress> read = store().progress("s1", "Empik");

        // then
        assertThat(read).isEmpty();
    }

    @Test
    void readsBackTheCodeOfAFailedRun() {
        // given
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        OfferMatchProgress failed = progress().failed(Instant.parse("2026-11-02T10:02:00Z"),
                OfferMatchError.MARKETPLACE_ERROR, 503);
        store().saveProgress("s1", "Empik", failed);
        verify(fileStorage).put(eq(BUCKET), eq(PROGRESS_KEY), bytes.capture());
        when(fileStorage.canRead(BUCKET, PROGRESS_KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, PROGRESS_KEY)).thenReturn(bytes.getValue());

        // when
        Optional<OfferMatchProgress> read = store().progress("s1", "Empik");

        // then
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).contains("\"error\":\"MARKETPLACE_ERROR\"");
        assertThat(read).contains(failed);
    }

    @Test
    void deletesProgressOfAFinishedRun() {
        // when
        store().deleteProgress("s1", "Empik");

        // then
        verify(fileStorage).delete(BUCKET, PROGRESS_KEY);
    }
}
