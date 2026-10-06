package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.PimCategoryTree;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryCountsTest {

    @Test
    void leafCountsAddUpToEveryAncestorAndUnknownIdsKeepTheirOwn() {
        // given
        PimCatalog catalog = mock(PimCatalog.class);
        when(catalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty", "pl"),
                new PimCategory("11", "10", "Karty graficzne", "pl"),
                new PimCategory("12", "10", "Dyski SSD", "pl")));
        PimCategoryTree tree = new PimCategoryTree(catalog);

        // when
        Map<String, Integer> counts = CategoryCounts.rollUp(Map.of("11", 3, "12", 2, "77", 4, "-", 1), tree);

        // then
        assertThat(counts).containsEntry("10", 5).containsEntry("11", 3).containsEntry("12", 2)
                .containsEntry("77", 4).containsEntry("-", 1);
    }
}
