package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.links.ResolvedOffer.Source;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.pim.ScopedPimCatalog.cloneEntry;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;

class MarketplaceOfferResolverTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";

    private final MarketplaceOfferLinkRepository links = mock(MarketplaceOfferLinkRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductCatalogRepository catalogs = mock(ProductCatalogRepository.class);

    private MarketplaceOfferResolver resolverOver(PimCatalog pim) {
        when(links.find(any(), any(), any())).thenReturn(Optional.empty());
        when(products.findAllByPimId(any())).thenReturn(List.of());
        when(catalogs.findAll(STORE)).thenReturn(List.of());
        return new MarketplaceOfferResolver(links, pim, new StorePimAliases(pim), products, catalogs);
    }

    private void link(String key, String pimId) {
        when(links.find(STORE, EMPIK, key)).thenReturn(Optional.of(
                MarketplaceOfferLink.of(STORE, EMPIK, key, pimId, MarketplaceOfferLinkSource.MANUAL, "u", Instant.now())));
    }

    @Test
    void resolvesThroughLinkFirst() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("pim-1", "5900000000011", "M1", "cat-a"),
                globalEntry("pim-2", "5900000000022", "1696938202", "cat-b")));
        link("SILLO-84980", "pim-1");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "SILLO-84980", "1696938202");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5900000000011", "M1", "cat-a", Source.LINK));
    }

    @Test
    void treatsOfferKeyAsOwnPimIdWhenNoLink() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("k7m2xq9pz4", "5900000000011", "M1", "cat-a")));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "k7m2xq9pz4", null);

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
        assertThat(result).map(ResolvedOffer::pimId).contains("k7m2xq9pz4");
    }

    @Test
    void resolvesOwnOfferPublishedUnderCloneSource() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("src", "5900000000011", "M1", "cat-a"),
                cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a")));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "src", null);

        // then
        assertThat(result).map(ResolvedOffer::pimId).contains("clone");
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
    }

    @Test
    void resolvesLinkToCloneSourceAsClone() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("src", "5900000000011", "M1", "cat-a"),
                cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a")));
        link("K", "src");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::pimId).contains("clone");
        assertThat(result).map(ResolvedOffer::source).contains(Source.LINK);
    }

    @Test
    void fallsBackToManufacturerCodeAsBefore() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("pim-2", "5900000000022", "1696938202", "cat-b")));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, null, "1696938202");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-2", "5900000000022", "1696938202", "cat-b", Source.MFN));
    }

    @Test
    void skipsLinkWhosePimEntryIsGone() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("pim-2", "5900000000022", "1696938202", "cat-b")));
        link("SILLO-84980", "pim-x");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "SILLO-84980", "1696938202");

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.MFN);
        assertThat(result).map(ResolvedOffer::pimId).contains("pim-2");
    }

    @Test
    void prefersCatalogProductCodesOverPimPrimaryCodes() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("pim-1", "5900000000011", "M1", "cat-a")));
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId("c1");
        ProductCatalog catalog = new ProductCatalog(STORE, "main");
        catalog.setCategories(List.of(category));
        when(catalogs.findAll(STORE)).thenReturn(List.of(catalog));
        Product projection = new Product("c1");
        projection.setProductId("p1");
        Product elsewhere = new Product("other-store-category");
        elsewhere.setProductId("p2");
        Product full = new Product("c1");
        full.setProductId("p1");
        full.setPimId("pim-1");
        full.setEan("5901234567890");
        full.setManufacturerCode("7041");
        when(products.findAllByPimId("pim-1")).thenReturn(List.of(elsewhere, projection));
        when(products.findByProductId("c1", "p1")).thenReturn(full);
        link("K", "pim-1");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5901234567890", "7041", "cat-a", Source.LINK));
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("pim-1", "5900000000011", "M1", "cat-a")));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "unknown", "nope");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void aProductCountsAsInTheStoreCatalogOnlyInTheStoresOwnCategories() {
        // given
        MarketplaceOfferResolver resolver = resolverOver(ScopedPimCatalog.of(
                globalEntry("pim-1", "5900000000011", "M1", "cat-a")));
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId("c1");
        ProductCatalog catalog = new ProductCatalog(STORE, "main");
        catalog.setCategories(List.of(category));
        when(catalogs.findAll(STORE)).thenReturn(List.of(catalog));
        Product own = new Product("c1");
        own.setProductId("p1");
        Product foreign = new Product("other-store-category");
        foreign.setProductId("p2");
        when(products.findAllByPimId("pim-1")).thenReturn(List.of(own));
        when(products.findAllByPimId("pim-2")).thenReturn(List.of(foreign));
        when(products.findByProductId("c1", "p1")).thenReturn(own);

        // when / then
        assertThat(resolver.isInStoreCatalog(STORE, "pim-1")).isTrue();
        assertThat(resolver.isInStoreCatalog(STORE, "pim-2")).isFalse();
        assertThat(resolver.isInStoreCatalog(STORE, "pim-unknown")).isFalse();
    }
}
