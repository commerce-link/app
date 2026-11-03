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

    private void link(String key, String... eans) {
        when(links.find(STORE, EMPIK, key)).thenReturn(Optional.of(
                MarketplaceOfferLink.of(STORE, EMPIK, key, List.of(eans), MarketplaceOfferLinkSource.MANUAL, "u", Instant.now())));
    }

    private static CategoryDefinition category(String categoryId, String name) {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(categoryId);
        category.setName(name);
        return category;
    }

    private static ProductCatalog catalog(String catalogId, CategoryDefinition... categories) {
        ProductCatalog catalog = new ProductCatalog(STORE, catalogId);
        catalog.setCatalogId(catalogId);
        catalog.setCategories(List.of(categories));
        return catalog;
    }

    private void storeCatalogs(ProductCatalog... storeCatalogs) {
        when(catalogs.findAll(STORE)).thenReturn(List.of(storeCatalogs));
    }

    private static Product product(String categoryId, String productId, String pimId, String ean, String mfn) {
        Product product = new Product(categoryId, pimId, ean, mfn, "Brand", "Label", "Name " + productId, "default");
        product.setProductId(productId);
        return product;
    }

    private void categoryHolds(String categoryId, Product... categoryProducts) {
        when(products.findAll(categoryId)).thenReturn(List.of(categoryProducts));
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
    void anOwnOfferHitWithoutAnyManufacturerCodeIsUnresolved() {
        // given
        storeSees("pim-3", globalEntry("pim-3", "5900000000033", null, "cat-c"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-3", null);

        // then
        assertThat(result).isEmpty();
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
        assertThat(resolver.forStore(STORE).isInStoreCatalog("pim-1")).isTrue();
        assertThat(resolver.forStore(STORE).isInStoreCatalog("pim-2")).isFalse();
        assertThat(resolver.forStore(STORE).isInStoreCatalog("pim-unknown")).isFalse();
    }

    @Test
    void aCloneCountsAsInTheStoreCatalogWhileTheProductIsPinnedToItsSource() {
        // given
        when(aliases.aliasesOf(STORE, "clone")).thenReturn(Set.of("clone", "src"));
        catalogCategory("c1", "Krzesła");
        when(products.findAllByPimId("clone")).thenReturn(List.of());
        catalogProduct("c1", "p1", "src", null, null);

        // when / then
        assertThat(resolver.forStore(STORE).isInStoreCatalog("clone")).isTrue();
    }

    @Test
    void listsEveryCatalogProductInStableOrderReadingTheCatalogsOnce() {
        // given
        storeCatalogs(catalog("b", category("c3", "Lampy")), catalog("a", category("c2", "Biurka"), category("c1", "Krzesła")));
        Product pinned = product("c1", "p2", "pim-1", "5900000000011", "M1");
        Product unpinned = product("c1", "p1", null, "5900000000022", "M2");
        Product desk = product("c2", "p9", null, "5900000000033", "M3");
        Product lamp = product("c3", "p0", null, "5900000000044", "M4");
        categoryHolds("c1", pinned, unpinned);
        categoryHolds("c2", desk);
        categoryHolds("c3", lamp);
        MarketplaceOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        List<Product> first = offers.catalogProducts();
        List<Product> second = offers.catalogProducts();

        // then
        assertThat(first).containsExactly(desk, unpinned, pinned, lamp);
        assertThat(second).isSameAs(first);
        verify(catalogs, times(1)).findAll(STORE);
        verify(products, times(1)).findAll("c1");
    }

    @Test
    void resolvesALinkToTheCatalogProductCarryingItsEanWithoutAskingThePim() {
        // given
        link("K", "5901234567890");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", "pim-1", "5901234567890", "7041"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", "1696938202");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5901234567890", "7041", "Krzesła", Source.LINK));
        verifyNoInteractions(pimCatalog, aliases);
    }

    @Test
    void resolvesALinkToACatalogProductWithoutAPimCard() {
        // given
        link("K", "5901234567890");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", null, "5901234567890", "7041"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).contains(new ResolvedOffer(null, "5901234567890", "7041", "Krzesła", Source.LINK));
    }

    @Test
    void aLinkWithoutACatalogProductFallsThroughToTheManufacturerCodeStep() {
        // given
        link("K", "5901234567890");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", "pim-9", "5909999999999", "9999"));
        mpnIn("1696938202", globalEntry("pim-2", "5900000000022", "1696938202", "cat-b"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", "1696938202");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-2", "5900000000022", "1696938202", "cat-b", Source.MFN));
    }

    @Test
    void aLinkWithoutACatalogProductFallsThroughToTheOwnOfferStep() {
        // given
        link("k7m2xq9pz4", "5901234567890");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1");
        storeSees("k7m2xq9pz4", globalEntry("k7m2xq9pz4", "5900000000011", "M1", "cat-a"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "k7m2xq9pz4", null);

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
    }

    @Test
    void eansHittingProductsWithDifferentManufacturerCodesLeaveTheLineUnresolved() {
        // given
        link("K", "5901234567890", "5901234567891");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", null, "5901234567890", "7041"),
                product("c1", "p2", null, "5901234567891", "7042"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void theSameManufacturerCodeInTwoCategoriesTakesTheFirstInCatalogOrder() {
        // given
        link("K", "5901234567890", "5901234567891");
        storeCatalogs(catalog("b", category("c3", "Lampy")), catalog("a", category("c2", "Biurka"), category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p0", null, "5901234567890", "7041"));
        categoryHolds("c2", product("c2", "p2", null, "5901234567890", "70 41"),
                product("c2", "p1", "pim-1", "5901234567891", "7041"));
        categoryHolds("c3", product("c3", "p3", null, "5901234567890", "7041"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5901234567891", "7041", "Biurka", Source.LINK));
    }

    @Test
    void aLinkedProductWithoutAManufacturerCodeFallsThroughToTheManufacturerCodeStep() {
        // given
        link("K", "5901234567890");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", "pim-3", "5901234567890", null));
        mpnIn("1696938202", globalEntry("pim-2", "5900000000022", "1696938202", "cat-b"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", "1696938202");

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.MFN);
        assertThat(result).map(ResolvedOffer::pimId).contains("pim-2");
    }

    @Test
    void aLinkWithSeveralEansResolvesThroughAnyOfThem() {
        // given
        link("K", "5900000000099", "5901234567890");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", null, "5901234567890", "7041"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::mfn).contains("7041");
    }

    @Test
    void readsTheStoresCatalogsOnceForAllLinesOfOneOrder() {
        // given
        link("K1", "5901234567890");
        link("K2", "5901234567891");
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", null, "5901234567890", "7041"),
                product("c1", "p2", null, "5901234567891", "7042"));
        MarketplaceOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        offers.resolve(EMPIK, "K1", null);
        offers.resolve(EMPIK, "K2", null);

        // then
        verify(catalogs, times(1)).findAll(STORE);
        verify(products, times(1)).findAll("c1");
    }

    @Test
    void matchCatalogUnifiesTheGivenEansAndIgnoresBlanks() {
        // given
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        Product chair = product("c1", "p1", null, "5901234567890", "7041");
        categoryHolds("c1", chair);
        List<String> eans = new java.util.ArrayList<>(List.of("0005901234567890", " "));
        eans.add(null);

        // when
        CatalogMatch match = resolver.forStore(STORE).matchCatalog(eans);

        // then
        assertThat(match).isEqualTo(new CatalogMatch.Found(chair, "Krzesła"));
    }

    @Test
    void matchCatalogWithoutEansIsNotInCatalogWithoutReadingTheCatalogs() {
        // when
        CatalogMatch match = resolver.forStore(STORE).matchCatalog(List.of());

        // then
        assertThat(match).isInstanceOf(CatalogMatch.NotInCatalog.class);
        verifyNoInteractions(catalogs, products);
    }

    @Test
    void matchCatalogWithoutAHitIsNotInCatalog() {
        // given
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        categoryHolds("c1", product("c1", "p1", null, "5901234567890", "7041"));

        // when
        CatalogMatch match = resolver.forStore(STORE).matchCatalog(List.of("5900000000099"));

        // then
        assertThat(match).isInstanceOf(CatalogMatch.NotInCatalog.class);
    }

    @Test
    void matchCatalogTreatsAHitWithoutAManufacturerCodeNextToOneWithACodeAsAmbiguous() {
        // given
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        Product coded = product("c1", "p1", null, "5901234567890", "7041");
        Product uncoded = product("c1", "p2", null, "5901234567890", " ");
        categoryHolds("c1", coded, uncoded);

        // when
        CatalogMatch match = resolver.forStore(STORE).matchCatalog(List.of("5901234567890"));

        // then
        assertThat(match).isEqualTo(new CatalogMatch.Ambiguous(List.of(coded, uncoded)));
    }

    @Test
    void matchCatalogWithOnlyUncodedHitsHasNoManufacturerCode() {
        // given
        storeCatalogs(catalog("main", category("c1", "Krzesła")));
        Product first = product("c1", "p1", null, "5901234567890", null);
        Product second = product("c1", "p2", null, "5901234567891", "");
        categoryHolds("c1", first, second);

        // when
        CatalogMatch match = resolver.forStore(STORE).matchCatalog(List.of("5901234567890", "5901234567891"));

        // then
        assertThat(match).isEqualTo(new CatalogMatch.NoManufacturerCode(List.of(first, second)));
    }
}
