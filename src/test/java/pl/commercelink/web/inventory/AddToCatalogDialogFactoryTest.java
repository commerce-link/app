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
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.taxonomy.Taxonomy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddToCatalogDialogFactoryTest {

    private static final String STORE_ID = "store-1";

    @Mock private Inventory inventory;
    @Mock private InventoryView view;
    @Mock private CatalogPlacement catalogPlacement;
    private AddToCatalogDialogFactory factory;

    @BeforeEach
    void setUp() {
        PimCatalog pimCatalog = mock(PimCatalog.class);
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
        CatalogPlacement.Existing existing = new CatalogPlacement.Existing("c-2", "cat-b2b", "p-1", InventoryKey.fromEan("5901000000001"));
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(gpu, b2b, cases), List.of(existing)));
    }

    @Test
    void matchingCategoriesComeFirstWithHowManyAreAlreadyThere() {
        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000001", "5901000000002", "5901000000001"),
                "/dashboard/inventory?view=browse&cat=11");

        // then
        assertThat(dialog.count()).isEqualTo(2);
        assertThat(dialog.pimCategoryName()).isEqualTo("Karty graficzne");
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::label)
                .containsExactly("Podzespoły › Karta graficzna", "Sklep B2B › Karty");
        assertThat(dialog.matching()).extracting(AddToCatalogDialog.Option::alreadyIn).containsExactly(0, 1);
        assertThat(dialog.others()).extracting(AddToCatalogDialog.Group::catalogName).containsExactly("Podzespoły");
        assertThat(dialog.returnTo()).isEqualTo("/dashboard/inventory?view=browse&cat=11");
    }

    @Test
    void productOutsideEveryMappedCategoryGetsAllManualCategories() {
        // when
        AddToCatalogDialog dialog = factory.build(STORE_ID, List.of("5901000000003"), "//evil.com");

        // then
        assertThat(dialog.productName()).isEqualTo("Freezer 360");
        assertThat(dialog.anyMatching()).isFalse();
        assertThat(dialog.others()).extracting(AddToCatalogDialog.Group::catalogName).containsExactly("Podzespoły", "Sklep B2B");
        assertThat(dialog.returnTo()).isEqualTo("/dashboard/inventory?view=browse");
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

    private void product(String ean, String name, String categoryId) {
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(InventoryKey.fromEan(ean));
        when(matched.getTaxonomy()).thenReturn(new Taxonomy(ean, "M-" + ean, "Brand", name, "x", 1, null, null, null, categoryId));
        when(view.findByEan(ean)).thenReturn(matched);
    }
}
