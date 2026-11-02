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
        String old = "{\"finishedAt\":\"2026-11-02T10:00:00Z\",\"total\":1,\"linked\":[],\"notInCatalog\":[],"
                + "\"noPimEntry\":[{\"sellerOfferKey\":\"SILLO-3\",\"title\":\"Puzzle\",\"eans\":[\"5900000000000\"],"
                + "\"pimId\":null,\"reason\":null}],\"toReview\":[],\"ownOffers\":0,\"alreadyLinked\":0}";
        when(fileStorage.canRead(BUCKET, KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, KEY)).thenReturn(old.getBytes(StandardCharsets.UTF_8));

        // when
        Optional<OfferMatchResult> latest = store().latest("s1", "Empik");

        // then
        assertThat(latest).isPresent();
        assertThat(latest.get().noPimEntry()).extracting(OfferMatchResult.Row::active, OfferMatchResult.Row::catalogEan)
                .containsExactly(org.assertj.core.api.Assertions.tuple(true, null));
    }

    private static OfferMatchResult result() {
        return new OfferMatchResult(Instant.parse("2026-11-02T10:00:00Z"), 6,
                List.of(new OfferMatchResult.Row("SILLO-1", "Klocki", List.of("5903771700818"), "pim-1", null)),
                List.of(new OfferMatchResult.Row("SILLO-2", "Lalka", List.of("5903771700825"), "pim-2", null)),
                List.of(new OfferMatchResult.Row("SILLO-3", "Puzzle", List.of("5900000000000"), null, null)),
                List.of(new OfferMatchResult.Row("SILLO-4", "Gra", List.of(), null, "no-ean")),
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
}
