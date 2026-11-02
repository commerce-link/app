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
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.Product;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.pim.ScopedPimCatalog.cloneEntry;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;
import static pl.commercelink.pim.ScopedPimCatalog.storeEntry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferMatchingTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock
    private MarketplaceOfferLinkRepository links;
    @Mock
    private MarketplaceOfferResolver resolver;
    @Mock
    private MarketplaceOfferResolver.StoreOffers storeOffers;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE);
        when(resolver.forStore(STORE)).thenReturn(storeOffers);
        when(storeOffers.isInStoreCatalog(any())).thenReturn(true);
        when(storeOffers.catalogProducts()).thenReturn(List.of());
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of());
        when(links.saveIfAbsent(any())).thenReturn(true);
    }

    private MarketplaceOfferMatching matching(PimEntry... entries) {
        PimCatalog pim = ScopedPimCatalog.of(entries);
        return new MarketplaceOfferMatching(links, resolver, pim, new StorePimAliases(pim),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Product catalogProduct(String pimId, String ean) {
        return new Product("cat-1", pimId, ean, "MFN-" + pimId, "Brand", "Label", "Name " + pimId, "default");
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

    private static MarketplaceOfferLink existing(String key, String pimId, MarketplaceOfferLinkSource source) {
        return MarketplaceOfferLink.of(STORE, EMPIK, key, pimId, source, "someone", NOW.minusSeconds(3600));
    }

    private MarketplaceOfferLink savedLink() {
        ArgumentCaptor<MarketplaceOfferLink> captor = ArgumentCaptor.forClass(MarketplaceOfferLink.class);
        verify(links).saveIfAbsent(captor.capture());
        return captor.getValue();
    }

    @Test
    void savesEanLinkWhenOneEntryMatches() {
        // given
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        MarketplaceOfferLink link = savedLink();
        assertThat(link.getStoreId()).isEqualTo(STORE);
        assertThat(link.getMarketplace()).isEqualTo(EMPIK);
        assertThat(link.getSellerOfferKey()).isEqualTo("SILLO-1");
        assertThat(link.getPimId()).isEqualTo("pim-1");
        assertThat(link.getSource()).isEqualTo(MarketplaceOfferLinkSource.EAN);
        assertThat(link.getCreatedBy()).isEqualTo("offer-matching");
        assertThat(link.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.linked()).containsExactly(new OfferMatchResult.Row("SILLO-1", "Title SILLO-1",
                List.of("5903771700818"), "pim-1", null));
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.notInCatalog()).isEmpty();
        assertThat(result.noPimEntry()).isEmpty();
        assertThat(result.toReview()).isEmpty();
    }

    @Test
    void doesNotOverwriteManualLink() {
        // given
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("SILLO-1", "pim-9", MarketplaceOfferLinkSource.MANUAL)));
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        verify(links, never()).save(any());
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.linked()).isEmpty();
    }

    @Test
    void doesNotDuplicateExistingEanLink() {
        // given
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("SILLO-1", "pim-1", MarketplaceOfferLinkSource.EAN)));
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        verify(links, never()).save(any());
        assertThat(result.alreadyLinked()).isEqualTo(1);
    }

    @Test
    void reportsExistingLinkStillMissingFromCatalog() {
        // given an earlier run linked the offer, and the product is still not in the store's catalog
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("SILLO-1", "pim-1", MarketplaceOfferLinkSource.EAN)));
        when(storeOffers.isInStoreCatalog("pim-1")).thenReturn(false);
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.notInCatalog()).containsExactly(new OfferMatchResult.Row("SILLO-1", "Title SILLO-1",
                List.of("5903771700818"), "pim-1", "already-linked", true, "5903771700818"));
    }

    @Test
    void doesNotReportExistingLinkInCatalog() {
        // given
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("SILLO-1", "pim-1", MarketplaceOfferLinkSource.EAN)));
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.linked()).isEmpty();
        assertThat(result.notInCatalog()).isEmpty();
        assertThat(result.toReview()).isEmpty();
    }

    @Test
    void reportsStaleLinkForReview() {
        // given the link points at an entry the PIM no longer knows
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("SILLO-1", "pim-gone", MarketplaceOfferLinkSource.MANUAL)));
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then nothing is written: the operator decides
        verify(links, never()).saveIfAbsent(any());
        verify(links, never()).save(any());
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.toReview()).containsExactly(new OfferMatchResult.Row("SILLO-1", "Title SILLO-1",
                List.of("5903771700818"), "pim-gone", "stale-link"));
        assertThat(result.notInCatalog()).isEmpty();
    }

    @Test
    void reportsLinkSavedMeanwhileStillMissingFromCatalog() {
        // given
        when(links.saveIfAbsent(any())).thenReturn(false);
        when(links.find(STORE, EMPIK, "SILLO-1")).thenReturn(Optional.of(
                existing("SILLO-1", "pim-1", MarketplaceOfferLinkSource.MANUAL)));
        when(storeOffers.isInStoreCatalog("pim-1")).thenReturn(false);
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.notInCatalog()).extracting(OfferMatchResult.Row::pimId, OfferMatchResult.Row::reason)
                .containsExactly(tuple("pim-1", "already-linked"));
    }

    @Test
    void countsAnOfferRepeatedAcrossPagesOnce() {
        // given the listing shifted between pages and returned the same offer twice
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(
                List.of(offer("SILLO-1", "5903771700818")),
                List.of(offer("SILLO-1", "5903771700818"), offer("SILLO-2", "5900000000000"))));

        // then
        verify(links, times(1)).saveIfAbsent(any());
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.linked()).hasSize(1);
        assertThat(result.alreadyLinked()).isZero();
        assertThat(result.noPimEntry()).hasSize(1);
    }

    @Test
    void countsLinkSavedMeanwhileAsAlreadyLinked() {
        // given an operator linked the offer after the run read the store's links
        when(links.saveIfAbsent(any())).thenReturn(false);
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        verify(links, never()).save(any());
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.linked()).isEmpty();
    }

    @Test
    void sendsAmbiguousEansToReview() {
        // given
        MarketplaceOfferMatching matching = matching(
                globalEntry("pim-1", "5903771700818", "7041", "Zabawki"),
                globalEntry("pim-2", "5903771700825", "7042", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818", "5903771700825"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.toReview()).containsExactly(new OfferMatchResult.Row("SILLO-1", "Title SILLO-1",
                List.of("5903771700818", "5903771700825"), null, "ambiguous"));
        assertThat(result.linked()).isEmpty();
    }

    @Test
    void linksOfferWhoseEansAllHitTheSameEntry() {
        // given one entry with two GTINs, and an EAN the PIM does not know
        PimEntry entry = new PimEntry("pim-1", List.of(
                new PimIdentifier("5903771700818", PimIdentifierType.GTIN),
                new PimIdentifier("5903771700825", PimIdentifierType.GTIN)),
                "Brand", "Name", "Zabawki", null, true, 1, 1);
        MarketplaceOfferMatching matching = matching(entry);

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818", "5903771700825", "5900000000000"))));

        // then
        assertThat(savedLink().getPimId()).isEqualTo("pim-1");
        assertThat(result.linked()).extracting(OfferMatchResult.Row::pimId).containsExactly("pim-1");
        assertThat(result.toReview()).isEmpty();
    }

    @Test
    void treatsTheStoresCloneAndItsSourceAsOneEntry() {
        // given the store's clone carries an EAN of its own, the source keeps the original one
        MarketplaceOfferMatching matching = matching(
                globalEntry("src", "5903771700818", "7041", "Zabawki"),
                cloneEntry("clone", STORE, "src", "5903771700825", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818", "5903771700825"))));

        // then the link points at the source id, which resolves to the store's clone
        assertThat(result.toReview()).isEmpty();
        assertThat(savedLink().getPimId()).isEqualTo("src");
        assertThat(result.linked()).extracting(OfferMatchResult.Row::pimId).containsExactly("src");
    }

    @Test
    void sendsOfferWithoutEanToReview() {
        // given
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1"), offer("SILLO-2", " ", null))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.toReview()).extracting(OfferMatchResult.Row::sellerOfferKey, OfferMatchResult.Row::reason)
                .containsExactly(
                        tuple("SILLO-1", "no-ean"),
                        tuple("SILLO-2", "no-ean"));
    }

    @Test
    void sendsOfferWithoutKeyToReview() {
        // given
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer(" ", "5903771700818"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.toReview()).extracting(OfferMatchResult.Row::reason).containsExactly("no-key");
    }

    @Test
    void countsOwnOffersWithoutLinking() {
        // given one offer published under our pimId, one under the source id of the store's clone
        MarketplaceOfferMatching matching = matching(
                globalEntry("pim-1", "5903771700818", "7041", "Zabawki"),
                globalEntry("src", "5903771700825", "7042", "Zabawki"),
                cloneEntry("clone", STORE, "src", "5903771700825", "7042", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("pim-1", "5903771700818"), offer("src", "5903771700825"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.ownOffers()).isEqualTo(2);
        assertThat(result.linked()).isEmpty();
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void marksLinkedProductMissingFromCatalog() {
        // given
        when(storeOffers.isInStoreCatalog("pim-1")).thenReturn(false);
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then the link is saved anyway: imports use it now, price and export once the product is in the catalog
        assertThat(savedLink().getPimId()).isEqualTo("pim-1");
        assertThat(result.notInCatalog()).extracting(OfferMatchResult.Row::pimId).containsExactly("pim-1");
        assertThat(result.linked()).isEmpty();
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
        assertThat(result.noPimEntry()).extracting(OfferMatchResult.Row::sellerOfferKey, OfferMatchResult.Row::active)
                .containsExactly(tuple("OLD-1", false), tuple("NEW-1", true));
    }

    @Test
    void givesALinkedRowMissingFromTheCatalogThePimEntrysPrimaryGtin() {
        // given the offer carries two EANs, the entry's primary GTIN is the first of its own
        when(storeOffers.isInStoreCatalog("pim-1")).thenReturn(false);
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5900000000000", "5903771700818"))));

        // then
        assertThat(result.notInCatalog()).extracting(OfferMatchResult.Row::catalogEan).containsExactly("5903771700818");
    }

    @Test
    void reportsEanWithoutPimEntry() {
        // given
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5900000000000"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.noPimEntry()).containsExactly(new OfferMatchResult.Row("SILLO-1", "Title SILLO-1",
                List.of("5900000000000"), null, null));
    }

    @Test
    void prefersTheCatalogProductsEntryOverTheStoresOwnCardWithTheSameEan() {
        // given the store's own card shares the EAN of the global entry its catalog product uses
        when(storeOffers.catalogProducts()).thenReturn(List.of(catalogProduct("pim-g", "730143315289")));
        when(storeOffers.isInStoreCatalog("store-card")).thenReturn(false);
        MarketplaceOfferMatching matching = matching(
                globalEntry("pim-g", "730143315289", "100-100001084WOF", "CPU"),
                storeEntry("store-card", STORE, "730143315289", "STORE-OWN-01", "CPU"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-84980", "730143315289"))));

        // then the link points at the catalog product's entry, so imports and the export use it
        assertThat(savedLink().getPimId()).isEqualTo("pim-g");
        assertThat(result.linked()).extracting(OfferMatchResult.Row::pimId).containsExactly("pim-g");
        assertThat(result.notInCatalog()).isEmpty();
        assertThat(result.toReview()).isEmpty();
    }

    @Test
    void findsTheCatalogProductByTheGtinOfItsEntryWhenTheProductHasNoEan() {
        // given
        when(storeOffers.catalogProducts()).thenReturn(List.of(catalogProduct("pim-g", null)));
        MarketplaceOfferMatching matching = matching(
                globalEntry("pim-g", "730143315289", "M1", "CPU"),
                storeEntry("store-card", STORE, "730143315289", "STORE-OWN-01", "CPU"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-84980", "730143315289"))));

        // then
        assertThat(savedLink().getPimId()).isEqualTo("pim-g");
        assertThat(result.linked()).hasSize(1);
    }

    @Test
    void sendsAnEanOnBothAStoreCardAndAGlobalCardOutsideTheCatalogToReview() {
        // given neither card is used by a catalog product
        MarketplaceOfferMatching matching = matching(
                globalEntry("pim-g", "730143315289", "M1", "CPU"),
                storeEntry("store-card", STORE, "730143315289", "STORE-OWN-01", "CPU"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-84980", "730143315289"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.toReview()).extracting(OfferMatchResult.Row::sellerOfferKey, OfferMatchResult.Row::reason)
                .containsExactly(tuple("SILLO-84980", "ambiguous"));
        assertThat(result.linked()).isEmpty();
        assertThat(result.notInCatalog()).isEmpty();
    }

    @Test
    void sendsAnEanOfTwoCatalogProductsWithDifferentEntriesToReview() {
        // given
        when(storeOffers.catalogProducts()).thenReturn(List.of(
                catalogProduct("pim-1", "5903771700818"), catalogProduct("pim-2", "5903771700818")));
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "M1", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.toReview()).extracting(OfferMatchResult.Row::reason).containsExactly("ambiguous");
    }

    @Test
    void countsCatalogProductsOnTheCloneAndOnItsSourceAsOneEntry() {
        // given one product still pinned to the source, another moved onto the store's clone
        when(storeOffers.catalogProducts()).thenReturn(List.of(
                catalogProduct("src", "5903771700818"), catalogProduct("clone", "5903771700818")));
        MarketplaceOfferMatching matching = matching(
                globalEntry("src", "5903771700818", "7041", "Zabawki"),
                cloneEntry("clone", STORE, "src", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        assertThat(savedLink().getPimId()).isEqualTo("src");
        assertThat(result.toReview()).isEmpty();
    }

    @Test
    void sendsAnOfferWhoseEansHitACatalogProductAndAnotherEntryToReview() {
        // given
        when(storeOffers.catalogProducts()).thenReturn(List.of(catalogProduct("pim-1", "5903771700818")));
        MarketplaceOfferMatching matching = matching(
                globalEntry("pim-1", "5903771700818", "M1", "Zabawki"),
                globalEntry("pim-2", "5903771700825", "M2", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(List.of(offer("SILLO-1", "5903771700818", "5903771700825"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.toReview()).extracting(OfferMatchResult.Row::reason).containsExactly("ambiguous");
    }

    @Test
    void readsTheStoresLinksAndCatalogsOncePerMessage() {
        // given
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"),
                globalEntry("pim-2", "5903771700825", "7042", "Zabawki"));

        // when
        OfferMatchResult result = runToEnd(matching, pages(
                List.of(offer("SILLO-1", "5903771700818")),
                List.of(offer("SILLO-2", "5903771700825"), offer("SILLO-3", "5900000000000"))));

        // then
        verify(resolver, times(1)).forStore(STORE);
        verify(storeOffers, times(1)).catalogProducts();
        verify(links, times(1)).findAll(STORE, EMPIK);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.linked()).hasSize(2);
        assertThat(result.noPimEntry()).hasSize(1);
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
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));
        ListedPages offers = pages(List.of(offer("SILLO-1", "5903771700818")), List.of(offer("SILLO-2", "5900000000000")));

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
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));
        ListedPages offers = pages(List.of(offer("SILLO-1", "5903771700818")), List.of(offer("SILLO-2", "5900000000000")),
                List.of(offer("SILLO-3", "5900000000001"))).rateLimitedOnceAt(1);

        // when
        MarketplaceOfferMatching.Step step = matching.resume(store, EMPIK, offers, fresh());

        // then the first page is counted, the second is where the next message starts
        assertThat(step).isInstanceOfSatisfying(MarketplaceOfferMatching.Paused.class, paused -> {
            assertThat(paused.delay()).isEqualTo(Duration.ofSeconds(60));
            OfferMatchProgress progress = paused.progress();
            assertThat(progress.nextCursor()).isEqualTo("1");
            assertThat(progress.processed()).isEqualTo(1);
            assertThat(progress.total()).isEqualTo(3);
            assertThat(progress.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("SILLO-1");
            assertThat(progress.seenKeys()).containsExactly("SILLO-1");
            assertThat(progress.runId()).isEqualTo("run-1");
            assertThat(progress.status()).isEqualTo(OfferMatchProgress.Status.RUNNING);
        });
    }

    @Test
    void continuesFromCursorAfterRateLimit() {
        // given a run paused before page 1
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));
        ListedPages offers = pages(List.of(offer("SILLO-1", "5903771700818")), List.of(offer("SILLO-2", "5900000000000")))
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
        assertThat(result.linked()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("SILLO-1");
        assertThat(result.noPimEntry()).extracting(OfferMatchResult.Row::sellerOfferKey).containsExactly("SILLO-2");
        verify(links, times(1)).saveIfAbsent(any());
    }

    @Test
    void pausesAtTheFirstPageWithoutACursor() {
        // given
        MarketplaceOfferMatching matching = matching();
        ListedPages offers = pages(List.of(offer("SILLO-1", "5900000000000"))).rateLimitedOnceAt(0);

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
        PimCatalog pim = ScopedPimCatalog.of();
        MarketplaceOfferMatching matching = new MarketplaceOfferMatching(links, resolver, pim, new StorePimAliases(pim), clock);
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
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));
        ListedPages offers = pages(List.of(offer("SILLO-1", "5903771700818")),
                List.of(offer("SILLO-1", "5903771700818"), offer("SILLO-2", "5900000000000"))).rateLimitedOnceAt(1);

        // when
        OfferMatchResult result = runToEnd(matching, offers);

        // then
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.linked()).hasSize(1);
        assertThat(result.alreadyLinked()).isZero();
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
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(
                existing("OLD-1", "pim-3", MarketplaceOfferLinkSource.MANUAL),
                existing("OLD-2", "pim-gone", MarketplaceOfferLinkSource.EAN)));
        when(storeOffers.isInStoreCatalog("pim-3")).thenReturn(false);
        when(storeOffers.catalogProducts()).thenReturn(List.of(catalogProduct("pim-g", "730143315289")));
        PimEntry[] entries = {
                globalEntry("pim-1", "5903771700818", "7041", "Zabawki"),
                globalEntry("pim-2", "5903771700825", "7042", "Zabawki"),
                globalEntry("pim-3", "5903771700832", "7043", "Zabawki"),
                globalEntry("pim-g", "730143315289", "M1", "CPU"),
                storeEntry("store-card", STORE, "730143315289", "STORE-OWN-01", "CPU"),
                globalEntry("own-1", "5903771700849", "7044", "Zabawki")};
        MarketplaceListedOffer inactive = new MarketplaceListedOffer("OFF-1", List.of("5900000000009"), "Stara", 0,
                new BigDecimal("1.00"), false);
        List<List<MarketplaceListedOffer>> listing = List.of(
                List.of(offer("SILLO-1", "5903771700818"), offer("own-1", "5903771700849"), offer(" ", "5900000000001")),
                List.of(offer("OLD-1", "5903771700832"), offer("OLD-2", "5903771700818"), offer("AMB", "5903771700818", "5903771700825")),
                List.of(offer("SILLO-1", "5903771700818"), offer("NOEAN"), inactive),
                List.of(offer("CPU", "730143315289"), offer("MISS", "5900000000002")));
        OfferMatchResult inOneMessage = runToEnd(matching(entries), new ListedPages(listing));

        // when the same listing is read with a 429 before pages 1, 2 and 3, each part going through the progress file
        Map<String, byte[]> files = new HashMap<>();
        FileStorage storage = mock(FileStorage.class);
        doAnswer(call -> files.put(call.getArgument(1), call.getArgument(2))).when(storage).put(any(), any(), any());
        when(storage.canRead(any(), any())).thenAnswer(call -> files.containsKey(call.<String>getArgument(1)));
        when(storage.getBytes(any(), any())).thenAnswer(call -> files.get(call.<String>getArgument(1)));
        OfferMatchResultStore progressFiles = new OfferMatchResultStore(storage, "stores");
        MarketplaceOfferMatching split = matching(entries);
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
