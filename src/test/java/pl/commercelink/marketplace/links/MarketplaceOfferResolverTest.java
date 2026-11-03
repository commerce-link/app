package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.links.ResolvedOffer.Source;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.commercelink.pim.ScopedPimCatalog.cloneEntry;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;

@ExtendWith(MockitoExtension.class)
class MarketplaceOfferResolverTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";

    @Mock
    private MarketplaceOfferLinkRepository links;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimView view;
    @Mock
    private StorePimAliases aliases;
    @Mock
    private ProductRepository products;
    @Mock
    private ProductCatalogRepository catalogs;

    @InjectMocks
    private MarketplaceOfferResolver resolver;

    private void link(String key, String pimId) {
        when(links.find(STORE, EMPIK, key)).thenReturn(Optional.of(
                MarketplaceOfferLink.of(STORE, EMPIK, key, List.of(pimId), MarketplaceOfferLinkSource.MANUAL, "u", Instant.now())));
    }

    private void storeSees(String pimId, PimEntry entry) {
        when(aliases.effectiveEntry(STORE, pimId)).thenReturn(Optional.of(entry));
    }

    private void mpnIn(String mpn, PimEntry entry) {
        when(pimCatalog.withStoreData(STORE)).thenReturn(view);
        when(view.findByMpn(mpn)).thenReturn(Optional.of(entry));
    }

    private void catalogCategory(String categoryId, String name) {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(categoryId);
        category.setName(name);
        ProductCatalog catalog = new ProductCatalog(STORE, "main");
        catalog.setCategories(List.of(category));
        when(catalogs.findAll(STORE)).thenReturn(List.of(catalog));
    }

    private Product catalogProduct(String categoryId, String productId, String pimId, String ean, String mfn) {
        Product projection = new Product(categoryId);
        projection.setProductId(productId);
        Product full = new Product(categoryId);
        full.setProductId(productId);
        full.setPimId(pimId);
        full.setEan(ean);
        full.setManufacturerCode(mfn);
        when(products.findAllByPimId(pimId)).thenReturn(List.of(projection));
        when(products.findByProductId(categoryId, productId)).thenReturn(full);
        return full;
    }

    @Test
    void resolvesThroughLinkFirst() {
        // given
        link("SILLO-84980", "pim-1");
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", "cat-a"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "SILLO-84980", "1696938202");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5900000000011", "M1", "cat-a", Source.LINK));
    }

    @Test
    void treatsOfferKeyAsOwnPimIdWhenNoLink() {
        // given
        storeSees("k7m2xq9pz4", globalEntry("k7m2xq9pz4", "5900000000011", "M1", "cat-a"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "k7m2xq9pz4", null);

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
        assertThat(result).map(ResolvedOffer::pimId).contains("k7m2xq9pz4");
    }

    @Test
    void resolvesOwnOfferPublishedUnderCloneSourceAsTheClone() {
        // given
        storeSees("src", cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "src", null);

        // then
        assertThat(result).map(ResolvedOffer::pimId).contains("clone");
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
    }

    @Test
    void fallsBackToManufacturerCodeAsBeforeWithoutReadingTheCatalogs() {
        // given
        mpnIn("1696938202", globalEntry("pim-2", "5900000000022", "1696938202", "cat-b"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, null, "1696938202");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-2", "5900000000022", "1696938202", "cat-b", Source.MFN));
        verifyNoInteractions(catalogs, products, links, aliases);
    }

    @Test
    void skipsLinkWhosePimEntryIsGone() {
        // given
        link("SILLO-84980", "pim-x");
        mpnIn("1696938202", globalEntry("pim-2", "5900000000022", "1696938202", "cat-b"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "SILLO-84980", "1696938202");

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.MFN);
        assertThat(result).map(ResolvedOffer::pimId).contains("pim-2");
    }

    @Test
    void prefersCatalogProductCodesOverPimPrimaryCodes() {
        // given
        link("K", "pim-1");
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", "cat-a"));
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        catalogCategory("c1", "Krzesła");
        Product elsewhere = new Product("other-store-category");
        elsewhere.setProductId("p2");
        Product projection = new Product("c1");
        projection.setProductId("p1");
        Product full = new Product("c1");
        full.setProductId("p1");
        full.setEan("5901234567890");
        full.setManufacturerCode("7041");
        when(products.findAllByPimId("pim-1")).thenReturn(List.of(elsewhere, projection));
        when(products.findByProductId("c1", "p1")).thenReturn(full);

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5901234567890", "7041", "cat-a", Source.LINK));
    }

    @Test
    void usesPimPrimaryCodesWhenPimKnowsTheIdButTheProductIsNotInTheStoreCatalog() {
        // given
        link("K", "pim-1");
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", "cat-a"));
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        catalogCategory("c1", "Krzesła");
        Product foreign = new Product("other-store-category");
        foreign.setProductId("p2");
        when(products.findAllByPimId("pim-1")).thenReturn(List.of(foreign));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5900000000011", "M1", "cat-a", Source.LINK));
    }

    @Test
    void findsTheStoreProductStillPinnedToTheClonesSource() {
        // given
        link("K", "src");
        storeSees("src", cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a"));
        when(aliases.aliasesOf(STORE, "clone")).thenReturn(Set.of("clone", "src"));
        catalogCategory("c1", "Krzesła");
        when(products.findAllByPimId("clone")).thenReturn(List.of());
        catalogProduct("c1", "p1", "src", "5901234567890", "7041");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).contains(new ResolvedOffer("clone", "5901234567890", "7041", "cat-a", Source.LINK));
    }

    @Test
    void usesTheCatalogCategoryNameWhenThePimEntryHasNoCategory() {
        // given
        link("K", "pim-1");
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", null));
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        catalogCategory("c1", "Krzesła");
        catalogProduct("c1", "p1", "pim-1", "5901234567890", "7041");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::category).contains("Krzesła");
    }

    @Test
    void aLinkHitWithoutAnyManufacturerCodeFallsThroughToTheManufacturerCodeStep() {
        // given
        link("K", "pim-3");
        storeSees("pim-3", globalEntry("pim-3", "5900000000033", null, "cat-c"));
        mpnIn("1696938202", globalEntry("pim-2", "5900000000022", "1696938202", "cat-b"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", "1696938202");

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.MFN);
        assertThat(result).map(ResolvedOffer::pimId).contains("pim-2");
    }

    @Test
    void anOwnOfferHitWithoutAnyManufacturerCodeIsUnresolved() {
        // given
        storeSees("pim-3", globalEntry("pim-3", "5900000000033", null, "cat-c"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-3", null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void readsTheStoresCatalogsOnceForAllLinesOfOneOrder() {
        // given
        link("K1", "pim-1");
        link("K2", "pim-2");
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", "cat-a"));
        storeSees("pim-2", globalEntry("pim-2", "5900000000022", "M2", "cat-b"));
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        when(aliases.aliasesOf(STORE, "pim-2")).thenReturn(Set.of("pim-2"));
        catalogCategory("c1", "Krzesła");
        MarketplaceOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        offers.resolve(EMPIK, "K1", null);
        offers.resolve(EMPIK, "K2", null);

        // then
        verify(catalogs, times(1)).findAll(STORE);
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        // given
        when(pimCatalog.withStoreData(STORE)).thenReturn(view);

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "unknown", "nope");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void aProductCountsAsInTheStoreCatalogOnlyInTheStoresOwnCategories() {
        // given
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        when(aliases.aliasesOf(STORE, "pim-2")).thenReturn(Set.of("pim-2"));
        when(aliases.aliasesOf(STORE, "pim-unknown")).thenReturn(Set.of("pim-unknown"));
        catalogCategory("c1", "Krzesła");
        catalogProduct("c1", "p1", "pim-1", null, null);
        Product foreign = new Product("other-store-category");
        foreign.setProductId("p2");
        when(products.findAllByPimId("pim-2")).thenReturn(List.of(foreign));

        // when / then
        assertThat(resolver.isInStoreCatalog(STORE, "pim-1")).isTrue();
        assertThat(resolver.isInStoreCatalog(STORE, "pim-2")).isFalse();
        assertThat(resolver.isInStoreCatalog(STORE, "pim-unknown")).isFalse();
    }

    @Test
    void aCloneCountsAsInTheStoreCatalogWhileTheProductIsPinnedToItsSource() {
        // given
        when(aliases.aliasesOf(STORE, "clone")).thenReturn(Set.of("clone", "src"));
        catalogCategory("c1", "Krzesła");
        when(products.findAllByPimId("clone")).thenReturn(List.of());
        catalogProduct("c1", "p1", "src", null, null);

        // when / then
        assertThat(resolver.isInStoreCatalog(STORE, "clone")).isTrue();
    }

    @Test
    void listsTheCatalogProductsWithAPimEntryReadingTheCatalogsOnce() {
        // given
        catalogCategory("c1", "Krzesła");
        Product pinned = new Product("c1", "pim-1", "5900000000011", "M1", "Brand", "Label", "Pinned", "default");
        Product unpinned = new Product("c1", null, "5900000000022", "M2", "Brand", "Label", "Unpinned", "default");
        when(products.findAll("c1")).thenReturn(List.of(pinned, unpinned));
        MarketplaceOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        List<Product> first = offers.catalogProducts();
        List<Product> second = offers.catalogProducts();

        // then
        assertThat(first).containsExactly(pinned);
        assertThat(second).containsExactly(pinned);
        verify(catalogs, times(1)).findAll(STORE);
        verify(products, times(1)).findAll("c1");
    }
}
