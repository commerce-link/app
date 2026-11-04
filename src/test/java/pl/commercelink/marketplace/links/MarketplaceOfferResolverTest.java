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
import pl.commercelink.products.StoreCategoryResolver;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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
    @Mock
    private TaxonomyCache taxonomy;
    @Mock
    private StoreCategoryResolver storeCategories;

    @InjectMocks
    private MarketplaceOfferResolver resolver;

    private void inTaxonomy(String ean, String category, String categoryId) {
        when(taxonomy.findByEan(ean)).thenReturn(new Taxonomy(ean, "MFN-" + ean, "Brand", "Name " + ean, category, 1,
                null, null, "raw", categoryId));
    }

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
    void anOwnOfferHitWithoutAManufacturerCodeIsResolvedByItsEan() {
        // given
        storeSees("pim-3", globalEntry("pim-3", "05900000000033", null, "cat-c"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-3", null);

        // then
        assertThat(result).contains(new ResolvedOffer("pim-3", "5900000000033", null, "cat-c", Source.OWN_OFFER));
        assertThat(result).map(ResolvedOffer::sku).contains("5900000000033");
    }

    @Test
    void anOwnOfferHitWithNeitherAnEanNorAManufacturerCodeIsUnresolved() {
        // given
        storeSees("pim-3", globalEntry("pim-3", null, null, "cat-c"));

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
    void resolvesALinkToItsEanWithoutReadingTheCatalogsOrAskingThePim() {
        // given
        link("K", "5901234567890");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", "1696938202");

        // then the goods need not be in a catalog: fulfilment finds them by the EAN
        assertThat(result).contains(new ResolvedOffer(null, "5901234567890", null, null, Source.LINK));
        assertThat(result).map(ResolvedOffer::sku).contains("5901234567890");
        verifyNoInteractions(pimCatalog, aliases, catalogs, products);
    }

    @Test
    void aLinkedLineTakesTheStoresNameForTheTaxonomyCategoryOfItsEan() {
        // given
        link("K", "5901234567890");
        inTaxonomy("5901234567890", "Processors", "pim-cpu");
        when(storeCategories.categoryNamesByPimCategoryId(STORE)).thenReturn(Map.of("pim-cpu", "Procesory"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::category).contains("Procesory");
        verifyNoInteractions(pimCatalog, catalogs, products);
    }

    @Test
    void aLinkedLineTakesTheTaxonomyCategoryWhenTheStoreHasNoCategoryForIt() {
        // given
        link("K", "5901234567890");
        inTaxonomy("5901234567890", "Processors", "pim-cpu");
        when(storeCategories.categoryNamesByPimCategoryId(STORE)).thenReturn(Map.of());

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::category).contains("Processors");
    }

    @Test
    void aLinkedLineSkipsAnEanTheTaxonomyKnowsWithoutACategoryYet() {
        // given the first EAN still waits for its category
        link("K", "5900000000099", "5901234567890");
        inTaxonomy("5900000000099", null, null);
        inTaxonomy("5901234567890", "Lamps", null);

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::category).contains("Lamps");
        assertThat(result).map(ResolvedOffer::ean).contains("5900000000099");
    }

    @Test
    void aLinkedLineWhoseEansTheTaxonomyDoesNotKnowKeepsNoCategory() {
        // given
        link("K", "5901234567890");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then the line is still resolved by its EAN
        assertThat(result).map(ResolvedOffer::sku).contains("5901234567890");
        assertThat(result.get().category()).isNull();
        verifyNoInteractions(storeCategories);
    }

    @Test
    void readsTheStoresCategoriesOnceForAllLinesOfOneScope() {
        // given
        link("K1", "5901234567890");
        link("K2", "5901234567891");
        inTaxonomy("5901234567890", "Processors", "pim-cpu");
        inTaxonomy("5901234567891", "Lamps", "pim-lamps");
        when(storeCategories.categoryNamesByPimCategoryId(STORE)).thenReturn(Map.of("pim-cpu", "Procesory"));
        MarketplaceOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        Optional<ResolvedOffer> first = offers.resolve(EMPIK, "K1", null);
        Optional<ResolvedOffer> second = offers.resolve(EMPIK, "K2", null);

        // then
        assertThat(first).map(ResolvedOffer::category).contains("Procesory");
        assertThat(second).map(ResolvedOffer::category).contains("Lamps");
        verify(storeCategories, times(1)).categoryNamesByPimCategoryId(STORE);
    }

    @Test
    void aLinkWithSeveralEansGivesTheFirstAsTheEanAndAllAsAlternativeCodes() {
        // given
        link("K", "5900000000099", "5901234567890");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::ean).contains("5900000000099");
        assertThat(result).map(ResolvedOffer::sku).contains("5900000000099:5901234567890");
    }

    @Test
    void anEan13AndItsUpcFormAreOneCode() {
        // given the marketplace lists the same code with and without the leading zero
        link("K", "0730143315555", "730143315555");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", null);

        // then
        assertThat(result).map(ResolvedOffer::sku).contains("730143315555");
    }

    @Test
    void aLinkWithoutEansFallsThroughToTheManufacturerCodeStep() {
        // given
        link("K");
        mpnIn("1696938202", globalEntry("pim-2", "5900000000022", "1696938202", "cat-b"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K", "1696938202");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-2", "5900000000022", "1696938202", "cat-b", Source.MFN));
    }

    @Test
    void ownOfferPrefersCatalogProductCodesOverPimPrimaryCodes() {
        // given
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
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-1", null);

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5901234567890", "7041", "cat-a", Source.OWN_OFFER));
    }

    @Test
    void ownOfferUsesPimPrimaryCodesWhenTheProductIsNotInTheStoreCatalog() {
        // given
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", "cat-a"));
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        catalogCategory("c1", "Krzesła");
        Product foreign = new Product("other-store-category");
        foreign.setProductId("p2");
        when(products.findAllByPimId("pim-1")).thenReturn(List.of(foreign));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-1", null);

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5900000000011", "M1", "cat-a", Source.OWN_OFFER));
    }

    @Test
    void ownOfferFindsTheStoreProductStillPinnedToTheClonesSource() {
        // given
        storeSees("src", cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a"));
        when(aliases.aliasesOf(STORE, "clone")).thenReturn(Set.of("clone", "src"));
        catalogCategory("c1", "Krzesła");
        when(products.findAllByPimId("clone")).thenReturn(List.of());
        catalogProduct("c1", "p1", "src", "5901234567890", "7041");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "src", null);

        // then
        assertThat(result).contains(new ResolvedOffer("clone", "5901234567890", "7041", "cat-a", Source.OWN_OFFER));
    }

    @Test
    void ownOfferIgnoresAProductPinnedToAnIdThatIsNotAnAliasOfTheClone() {
        // given
        storeSees("src", cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a"));
        when(aliases.aliasesOf(STORE, "clone")).thenReturn(Set.of("clone"));
        catalogCategory("c1", "Krzesła");
        when(products.findAllByPimId("clone")).thenReturn(List.of());

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "src", null);

        // then
        assertThat(result).contains(new ResolvedOffer("clone", "5900000000011", "M1", "cat-a", Source.OWN_OFFER));
        verify(products, never()).findAllByPimId("src");
    }

    @Test
    void ownOfferUsesTheCatalogCategoryNameWhenThePimEntryHasNoCategory() {
        // given
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", null));
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        catalogCategory("c1", "Krzesła");
        catalogProduct("c1", "p1", "pim-1", "5901234567890", "7041");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-1", null);

        // then
        assertThat(result).map(ResolvedOffer::category).contains("Krzesła");
    }

    @Test
    void ownOfferTakesCatalogCodesOnlyFromProductsInTheStoresOwnCategories() {
        // given
        storeSees("pim-1", globalEntry("pim-1", "5900000000011", "M1", "cat-a"));
        storeSees("pim-2", globalEntry("pim-2", "5900000000022", "M2", "cat-b"));
        when(aliases.aliasesOf(STORE, "pim-1")).thenReturn(Set.of("pim-1"));
        when(aliases.aliasesOf(STORE, "pim-2")).thenReturn(Set.of("pim-2"));
        catalogCategory("c1", "Krzesła");
        catalogProduct("c1", "p1", "pim-1", "5901234567890", "7041");
        Product foreign = new Product("other-store-category");
        foreign.setProductId("p2");
        foreign.setEan("5909999999999");
        foreign.setManufacturerCode("9999");
        when(products.findAllByPimId("pim-2")).thenReturn(List.of(foreign));

        // when
        Optional<ResolvedOffer> own = resolver.resolve(STORE, EMPIK, "pim-1", null);
        Optional<ResolvedOffer> other = resolver.resolve(STORE, EMPIK, "pim-2", null);

        // then
        assertThat(own).map(ResolvedOffer::mfn).contains("7041");
        assertThat(other).contains(new ResolvedOffer("pim-2", "5900000000022", "M2", "cat-b", Source.OWN_OFFER));
        verify(products, never()).findByProductId("other-store-category", "p2");
    }
}
