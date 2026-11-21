package pl.commercelink.web.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.MatchedInventoryFixture;
import pl.commercelink.products.ProductDataFixture;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.inventory.InventoryViewFixture;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.products.ProductData;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogTargetOptionsFactoryTest {

    private static final String STORE_ID = "store-1";

    @Mock private Inventory inventory;
    @Mock private InventoryView view;
    @Mock private CatalogPlacement catalogPlacement;
    @Mock private PimCatalog pimCatalog;
    @Mock private TaxonomyCache taxonomyCache;
    /** Mocked, not built: a real group adds every offer's codes to its key, and this case needs the global key alone. */
    @Mock private MatchedInventory merged;
    private CatalogTargetOptionsFactory factory;

    @BeforeEach
    void setUp() {
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("11", null, "Karty graficzne", "pl"), new PimCategory("31", null, "Chłodzenie", "pl")));
        factory = new CatalogTargetOptionsFactory(inventory, catalogPlacement, new PimCategoryTree(pimCatalog));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(view);
        when(view.findByEan(anyString())).thenReturn(MatchedInventory.empty(new InventoryKey()));
        product("5901000000001", "RTX 4060", "11");
        product("5901000000002", "RX 7600", "11");
        product("5901000000003", "Freezer 360", "31");
        CatalogPlacement.Target gpu = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-gpu", "Karta graficzna", List.of("11"));
        CatalogPlacement.Target b2b = new CatalogPlacement.Target("c-2", "Sklep B2B", "cat-b2b", "Karty", List.of("11"));
        CatalogPlacement.Target cases = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-case", "Obudowa", List.of("40"));
        CatalogPlacement.Existing existing = new CatalogPlacement.Existing("c-2", "cat-b2b", InventoryKey.fromEan("5901000000001"));
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(gpu, b2b, cases), List.of(existing)));
    }

    @Test
    void matchingCategoriesComeFirstWithHowManyAreAlreadyThere() {
        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000001", "5901000000002", "5901000000001"));

        // then
        assertThat(options.count()).isEqualTo(2);
        assertThat(options.pimCategoryName()).isEqualTo("Karty graficzne");
        assertThat(options.matching()).extracting(CatalogTargetOptions.Option::value)
                .containsExactly("c-1/cat-gpu", "c-2/cat-b2b");
        assertThat(options.matching()).extracting(CatalogTargetOptions.Option::catalogName, CatalogTargetOptions.Option::categoryName)
                .containsExactly(tuple("Podzespoły", "Karta graficzna"), tuple("Sklep B2B", "Karty"));
        assertThat(options.matching()).extracting(CatalogTargetOptions.Option::alreadyIn).containsExactly(0, 1);
        assertThat(options.preselectedValue()).isEqualTo("c-1/cat-gpu");
        assertThat(options.others()).extracting(CatalogTargetOptions.Option::value).containsExactly("c-1/cat-case");
    }

    @Test
    void productIsMatchedToCategoriesByTheResolvedProductData() {
        // given
        String ean = "5901000000055";
        MatchedInventory row = MatchedInventoryFixture.describedAs(
                new ProductData("PIM name", "PIM brand", "Karty graficzne", "11", ean, "PIM-MFN", null, null),
                new MatchedInventory(InventoryKey.fromEan(ean), new InventoryItem(ean, "PIM-MFN", 100.0, "PLN", 1, 1, "AB"), null));
        when(view.findByEan(ean)).thenReturn(row);

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of(ean));

        // then
        assertThat(options.matching()).extracting(CatalogTargetOptions.Option::value)
                .containsExactly("c-1/cat-gpu", "c-2/cat-b2b");
        assertThat(options.pimCategoryName()).isEqualTo("Karty graficzne");
    }

    @Test
    void productsFoundInTheViewAreDescribedInOneReadHoweverManyThereAre() {
        // given
        TaxonomyCache records = new TaxonomyCache(mock(TaxonomyRepository.class));
        List<String> eans = List.of("5901000000061", "5901000000062", "5901000000063");
        List<MatchedInventory> groups = eans.stream().map(ean -> {
            records.add(new Taxonomy(ean, "M-" + ean, "Brand", "GPU " + ean, "x", 1, null, null, null, "11"));
            return new MatchedInventory(new InventoryKey(ean, "M-" + ean),
                    List.of(new InventoryItem(ean, "M-" + ean, 100.0, "PLN", 1, 1, "AB")), mock(SupplierRegistry.class));
        }).toList();
        ProductDataResolver resolver = spy(new ProductDataResolver(ScopedPimCatalog.of(), records));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(InventoryViewFixture.over(STORE_ID, resolver,
                mock(SupplierRegistry.class), groups.toArray(MatchedInventory[]::new)));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, eans);

        // then
        assertThat(options.count()).isEqualTo(3);
        assertThat(options.pimCategoryName()).isEqualTo("Karty graficzne");
        verify(resolver, times(1)).ofGroups(eq(STORE_ID), any());
    }

    @Test
    void productInTheFirstMatchingCategoryOnlyHasTheNextOnePreselected() {
        // given
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000001")));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000001"));

        // then
        assertThat(options.preselectedValue()).isEqualTo("c-2/cat-b2b");
    }

    @Test
    void productInEveryMatchingCategoryHasTheFirstOnePreselected() {
        // given
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000001")),
                new CatalogPlacement.Existing("c-2", "cat-b2b", InventoryKey.fromEan("5901000000001")));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000001"));

        // then
        assertThat(options.preselectedValue()).isEqualTo("c-1/cat-gpu");
    }

    @Test
    void bulkPreselectsTheFirstCategoryThatDoesNotHoldEveryChosenProduct() {
        // given
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000001")),
                new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000002")),
                new CatalogPlacement.Existing("c-2", "cat-b2b", InventoryKey.fromEan("5901000000001")));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000001", "5901000000002"));

        // then
        assertThat(options.matching()).extracting(CatalogTargetOptions.Option::alreadyIn).containsExactly(2, 1);
        assertThat(options.preselectedValue()).isEqualTo("c-2/cat-b2b");
    }

    /**
     * A store's own offer joined a global product by one shared code and is the cheapest, so the row adds the product by
     * that offer's EAN. The catalog holds it under that EAN only; the options must count it there, as the row's pill does.
     */
    @Test
    void productInTheCatalogUnderTheRowsEanOnlyCountsAsThereAndTheOtherCategoryIsPreselected() {
        // given
        mergedProduct("5901000000077", "OWN-77");
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000077")));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000077"));

        // then
        assertThat(options.matching()).extracting(CatalogTargetOptions.Option::alreadyIn).containsExactly(1, 0);
        assertThat(options.preselectedValue()).isEqualTo("c-2/cat-b2b");
    }

    @Test
    void productInTheCatalogUnderTheMfnOfTheOfferOnlyDoesNotCount() {
        // given
        mergedProduct("5901000000077", "OWN-77");
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", new InventoryKey("5901000000078", "OWN-77")));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000077"));

        // then
        assertThat(options.matching()).extracting(CatalogTargetOptions.Option::alreadyIn).containsExactly(0, 0);
    }

    @Test
    void productOutsideEveryMappedCategoryGetsAllManualCategories() {
        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000003"));

        // then
        assertThat(options.unmatched()).isTrue();
        assertThat(options.preselectedValue()).isNull();
        assertThat(options.pimCategoryName()).isEqualTo("Chłodzenie");
        assertThat(options.others()).extracting(CatalogTargetOptions.Option::value)
                .containsExactly("c-1/cat-gpu", "c-1/cat-case", "c-2/cat-b2b");
    }

    /** One flat list: the categories that do not match by catalog name, then category name, in Polish order. */
    @Test
    void otherCategoriesAreSortedByCatalogAndThenCategoryInPolishOrder() {
        // given
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(
                new CatalogPlacement.Target("c-3", "Zestawy", "z1", "Akcesoria", List.of()),
                new CatalogPlacement.Target("c-2", "Łączność", "l1", "Routery", List.of()),
                new CatalogPlacement.Target("c-1", "Lampy", "a2", "Żarówki", List.of()),
                new CatalogPlacement.Target("c-1", "Lampy", "a1", "Świetlówki", List.of()),
                new CatalogPlacement.Target("c-1", "Lampy", "a3", "Oprawy", List.of())), List.of()));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000003"));

        // then
        assertThat(options.others()).extracting(CatalogTargetOptions.Option::categoryName)
                .containsExactly("Oprawy", "Świetlówki", "Żarówki", "Routery", "Akcesoria");
    }

    @Test
    void storeWithoutManualCategoriesIsToldSo() {
        // given
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(), List.of()));

        // when
        CatalogTargetOptions options = factory.build(STORE_ID, List.of("5901000000001"));

        // then
        assertThat(options.noManualCategories()).isTrue();
    }

    private void placement(CatalogPlacement.Existing... existing) {
        CatalogPlacement.Target gpu = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-gpu", "Karta graficzna", List.of("11"));
        CatalogPlacement.Target b2b = new CatalogPlacement.Target("c-2", "Sklep B2B", "cat-b2b", "Karty", List.of("11"));
        CatalogPlacement.Target cases = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-case", "Obudowa", List.of("40"));
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(gpu, b2b, cases), List.of(existing)));
    }

    /** Found by the own offer's EAN, keyed by the global product's codes that the own offer joined. */
    private void mergedProduct(String ownEan, String ownMfn) {
        InventoryKey group = InventoryKey.fromEan("5909999999998");
        group.addManufacturerCode("GLOBAL-98");
        when(merged.isEmpty()).thenReturn(false);
        when(merged.getInventoryKey()).thenReturn(group);
        when(merged.getProductData()).thenReturn(ProductDataFixture.from(new Taxonomy(ownEan, ownMfn, "Brand", "Merged", "x", 1, null, null, null, "11")));
        when(view.findByEan(ownEan)).thenReturn(merged);
    }

    private void product(String ean, String name, String categoryId) {
        InventoryKey key = InventoryKey.fromEan(ean);
        MatchedInventory matched = MatchedInventoryFixture.readingFrom(taxonomyCache, new MatchedInventory(key,
                new InventoryItem(ean, "M-" + ean, 100.0, "PLN", 1, 1, "AB"), null));
        when(taxonomyCache.find(same(key)))
                .thenReturn(new Taxonomy(ean, "M-" + ean, "Brand", name, "x", 1, null, null, null, categoryId));
        when(view.findByEan(ean)).thenReturn(matched);
    }
}
