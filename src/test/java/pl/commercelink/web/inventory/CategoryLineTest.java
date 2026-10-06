package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryLineTest {

    private static final InventoryKey KEY = InventoryKey.fromEan("5901000000001");
    private static final CatalogPlacement.Target FAN = new CatalogPlacement.Target("c-1", "Local Catalog", "cat-fan", "Fan", List.of("11"));
    private static final CatalogPlacement.Target ACCESSORIES =
            new CatalogPlacement.Target("c-1", "Local Catalog", "cat-acc", "Akcesoria", List.of("11"));
    private static final CatalogPlacement.Target OTHER = new CatalogPlacement.Target("c-2", "Sklep B2B", "cat-other", "Inne", List.of("99"));

    private final PimCategoryTree tree = tree();

    @Test
    void productOutsideTheCatalogShowsThePimPathAndNothingCatalogRelated() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(FAN, ACCESSORIES, OTHER), List.of());

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.pimAncestors()).containsExactly("Chłodzenie");
        assertThat(line.pimLeaf()).isEqualTo("Wentylatory");
        assertThat(line.pimFullPath()).isEqualTo("Chłodzenie › Wentylatory");
        assertThat(line.inCatalog()).isFalse();
        assertThat(line.inCatalogLabels()).isEmpty();
    }

    @Test
    void productInOneCategorySaysWhereAndLinksToIt() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(FAN, ACCESSORIES, OTHER),
                List.of(new CatalogPlacement.Existing("c-1", "cat-acc", "p-1", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalog()).isTrue();
        assertThat(line.inCatalogLabels()).containsExactly("Local Catalog › Akcesoria");
        assertThat(line.inCatalogHref()).isEqualTo("/dashboard/catalogs/c-1/category/cat-acc/products/p-1");
    }

    @Test
    void productInTwoCategoriesListsBothOnePerLineAndLinksToTheFirst() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(FAN, ACCESSORIES, OTHER),
                List.of(new CatalogPlacement.Existing("c-1", "cat-fan", "p-1", KEY),
                        new CatalogPlacement.Existing("c-1", "cat-acc", "p-2", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalogLabels()).containsExactly("Local Catalog › Fan", "Local Catalog › Akcesoria");
        assertThat(line.inCatalogPlaces()).isEqualTo("Local Catalog › Fan\nLocal Catalog › Akcesoria");
        assertThat(line.inCatalogHref()).isEqualTo("/dashboard/catalogs/c-1/category/cat-fan/products/p-1");
    }

    @Test
    void sameNamedCategoriesInTwoCatalogsCountAsTwoPlacesButOneCategoryCountsOnce() {
        // given
        CatalogPlacement.Target first = new CatalogPlacement.Target("c-1", "Sklep", "cat-a", "Fan", List.of("11"));
        CatalogPlacement.Target second = new CatalogPlacement.Target("c-2", "Sklep", "cat-b", "Fan", List.of("11"));
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(first, second),
                List.of(new CatalogPlacement.Existing("c-1", "cat-a", "p-1", KEY),
                        new CatalogPlacement.Existing("c-1", "cat-a", "p-2", KEY),
                        new CatalogPlacement.Existing("c-2", "cat-b", "p-3", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalogLabels()).containsExactly("Sklep › Fan", "Sklep › Fan");
    }

    @Test
    void entryInACategoryThatIsNoLongerATargetFallsBackToItsIds() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(FAN),
                List.of(new CatalogPlacement.Existing("c-9", "cat-gone", "p-1", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalogLabels()).containsExactly("c-9 › cat-gone");
    }

    @Test
    void productInACategoryItsPimCategoryDoesNotMatchStillSaysWhere() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(OTHER),
                List.of(new CatalogPlacement.Existing("c-2", "cat-other", "p-1", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalogLabels()).containsExactly("Sklep B2B › Inne");
    }

    @Test
    void withoutPlacementEverythingCatalogRelatedIsEmpty() {
        // when
        CategoryLine line = CategoryLine.of(tree, null, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalogLabels()).isEmpty();
        assertThat(line.inCatalog()).isFalse();
    }

    private static PimCategoryTree tree() {
        PimCatalog pimCatalog = mock(PimCatalog.class);
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Chłodzenie", "pl"), new PimCategory("11", "10", "Wentylatory", "pl")));
        return new PimCategoryTree(pimCatalog);
    }
}
