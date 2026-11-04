package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.api.MarketplaceListedOffer;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceOffersPage;
import pl.commercelink.marketplace.api.MarketplaceRateLimitedException;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferMatchingTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock
    private MarketplaceOfferLinkRepository links;
    @Mock
    private StorePimAliases aliases;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE);
        when(aliases.effectiveEntry(anyString(), anyString())).thenReturn(Optional.empty());
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of());
        when(links.saveUnlessManual(any())).thenReturn(true);
    }

    private MarketplaceOfferMatching matching() {
        return new MarketplaceOfferMatching(links, aliases, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void ownOffer(String key) {
        when(aliases.effectiveEntry(STORE, key)).thenReturn(Optional.of(globalEntry(key, "5903771700849", "7044", "Zabawki")));
    }

    private static MarketplaceListedOffer offer(String key, String... eans) {
        return new MarketplaceListedOffer(key, Arrays.asList(eans), "Title " + key, 3, new BigDecimal("19.99"), true);
    }

    @SafeVarargs
    private static ListedPages pages(List<MarketplaceListedOffer>... pages) {
        return new ListedPages(List.of(pages));
    }

    /**
     * A listing whose cursor is the page number; each page in {@code rateLimitedOnce} answers 429 the first time it is
     * asked for, and {@code onPage} runs on every page served.
     */
    private static final class ListedPages implements MarketplaceOffers {

        private final List<List<MarketplaceListedOffer>> pages;
        private final Set<Integer> rateLimitedOnce = new HashSet<>();
        private final List<String> requested = new ArrayList<>();
        private Runnable onPage = () -> { };

        ListedPages(List<List<MarketplaceListedOffer>> pages) {
            this.pages = pages;
        }

        ListedPages rateLimitedOnceAt(Integer... pageNumbers) {
            rateLimitedOnce.addAll(Arrays.asList(pageNumbers));
            return this;
        }

        ListedPages onPage(Runnable action) {
            this.onPage = action;
            return this;
        }

        @Override
        public MarketplaceOffersPage page(String cursor) {
            requested.add(cursor);
            int number = cursor == null ? 0 : Integer.parseInt(cursor);
            if (rateLimitedOnce.remove(number)) {
                throw new MarketplaceRateLimitedException(Duration.ofSeconds(60));
            }
            onPage.run();
            String next = number + 1 < pages.size() ? String.valueOf(number + 1) : null;
            int total = pages.stream().mapToInt(List::size).sum();
            return new MarketplaceOffersPage(pages.isEmpty() ? List.of() : pages.get(number), next, total);
        }
    }

    private static OfferMatchProgress fresh() {
        return OfferMatchProgress.start("run-1", NOW);
    }

    /** The run as the queue carries it: each paused step goes on from the progress it saved. */
    private OfferMatchResult runToEnd(MarketplaceOfferMatching matching, MarketplaceOffers offers) {
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, fresh());
        while (step instanceof MarketplaceOfferMatching.Paused paused) {
            step = matching.resume(store, EMPIK, offers, paused.progress());
        }
        return ((MarketplaceOfferMatching.Finished) step).result();
    }

    private static MarketplaceOfferLink existing(String key, MarketplaceOfferLinkSource source, String... eans) {
        return MarketplaceOfferLink.of(STORE, EMPIK, key, List.of(eans), source, "someone", NOW.minusSeconds(3600));
    }

    private MarketplaceOfferLink savedLink() {
        ArgumentCaptor<MarketplaceOfferLink> captor = ArgumentCaptor.forClass(MarketplaceOfferLink.class);
        verify(links).saveUnlessManual(captor.capture());
        return captor.getValue();
    }

    @Test
    void linksAnOfferToItsEanWithoutLookingAtTheCatalogs() {
        // given
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("PS-1", " 5903771700818 "))));

        // then
        MarketplaceOfferLink link = savedLink();
        assertThat(link.getStoreId()).isEqualTo(STORE);
        assertThat(link.getMarketplace()).isEqualTo(EMPIK);
        assertThat(link.getSellerOfferKey()).isEqualTo("PS-1");
        assertThat(link.getEans()).containsExactly("5903771700818");
        assertThat(link.getSource()).isEqualTo(MarketplaceOfferLinkSource.EAN);
        assertThat(link.getCreatedBy()).isEqualTo("offer-matching");
        assertThat(link.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.linked()).containsExactly(new OfferMatchResult.Row("PS-1", "Title PS-1",
                List.of("5903771700818"), null, true));
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.toReview()).isEmpty();
        assertThat(result.alreadyLinked()).isZero();
    }

    @Test
    void linksAnOfferWithSeveralEansInTheMarketplacesOrder() {
        // given
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("PS-2", "5900000000017", "5900000000000"))));

        // then the first EAN is the one an order item gets
        assertThat(savedLink().getEans()).containsExactly("5900000000017", "5900000000000");
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("PS-2");
    }

    @Test
    void leavesAManualLinkAloneAndCountsIt() {
        // given
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("PS-1", MarketplaceOfferLinkSource.MANUAL, "5903771700818")));
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("PS-1", "5900000000000"))));

        // then
        verify(links, never()).saveUnlessManual(any());
        verify(links, never()).save(any());
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.linked()).isEmpty();
        assertThat(result.toReview()).isEmpty();
    }

    @Test
    void rewritesAnEanLinkWhoseEansChanged() {
        // given an earlier run linked the offer under an EAN the offer no longer carries
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("PS-1", MarketplaceOfferLinkSource.EAN, "5900000000000")));
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("PS-1", "5903771700818"))));

        // then
        MarketplaceOfferLink link = savedLink();
        assertThat(link.getEans()).containsExactly("5903771700818");
        assertThat(link.getSource()).isEqualTo(MarketplaceOfferLinkSource.EAN);
        assertThat(result.alreadyLinked()).isZero();
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("PS-1");
    }

    @Test
    void rewritesAnEanLinkWhoseEansChangedOrder() {
        // given the offer now lists its EANs the other way round, so its items would get another EAN first
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(
                existing("PS-1", MarketplaceOfferLinkSource.EAN, "5900000000000", "5903771700818")));
        MarketplaceOfferMatching matching = matching();

        // when
        runToEnd(matching, pages(List.of(offer("PS-1", "5903771700818", "5900000000000"))));

        // then
        assertThat(savedLink().getEans()).containsExactly("5903771700818", "5900000000000");
    }

    @Test
    void doesNotRewriteAnEanLinkWithTheSameEans() {
        // given
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(
                existing("PS-1", MarketplaceOfferLinkSource.EAN, "5903771700818", "5900000000000"),
                existing("PS-2", MarketplaceOfferLinkSource.EAN, "5900000000017")));
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(
                offer("PS-1", "5903771700818", "5900000000000"), offer("PS-2", "5900000000017"))));

        // then still reported as linked
        verify(links, never()).saveUnlessManual(any());
        verify(links, never()).save(any());
        assertThat(result.alreadyLinked()).isZero();
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("PS-1", "PS-2");
    }

    @Test
    void countsAnOfferLinkedByHandMeanwhileAsAlreadyLinked() {
        // given an operator linked the offer after the run read the store's links
        when(links.saveUnlessManual(any())).thenReturn(false);
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("PS-1", "5903771700818"))));

        // then
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.linked()).isEmpty();
        assertThat(result.toReview()).isEmpty();
    }

    @Test
    void sendsOfferWithoutEanToReview() {
        // given
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("PS-1"), offer("PS-2", " ", null))));

        // then
        verify(links, never()).saveUnlessManual(any());
        assertThat(result.toReview()).extracting(OfferMatchResult.Row::sellerOfferKey, OfferMatchResult.Row::reason)
                .containsExactly(tuple("PS-1", "no-ean"), tuple("PS-2", "no-ean"));
    }

    @Test
    void sendsOfferWithoutKeyToReview() {
        // given
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer(" ", "5903771700818"))));

        // then
        verify(links, never()).saveUnlessManual(any());
        assertThat(result.toReview()).extracting(OfferMatchResult.Row::reason).containsExactly("no-key");
    }

    @Test
    void countsOwnOffersWithoutLinkingAndAsksThePimNothingElse() {
        // given
        ownOffer("pim-1");
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("pim-1", "5903771700818"), offer("PS-1", "5903771700818"))));

        // then
        verify(links, times(1)).saveUnlessManual(any());
        assertThat(result.ownOffers()).isEqualTo(1);
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("PS-1");
        assertThat(result.total()).isEqualTo(2);
        verify(aliases, atLeastOnce()).effectiveEntry(eq(STORE), anyString());
        verifyNoMoreInteractions(aliases);
    }

    @Test
    void carriesTheOffersActiveFlagIntoItsRow() {
        // given
        MarketplaceOfferMatching matching = matching();
        MarketplaceListedOffer inactive = new MarketplaceListedOffer("OLD-1", List.of("5900000000000"), "Stara", 0,
                new BigDecimal("1.00"), false);

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(inactive, offer("NEW-1", "5900000000001"))));

        // then
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey, OfferMatchResult.Row::active)
                .containsExactly(tuple("OLD-1", false), tuple("NEW-1", true));
    }

    @Test
    void countsAnOfferRepeatedAcrossPagesOnce() {
        // given the listing shifted between pages and returned the same offer twice
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(
                List.of(offer("PS-1", "5903771700818")),
                List.of(offer("PS-1", "5903771700818"), offer("PS-2", "5900000000000"))));

        // then
        verify(links, times(2)).saveUnlessManual(any());
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("PS-1", "PS-2");
    }

    @Test
    void readsTheStoresLinksOncePerMessage() {
        // given
        MarketplaceOfferMatching matching = matching();

        // when
        OfferMatchResult result = runToEnd(matching, pages(
                List.of(offer("PS-1", "5903771700818")),
                List.of(offer("PS-2", "5903771700818"), offer("PS-3", "5900000000000"))));

        // then
        verify(links, times(1)).findAll(STORE, EMPIK);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.linked()).hasSize(3);
    }

    @Test
    void propagatesAFailedPage() {
        // given
        MarketplaceOfferMatching matching = matching();
        MarketplaceOffers failing = cursor -> {
            throw new IllegalStateException("OF21 failed");
        };

        // when / then
        assertThatThrownBy(() -> matching.resume(store, EMPIK, failing, fresh()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finishesInOneStepWhenTheListingAllowsIt() {
        // given
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("XS-1", "5903771700818")), List.of(offer("XS-2", "5900000000000")));

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, fresh());

        // then
        assertThat(step).isInstanceOfSatisfying(MarketplaceOfferMatching.Finished.class, finished -> {
            assertThat(finished.result().total()).isEqualTo(2);
            assertThat(finished.result().finishedAt()).isEqualTo(NOW);
        });
        assertThat(offers.requested).containsExactly(null, "1");
    }

    @Test
    void pausesAtTheRateLimitedPageWithTheMarketplacesDelay() {
        // given
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("XS-1", "5903771700818")), List.of(offer("XS-2", "5900000000000")),
                List.of(offer("XS-3", "5900000000001"))).rateLimitedOnceAt(1);

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, fresh());

        // then the first page is counted, the second is where the next message starts
        assertThat(step).isInstanceOfSatisfying(MarketplaceOfferMatching.Paused.class, paused -> {
            assertThat(paused.delay()).isEqualTo(Duration.ofSeconds(60));
            OfferMatchProgress progress = paused.progress();
            assertThat(progress.nextCursor()).isEqualTo("1");
            assertThat(progress.processed()).isEqualTo(1);
            assertThat(progress.total()).isEqualTo(3);
            assertThat(progress.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("XS-1");
            assertThat(progress.seenKeys()).containsExactly("XS-1");
            assertThat(progress.runId()).isEqualTo("run-1");
            assertThat(progress.status()).isEqualTo(OfferMatchProgress.Status.RUNNING);
        });
    }

    @Test
    void continuesFromCursorAfterRateLimit() {
        // given a run paused before page 1
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("XS-1", "5903771700818")), List.of(offer("XS-2", "5900000000000")))
                .rateLimitedOnceAt(1);
        OfferMatchProgress paused = ((MarketplaceOfferMatching.Paused) matching.resume(store, EMPIK, offers, fresh()))
                .progress();
        offers.requested.clear();

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, paused);

        // then page 0 is not read again and its counts are kept
        assertThat(offers.requested).containsExactly("1");
        OfferMatchResult result = ((MarketplaceOfferMatching.Finished) step).result();
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("XS-1", "XS-2");
        // page 0 is not matched again: one write per offer
        verify(links, times(2)).saveUnlessManual(any());
    }

    @Test
    void pausesAtTheFirstPageWithoutACursor() {
        // given
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("XS-1", "5900000000000"))).rateLimitedOnceAt(0);

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, fresh());

        // then
        assertThat(step).isInstanceOfSatisfying(MarketplaceOfferMatching.Paused.class, paused -> {
            assertThat(paused.progress().nextCursor()).isNull();
            assertThat(paused.progress().processed()).isZero();
        });
    }

    @Test
    void continuesWithoutDelayWhenTimeBudgetIsUsed() {
        // given every page takes six minutes, so the ten-minute budget ends after the second
        MutableClock clock = new MutableClock(NOW);
        MarketplaceOfferMatching matching = new MarketplaceOfferMatching(links, aliases, clock);
        ListedPages offers = pages(List.of(offer("A", "5900000000001")), List.of(offer("B", "5900000000002")),
                List.of(offer("C", "5900000000003"))).onPage(() -> clock.advance(Duration.ofMinutes(6)));

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, fresh());

        // then
        assertThat(step).isInstanceOfSatisfying(MarketplaceOfferMatching.Paused.class, paused -> {
            assertThat(paused.delay()).isEqualTo(Duration.ZERO);
            assertThat(paused.progress().nextCursor()).isEqualTo("2");
            assertThat(paused.progress().processed()).isEqualTo(2);
            assertThat(paused.progress().updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(12)));
        });
    }

    @Test
    void countsAnOfferRepeatedAfterAPauseOnce() {
        // given the listing repeats an offer of the page read before the pause
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("XS-1", "5903771700818")),
                List.of(offer("XS-1", "5903771700818"), offer("XS-2", "5900000000000"))).rateLimitedOnceAt(1);

        // when
        OfferMatchResult result = runToEnd(matching, offers);

        // then
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("XS-1", "XS-2");
        assertThat(result.alreadyLinked()).isZero();
    }

    @Test
    void countsPausesOnTheRateLimitWithoutAPageRead() {
        // given a run that already paused twice at page 1 without reading it
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("A", "5900000000001")), List.of(offer("B", "5900000000002")))
                .rateLimitedOnceAt(1);
        OfferMatchProgress pausedTwice = new OfferMatchProgress("run-1", NOW, NOW, OfferMatchProgress.Status.RUNNING, "1",
                1, 2, 0, 0, null, null, Set.of("A"), 2, null, null);

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, pausedTwice);

        // then
        assertThat(((MarketplaceOfferMatching.Paused) step).progress().idleRateLimitPauses()).isEqualTo(3);
    }

    @Test
    void resetsThePauseCountOnceAPageIsRead() {
        // given page 1 is read, page 2 answers 429
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("A", "5900000000001")), List.of(offer("B", "5900000000002")),
                List.of(offer("C", "5900000000003"))).rateLimitedOnceAt(2);
        OfferMatchProgress pausedOften = new OfferMatchProgress("run-1", NOW, NOW, OfferMatchProgress.Status.RUNNING, "1",
                1, 3, 0, 0, null, null, Set.of("A"), 9, null, null);

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, pausedOften);

        // then
        OfferMatchProgress progress = ((MarketplaceOfferMatching.Paused) step).progress();
        assertThat(progress.nextCursor()).isEqualTo("2");
        assertThat(progress.idleRateLimitPauses()).isZero();
    }

    @Test
    void failsWhenTheMarketplaceGivesBackTheSameCursor() {
        // given a listing that never moves on
        MarketplaceOfferMatching matching = matching();
        MarketplaceOffers stuck = cursor -> new MarketplaceOffersPage(List.of(offer("A", "5900000000001")), "same", 1);

        // when / then
        assertThatThrownBy(() -> matching.resume(store, EMPIK, stuck, fresh()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void producesTheSameResultInOneMessageAndInSeveralSplitByTheRateLimit() {
        // given every outcome a run can have, spread over four pages
        ownOffer("own-1");
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(
                existing("OLD-1", MarketplaceOfferLinkSource.MANUAL, "5903771700832"),
                existing("OLD-2", MarketplaceOfferLinkSource.EAN, "5903771700818")));
        MarketplaceListedOffer inactive = new MarketplaceListedOffer("OFF-1", List.of("5900000000009"), "Stara", 0,
                new BigDecimal("1.00"), false);
        List<List<MarketplaceListedOffer>> listing = List.of(
                List.of(offer("XS-1", "5903771700818"), offer("own-1", "5903771700849"), offer(" ", "5900000000001")),
                List.of(offer("OLD-1", "5903771700832"), offer("OLD-2", "5903771700818"), offer("AMB", "5903771700825")),
                List.of(offer("XS-1", "5903771700818"), offer("NOEAN"), inactive),
                List.of(offer("NOMFN", "5900000000048"), offer("MISS", "5900000000002")));
        OfferMatchResult inOneMessage = runToEnd(matching(), new ListedPages(listing));

        // when the same listing is read with a 429 before pages 1, 2 and 3, each part going through the progress file
        Map<String, byte[]> files = new HashMap<>();
        FileStorage storage = mock(FileStorage.class);
        doAnswer(call -> files.put(call.getArgument(1), call.getArgument(2))).when(storage).put(any(), any(), any());
        when(storage.canRead(any(), any())).thenAnswer(call -> files.containsKey(call.<String>getArgument(1)));
        when(storage.getBytes(any(), any())).thenAnswer(call -> files.get(call.<String>getArgument(1)));
        OfferMatchResultStore progressFiles = new OfferMatchResultStore(storage, "stores");
        MarketplaceOfferMatching split = matching();
        ListedPages offers = new ListedPages(listing).rateLimitedOnceAt(1, 2, 3);
        MarketplaceOfferMatching.Step step = split.resume(store, EMPIK, offers, fresh());
        int messages = 1;
        while (step instanceof MarketplaceOfferMatching.Paused paused) {
            progressFiles.saveProgress(STORE, EMPIK, paused.progress());
            step = split.resume(store, EMPIK, offers, progressFiles.progress(STORE, EMPIK).orElseThrow());
            messages++;
        }

        // then
        assertThat(messages).isEqualTo(4);
        assertThat(((MarketplaceOfferMatching.Finished) step).result()).isEqualTo(inOneMessage);
        assertThat(inOneMessage.total()).isEqualTo(10);
        assertThat(inOneMessage.ownOffers()).isEqualTo(1);
        assertThat(inOneMessage.alreadyLinked()).isEqualTo(1);
        assertThat(inOneMessage.linked()).extracting(OfferMatchResult.Row::sellerOfferKey)
                .containsExactly("XS-1", "OLD-2", "AMB", "OFF-1", "NOMFN", "MISS");
        assertThat(inOneMessage.toReview()).extracting(OfferMatchResult.Row::sellerOfferKey, OfferMatchResult.Row::reason)
                .containsExactly(tuple(" ", "no-key"), tuple("NOEAN", "no-ean"));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
