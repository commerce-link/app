package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCategoryViewFactoryTest {

    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final CatalogPlacement catalogPlacement = mock(CatalogPlacement.class);
    private final ProductCategoryViewFactory factory;

    private static final CatalogPlacement.Target GPU = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-gpu", "Karta graficzna", List.of("11"));
    private static final CatalogPlacement.Target B2B = new CatalogPlacement.Target("c-2", "Sklep B2B", "cat-b2b", "Karty", List.of("11"));

    ProductCategoryViewFactoryTest() {
        PimCatalog pimCatalog = mock(PimCatalog.class);
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty komputerowe", "pl"), new PimCategory("11", "10", "Karty graficzne", "pl")));
        factory = new ProductCategoryViewFactory(taxonomyCache, new PimCategoryTree(pimCatalog), catalogPlacement);
        when(catalogPlacement.forStore("store-1")).thenReturn(new CatalogPlacement.StorePlacement(List.of(
                new CatalogPlacement.Target("c-1", "Podzespoły", "cat-gpu", "Karta graficzna", List.of("11"))), List.of()));
    }

    @Test
    void foundProductGetsItsPimPathCatalogLineAndTheAddAction() {
        // given
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy("5901000000001", "GPU-1", "Gigabyte", "RTX 4060", "Karty", 1,
                null, null, null, "11"));

        // when
        Optional<ProductCategoryView> view = factory.build("store-1",
                new ProductHeader("RTX 4060", "Gigabyte", "5901000000001", "GPU-1"), true);

        // then
        assertThat(view).isPresent();
        assertThat(view.get().line().pimAncestors()).containsExactly("Komponenty komputerowe");
        assertThat(view.get().line().catalogLabel()).isEqualTo("Podzespoły › Karta graficzna");
        assertThat(view.get().canAdd()).isTrue();
        assertThat(view.get().addHref()).isEqualTo("/dashboard/inventory?open=add&ean=5901000000001");
    }

    @Test
    void productWithoutTaxonomyHasNoCategoryBlock() {
        // given
        when(taxonomyCache.find(any())).thenReturn(Taxonomy.EMPTY);

        // when / then
        assertThat(factory.build("store-1", new ProductHeader("X", "Y", "5901000000009", "X-9"), true)).isEmpty();
    }

    @Test
    void nonAdminSeesTheCategoryButNoActionAndNoCatalogLine() {
        // given
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy("5901000000001", "GPU-1", "Gigabyte", "RTX 4060", "Karty", 1,
                null, null, null, "11"));

        // when
        ProductCategoryView view = factory.build("store-1", new ProductHeader("RTX 4060", "Gigabyte", "5901000000001", "GPU-1"), false)
                .orElseThrow();

        // then
        assertThat(view.canAdd()).isFalse();
        assertThat(view.line().catalogLabel()).isNull();
    }

    @Test
    void productInOneOfTwoMatchingCategoriesCanBeAddedToTheOther() {
        // given
        givenTaxonomy();
        when(catalogPlacement.forStore("store-1")).thenReturn(new CatalogPlacement.StorePlacement(List.of(GPU, B2B),
                List.of(new CatalogPlacement.Existing("c-1", "cat-gpu", "p-1", InventoryKey.fromEan("5901000000001")))));

        // when
        ProductCategoryView view = factory.build("store-1", new ProductHeader("RTX 4060", "Gigabyte", "5901000000001", "GPU-1"), true)
                .orElseThrow();

        // then
        assertThat(view.line().inCatalog()).isTrue();
        assertThat(view.canAdd()).isTrue();
    }

    @Test
    void productInEveryMatchingCategoryCannotBeAddedAgain() {
        // given
        givenTaxonomy();
        when(catalogPlacement.forStore("store-1")).thenReturn(new CatalogPlacement.StorePlacement(List.of(GPU, B2B),
                List.of(new CatalogPlacement.Existing("c-1", "cat-gpu", "p-1", InventoryKey.fromEan("5901000000001")),
                        new CatalogPlacement.Existing("c-2", "cat-b2b", "p-2", InventoryKey.fromEan("5901000000001")))));

        // when
        ProductCategoryView view = factory.build("store-1", new ProductHeader("RTX 4060", "Gigabyte", "5901000000001", "GPU-1"), true)
                .orElseThrow();

        // then
        assertThat(view.line().inCatalog()).isTrue();
        assertThat(view.canAdd()).isFalse();
    }

    private void givenTaxonomy() {
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy("5901000000001", "GPU-1", "Gigabyte", "RTX 4060", "Karty", 1,
                null, null, null, "11"));
    }
}
