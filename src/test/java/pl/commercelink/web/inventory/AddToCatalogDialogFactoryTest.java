package pl.commercelink.web.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddToCatalogDialogFactoryTest {

    private static final String STORE_ID = "store-1";

    @Mock private Inventory inventory;
    @Mock private InventoryView view;
    @Mock private CatalogPlacement catalogPlacement;
    @Mock private PimCatalog pimCatalog;
    @Mock private TaxonomyCache taxonomyCache;
    /** Mocked, not built: a real group adds every offer's codes to its key, and this case needs the global key alone. */
    @Mock private MatchedInventory merged;
    private AddToCatalogDialogFactory factory;

    @BeforeEach
    void setUp() {
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("11", null, "Karty graficzne", "pl"), new PimCategory("31", null, "Chłodzenie", "pl")));
        factory = new AddToCatalogDialogFactory(inventory, catalogPlacement, new PimCategoryTree(pimCatalog));
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
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000001", "5901000000002", "5901000000001"),
                "/dashboard/inventory?cat=11");

        // then
        assertThat(dialog.count()).isEqualTo(2);
        assertThat(dialog.pimCategoryName()).isEqualTo("Karty graficzne");
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::label)
                .containsExactly("Podzespoły › Karta graficzna", "Sklep B2B › Karty");
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::alreadyIn).containsExactly(0, 1);
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::preselected).containsExactly(true, false);
        assertThat(dialog.others()).extracting(AddToCatalogDialog.Group::catalogName).containsExactly("Podzespoły");
        assertThat(dialog.returnTo()).isEqualTo("/dashboard/inventory?cat=11");
    }

    @Test
    void productInTheFirstMatchingCategoryOnlyHasTheNextOnePreselected() {
        // given
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000001")));

        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000001"), null);

        // then
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::preselected).containsExactly(false, true);
    }

    @Test
    void productInEveryMatchingCategoryHasTheFirstOnePreselected() {
        // given
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000001")),
                new CatalogPlacement.Existing("c-2", "cat-b2b", InventoryKey.fromEan("5901000000001")));

        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000001"), null);

        // then
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::preselected).containsExactly(true, false);
        assertThat(dialog.others()).flatExtracting(AddToCatalogDialog.Group::options)
                .extracting(AddToCatalogDialog.Option::preselected).containsOnly(false);
    }

    @Test
    void bulkPreselectsTheFirstCategoryThatDoesNotHoldEveryChosenProduct() {
        // given
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000001")),
                new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000002")),
                new CatalogPlacement.Existing("c-2", "cat-b2b", InventoryKey.fromEan("5901000000001")));

        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000001", "5901000000002"), null);

        // then
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::alreadyIn).containsExactly(2, 1);
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::preselected).containsExactly(false, true);
    }

    /**
     * A store's own offer joined a global product by one shared code and is the cheapest, so the row adds the product by
     * that offer's EAN. The catalog holds it under that EAN only; the dialog must count it there, as the row's pill does.
     */
    @Test
    void productInTheCatalogUnderTheRowsEanOnlyCountsAsThereAndTheOtherCategoryIsPreselected() {
        // given
        mergedProduct("5901000000077", "OWN-77");
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromEan("5901000000077")));

        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000077"), null);

        // then
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::alreadyIn).containsExactly(1, 0);
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::preselected).containsExactly(false, true);
    }

    @Test
    void productInTheCatalogUnderTheMfnOfTheOfferCarryingTheEanCountsAsThere() {
        // given
        mergedProduct("5901000000077", "OWN-77");
        placement(new CatalogPlacement.Existing("c-1", "cat-gpu", InventoryKey.fromMfn("OWN-77")));

        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000077"), null);

        // then
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::alreadyIn).containsExactly(1, 0);
    }

    @Test
    void productOutsideEveryMappedCategoryGetsAllManualCategories() {
        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000003"), "//evil.com");

        // then
        assertThat(dialog.productName()).isEqualTo("Freezer 360");
        assertThat(dialog.anyMatching()).isFalse();
        assertThat(dialog.others()).extracting(AddToCatalogDialog.Group::catalogName).containsExactly("Podzespoły", "Sklep B2B");
        assertThat(dialog.returnTo()).isEqualTo("/dashboard/inventory");
    }

    @Test
    void storeWithoutManualCategoriesIsToldSo() {
        // given
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(), List.of()));

        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000001"), null);

        // then
        assertThat(dialog.noManualCategories()).isTrue();
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
        when(merged.getInventoryItems()).thenReturn(List.of(
                new InventoryItem("5909999999998", "GLOBAL-98", 120.0, "PLN", 5, 1, "AB"),
                new InventoryItem(ownEan, ownMfn, 100.0, "PLN", 3, 1, "Own")));
        when(merged.getTaxonomy()).thenReturn(new Taxonomy(ownEan, ownMfn, "Brand", "Merged", "x", 1, null, null, null, "11"));
        when(view.findByEan(ownEan)).thenReturn(merged);
    }

    private void product(String ean, String name, String categoryId) {
        InventoryKey key = InventoryKey.fromEan(ean);
        MatchedInventory matched = new MatchedInventory(key,
                new InventoryItem(ean, "M-" + ean, 100.0, "PLN", 1, 1, "AB"), taxonomyCache, null);
        when(taxonomyCache.find(same(key)))
                .thenReturn(new Taxonomy(ean, "M-" + ean, "Brand", name, "x", 1, null, null, null, categoryId));
        when(view.findByEan(ean)).thenReturn(matched);
    }
}
