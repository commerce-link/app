package pl.commercelink.externaloffers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.externaloffers.ResolvedOffer.Source;
import pl.commercelink.pim.api.PimEntry;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static pl.commercelink.pim.ScopedPimCatalog.cloneEntry;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;

@ExtendWith(MockitoExtension.class)
class ExternalOfferResolverTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";

    @Mock
    private ExternalOfferLinkRepository links;
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
    private ExternalOfferResolver resolver;

    private void inTaxonomy(String ean, String category, String categoryId) {
        when(taxonomy.findByEan(ean)).thenReturn(new Taxonomy(ean, "MFN-" + ean, "Brand", "Name " + ean, category, 1,
                null, null, "raw", categoryId));
    }

    private void link(String key, String... eans) {
        when(links.find(STORE, EMPIK, key)).thenReturn(Optional.of(
                ExternalOfferLink.of(STORE, EMPIK, key, List.of(eans), ExternalOfferLinkSource.MANUAL, "u", Instant.now())));
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
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "k7m2xq9pz4");

        // then
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
        assertThat(result).map(ResolvedOffer::pimId).contains("k7m2xq9pz4");
    }

    @Test
    void anOfferCommerceLinkPublishedIsNeverTakenOverByALink() {
        // given a link saved for the key of our own offer
        storeSees("k7m2xq9pz4", globalEntry("k7m2xq9pz4", "5900000000011", "M1", "cat-a"));
        lenient().when(links.find(STORE, EMPIK, "k7m2xq9pz4")).thenReturn(Optional.of(ExternalOfferLink.of(STORE, EMPIK,
                "k7m2xq9pz4", List.of("2001000000043"), ExternalOfferLinkSource.MANUAL, "u", Instant.now())));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "k7m2xq9pz4");

        // then the offer is recognised as before offer links existed
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
        assertThat(result).map(ResolvedOffer::ean).contains("5900000000011");
        verify(links, never()).find(any(), any(), any());
    }

    @Test
    void anOfferCommerceLinkPublishedWithoutCodesIsUnresolvedNotTakenOverByALink() {
        // given
        storeSees("pim-9", globalEntry("pim-9", null, null, "cat-a"));
        lenient().when(links.find(STORE, EMPIK, "pim-9")).thenReturn(Optional.of(ExternalOfferLink.of(STORE, EMPIK,
                "pim-9", List.of("2001000000043"), ExternalOfferLinkSource.MANUAL, "u", Instant.now())));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-9");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void tellsTheKeyOfAnOfferCommerceLinkPublished() {
        // given
        storeSees("k7m2xq9pz4", globalEntry("k7m2xq9pz4", "5900000000011", "M1", "cat-a"));

        // when / then
        assertThat(resolver.isOwnOffer(STORE, "k7m2xq9pz4")).isTrue();
        assertThat(resolver.isOwnOffer(STORE, "PS-1010")).isFalse();
        assertThat(resolver.isOwnOffer(STORE, " ")).isFalse();
    }

    @Test
    void resolvesOwnOfferPublishedUnderCloneSourceAsTheClone() {
        // given
        storeSees("src", cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "src");

        // then
        assertThat(result).map(ResolvedOffer::pimId).contains("clone");
        assertThat(result).map(ResolvedOffer::source).contains(Source.OWN_OFFER);
    }

    @Test
    void aLineWithoutAnOfferKeyIsUnresolvedWithoutReadingAnything() {
        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, null);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(catalogs, products, links, aliases, taxonomy);
    }

    @Test
    void anOwnOfferHitWithoutAManufacturerCodeIsResolvedByItsEan() {
        // given
        storeSees("pim-3", globalEntry("pim-3", "05900000000033", null, "cat-c"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-3");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-3", "5900000000033", null, "cat-c", Source.OWN_OFFER));
        assertThat(result).map(ResolvedOffer::sku).contains("5900000000033");
    }

    @Test
    void anOwnOfferHitWithNeitherAnEanNorAManufacturerCodeIsUnresolved() {
        // given
        storeSees("pim-3", globalEntry("pim-3", null, null, "cat-c"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-3");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "unknown");

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
        ExternalOfferResolver.StoreOffers offers = resolver.forStore(STORE);

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
    void readsOneCatalogProductByItsKeyOnlyInTheStoresOwnCategories() {
        // given
        storeCatalogs(catalog("a", category("c1", "Krzesła")));
        Product chair = product("c1", "p1", null, "2001000000012", "M1");
        when(products.findByProductId("c1", "p1")).thenReturn(chair);
        ExternalOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        Optional<Product> own = offers.catalogProduct("c1", "p1");
        Optional<Product> foreign = offers.catalogProduct("other-store-category", "p1");

        // then the product is read by its key, not with every product of the catalogs
        assertThat(own).contains(chair);
        assertThat(foreign).isEmpty();
        verify(products, never()).findAll(anyString());
        verify(products, never()).findByProductId(eq("other-store-category"), anyString());
    }

    @Test
    void resolvesALinkToItsEanWithoutReadingTheCatalogsOrThePimData() {
        // given
        link("K", "5901234567890");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then the goods need not be in a catalog: fulfilment finds them by the EAN
        assertThat(result).contains(new ResolvedOffer(null, "5901234567890", null, null, Source.LINK));
        assertThat(result).map(ResolvedOffer::sku).contains("5901234567890");
        // the only question is whether the key is one of CommerceLink's own offers, which links leave alone
        verify(aliases).effectiveEntry(STORE, "K");
        verifyNoMoreInteractions(aliases);
        verifyNoInteractions(catalogs, products);
    }

    @Test
    void aLinkedLineTakesTheStoresNameForTheTaxonomyCategoryOfItsEan() {
        // given
        link("K", "5901234567890");
        inTaxonomy("5901234567890", "Processors", "pim-cpu");
        when(storeCategories.categoryNamesByPimCategoryId(STORE)).thenReturn(Map.of("pim-cpu", "Procesory"));

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then
        assertThat(result).map(ResolvedOffer::category).contains("Procesory");
        verifyNoInteractions(catalogs, products);
    }

    @Test
    void aLinkedLineTakesTheTaxonomyCategoryWhenTheStoreHasNoCategoryForIt() {
        // given
        link("K", "5901234567890");
        inTaxonomy("5901234567890", "Processors", "pim-cpu");
        when(storeCategories.categoryNamesByPimCategoryId(STORE)).thenReturn(Map.of());

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

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
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then
        assertThat(result).map(ResolvedOffer::category).contains("Lamps");
        assertThat(result).map(ResolvedOffer::ean).contains("5900000000099");
    }

    @Test
    void aLinkedLineWhoseEansTheTaxonomyDoesNotKnowKeepsNoCategory() {
        // given
        link("K", "5901234567890");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

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
        ExternalOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        Optional<ResolvedOffer> first = offers.resolve(EMPIK, "K1");
        Optional<ResolvedOffer> second = offers.resolve(EMPIK, "K2");

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
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then
        assertThat(result).map(ResolvedOffer::ean).contains("5900000000099");
        assertThat(result).map(ResolvedOffer::sku).contains("5900000000099:5901234567890");
    }

    @Test
    void anEan13AndItsUpcFormAreOneCode() {
        // given the marketplace lists the same code with and without the leading zero
        link("K", "0730143315555", "730143315555");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then
        assertThat(result).map(ResolvedOffer::sku).contains("730143315555");
    }

    @Test
    void aLinkWithoutEansIsNoLinkAndLeavesTheLineUnresolved() {
        // given
        link("K");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void aLinkKeepsOnlyItsValidEans() {
        // given a link saved before the format was checked
        link("K", "N/A", "2000000001012", "123:456");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then
        assertThat(result).map(ResolvedOffer::sku).contains("2000000001012");
        verify(taxonomy, never()).findByEan("N/A");
    }

    @Test
    void aLinkWithoutAValidEanIsNoLinkAndLeavesTheLineUnresolved() {
        // given
        link("K", "N/A", "20070000001A");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "K");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void looksEachEanUpInTheTaxonomyOncePerScope() {
        // given the same goods on several lines of one import, one EAN the taxonomy knows and one it does not
        when(taxonomy.findByEan("2000000001012")).thenReturn(null);
        inTaxonomy("2000000001029", "Lamps", null);
        ExternalOfferResolver.StoreOffers offers = resolver.forStore(STORE);

        // when
        ResolvedOffer first = offers.linked(List.of("2000000001012", "2000000001029"));
        ResolvedOffer second = offers.linked(List.of("2000000001012", "2000000001029"));

        // then the taxonomy is a scan of every entry, so a repeated EAN is not looked up again
        assertThat(first.category()).isEqualTo("Lamps");
        assertThat(second.category()).isEqualTo("Lamps");
        verify(taxonomy, times(1)).findByEan("2000000001012");
        verify(taxonomy, times(1)).findByEan("2000000001029");
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
        full.setManufacturerCode("DMW-2001");
        when(products.findAllByPimId("pim-1")).thenReturn(List.of(elsewhere, projection));
        when(products.findByProductId("c1", "p1")).thenReturn(full);

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-1");

        // then
        assertThat(result).contains(new ResolvedOffer("pim-1", "5901234567890", "DMW-2001", "cat-a", Source.OWN_OFFER));
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
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-1");

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
        catalogProduct("c1", "p1", "src", "5901234567890", "DMW-2001");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "src");

        // then
        assertThat(result).contains(new ResolvedOffer("clone", "5901234567890", "DMW-2001", "cat-a", Source.OWN_OFFER));
    }

    @Test
    void ownOfferIgnoresAProductPinnedToAnIdThatIsNotAnAliasOfTheClone() {
        // given
        storeSees("src", cloneEntry("clone", STORE, "src", "5900000000011", "M1", "cat-a"));
        when(aliases.aliasesOf(STORE, "clone")).thenReturn(Set.of("clone"));
        catalogCategory("c1", "Krzesła");
        when(products.findAllByPimId("clone")).thenReturn(List.of());

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "src");

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
        catalogProduct("c1", "p1", "pim-1", "5901234567890", "DMW-2001");

        // when
        Optional<ResolvedOffer> result = resolver.resolve(STORE, EMPIK, "pim-1");

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
        catalogProduct("c1", "p1", "pim-1", "5901234567890", "DMW-2001");
        Product foreign = new Product("other-store-category");
        foreign.setProductId("p2");
        foreign.setEan("5909999999999");
        foreign.setManufacturerCode("9999");
        when(products.findAllByPimId("pim-2")).thenReturn(List.of(foreign));

        // when
        Optional<ResolvedOffer> own = resolver.resolve(STORE, EMPIK, "pim-1");
        Optional<ResolvedOffer> other = resolver.resolve(STORE, EMPIK, "pim-2");

        // then
        assertThat(own).map(ResolvedOffer::mfn).contains("DMW-2001");
        assertThat(other).contains(new ResolvedOffer("pim-2", "5900000000022", "M2", "cat-b", Source.OWN_OFFER));
        verify(products, never()).findByProductId("other-store-category", "p2");
    }
}
