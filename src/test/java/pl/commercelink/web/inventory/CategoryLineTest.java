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
    void catalogLineListsEveryMatchingCategory() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(FAN, ACCESSORIES, OTHER), List.of());

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.catalogLabel()).isEqualTo("Local Catalog › Fan");
        assertThat(line.catalogMore()).isEqualTo(1);
        assertThat(line.catalogLabels()).containsExactly("Local Catalog › Fan", "Local Catalog › Akcesoria");
        assertThat(line.inCatalogLabels()).isEmpty();
        assertThat(line.addableElsewhere()).isFalse();
    }

    @Test
    void productInOneOfTwoMatchingCategoriesSaysWhereAndCanBeAddedToTheOther() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(FAN, ACCESSORIES, OTHER),
                List.of(new CatalogPlacement.Existing("c-1", "cat-acc", "p-1", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalog()).isTrue();
        assertThat(line.inCatalogLabels()).containsExactly("Local Catalog › Akcesoria");
        assertThat(line.inCatalogHref()).isEqualTo("/dashboard/catalogs/c-1/category/cat-acc/products/p-1");
        assertThat(line.addableElsewhere()).isTrue();
    }

    @Test
    void productInEveryMatchingCategoryListsThemAllAndOffersNoOtherOne() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(FAN, ACCESSORIES, OTHER),
                List.of(new CatalogPlacement.Existing("c-1", "cat-fan", "p-1", KEY),
                        new CatalogPlacement.Existing("c-1", "cat-acc", "p-2", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.inCatalogLabels()).containsExactly("Local Catalog › Fan", "Local Catalog › Akcesoria");
        assertThat(line.inCatalogHref()).isEqualTo("/dashboard/catalogs/c-1/category/cat-fan/products/p-1");
        assertThat(line.addableElsewhere()).isFalse();
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
        assertThat(line.addableElsewhere()).isTrue();
    }

    @Test
    void productWhosePimCategoryMatchesNoCatalogCategoryIsNotAddableElsewhere() {
        // given
        CatalogPlacement.StorePlacement placement = new CatalogPlacement.StorePlacement(List.of(OTHER),
                List.of(new CatalogPlacement.Existing("c-2", "cat-other", "p-1", KEY)));

        // when
        CategoryLine line = CategoryLine.of(tree, placement, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.catalogUnmatched()).isTrue();
        assertThat(line.inCatalogLabels()).containsExactly("Sklep B2B › Inne");
        assertThat(line.addableElsewhere()).isFalse();
    }

    @Test
    void withoutPlacementEverythingCatalogRelatedIsEmpty() {
        // when
        CategoryLine line = CategoryLine.of(tree, null, "11", "Wentylatory", KEY, false);

        // then
        assertThat(line.catalogLabels()).isEmpty();
        assertThat(line.inCatalogLabels()).isEmpty();
        assertThat(line.addableElsewhere()).isFalse();
        assertThat(line.inCatalog()).isFalse();
    }

    private static PimCategoryTree tree() {
        PimCatalog pimCatalog = mock(PimCatalog.class);
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Chłodzenie", "pl"), new PimCategory("11", "10", "Wentylatory", "pl")));
        return new PimCategoryTree(pimCatalog);
    }
}
