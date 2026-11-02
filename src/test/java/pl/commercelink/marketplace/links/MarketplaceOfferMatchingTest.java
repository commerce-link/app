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
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.pim.ScopedPimCatalog.cloneEntry;
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
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of());
        when(links.saveIfAbsent(any())).thenReturn(true);
    }

    private MarketplaceOfferMatching matching(PimEntry... entries) {
        PimCatalog pim = ScopedPimCatalog.of(entries);
        return new MarketplaceOfferMatching(links, resolver, pim, new StorePimAliases(pim),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MarketplaceListedOffer offer(String key, String... eans) {
        return new MarketplaceListedOffer(key, Arrays.asList(eans), "Title " + key, 3, new BigDecimal("19.99"), true);
    }

    @SafeVarargs
    private static MarketplaceOffers pages(List<MarketplaceListedOffer>... pages) {
        return consumer -> Arrays.stream(pages).forEach(consumer);
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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.alreadyLinked()).isEqualTo(1);
        assertThat(result.notInCatalog()).containsExactly(new OfferMatchResult.Row("SILLO-1", "Title SILLO-1",
                List.of("5903771700818"), "pim-1", "already-linked"));
    }

    @Test
    void doesNotReportExistingLinkInCatalog() {
        // given
        when(links.findAll(STORE, EMPIK)).thenReturn(List.of(existing("SILLO-1", "pim-1", MarketplaceOfferLinkSource.EAN)));
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(
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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK,
                pages(List.of(offer("SILLO-1", "5903771700818", "5903771700825"))));

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
        OfferMatchResult result = matching.match(store, EMPIK,
                pages(List.of(offer("SILLO-1", "5903771700818", "5903771700825", "5900000000000"))));

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
        OfferMatchResult result = matching.match(store, EMPIK,
                pages(List.of(offer("SILLO-1", "5903771700818", "5903771700825"))));

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
        OfferMatchResult result = matching.match(store, EMPIK,
                pages(List.of(offer("SILLO-1"), offer("SILLO-2", " ", null))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer(" ", "5903771700818"))));

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
        OfferMatchResult result = matching.match(store, EMPIK,
                pages(List.of(offer("pim-1", "5903771700818"), offer("src", "5903771700825"))));

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
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5903771700818"))));

        // then the link is saved anyway: it starts working once the product is added to the catalog
        assertThat(savedLink().getPimId()).isEqualTo("pim-1");
        assertThat(result.notInCatalog()).extracting(OfferMatchResult.Row::pimId).containsExactly("pim-1");
        assertThat(result.linked()).isEmpty();
    }

    @Test
    void reportsEanWithoutPimEntry() {
        // given
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"));

        // when
        OfferMatchResult result = matching.match(store, EMPIK, pages(List.of(offer("SILLO-1", "5900000000000"))));

        // then
        verify(links, never()).saveIfAbsent(any());
        assertThat(result.noPimEntry()).containsExactly(new OfferMatchResult.Row("SILLO-1", "Title SILLO-1",
                List.of("5900000000000"), null, null));
    }

    @Test
    void readsTheStoresLinksAndCatalogsOncePerRun() {
        // given
        MarketplaceOfferMatching matching = matching(globalEntry("pim-1", "5903771700818", "7041", "Zabawki"),
                globalEntry("pim-2", "5903771700825", "7042", "Zabawki"));

        // when
        OfferMatchResult result = matching.match(store, EMPIK, pages(
                List.of(offer("SILLO-1", "5903771700818")),
                List.of(offer("SILLO-2", "5903771700825"), offer("SILLO-3", "5900000000000"))));

        // then
        verify(resolver, times(1)).forStore(STORE);
        verify(links, times(1)).findAll(STORE, EMPIK);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.linked()).hasSize(2);
        assertThat(result.noPimEntry()).hasSize(1);
    }

    @Test
    void propagatesAFailedPage() {
        // given
        MarketplaceOfferMatching matching = matching();
        MarketplaceOffers failing = consumer -> {
            throw new IllegalStateException("OF21 failed");
        };

        // when / then
        assertThatThrownBy(() -> matching.match(store, EMPIK, failing))
                .isInstanceOf(IllegalStateException.class);
    }
}
