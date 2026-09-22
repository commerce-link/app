package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.Product;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryPageModelTest {

    private static Product product(String label, boolean enabled) {
        Product p = new Product("cat", "pim", "1", "m", "b", label, "n", "Default");
        p.setEnabled(enabled);
        return p;
    }

    private static CategoryDefinition category(String... labels) {
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        category.setGroupingOrder(List.of(labels));
        return category;
    }

    @Test
    void countsStatusesLabelsAndFeatures() {
        // given
        CategoryDefinition gpu = category("A", "B", "C");
        List<ProductRow> rows = List.of(
                ProductRow.of(product("A", true), gpu, "c", n -> n),
                ProductRow.of(product("A", false), gpu, "c", n -> n),
                ProductRow.of(product("B", true), gpu, "c", n -> n));

        // when
        CategoryPageModel model = CategoryPageModel.of(rows, gpu);

        // then
        assertThat(model.statusCounts())
                .containsEntry(ProductStatus.ACTIVE, 2)
                .containsEntry(ProductStatus.DISABLED, 1)
                .containsEntry(ProductStatus.NO_PIM, 0);
        assertThat(model.labelOptions()).extracting(CategoryPageModel.LabelOption::value).containsExactly("A", "B", "C");
        assertThat(model.labelOptions().get(0).count()).isEqualTo(2);
        assertThat(model.labelOptions().get(2).count()).isZero();
        assertThat(model.featureCounts()).containsEntry("marketplace", 0).containsKeys("stock", "srp", "mrp", "service");
        assertThat(model.total()).isEqualTo(3);
        assertThat(model.dynamic()).isFalse();
    }

    @Test
    void labelsOutsideTheListAppearAtTheEndOfTheOptions() {
        // given
        CategoryDefinition gpu = category("A");
        List<ProductRow> rows = List.of(ProductRow.of(product("Z", true), gpu, "c", n -> n));

        // when / then
        assertThat(CategoryPageModel.of(rows, gpu).labelOptions())
                .extracting(CategoryPageModel.LabelOption::value).containsExactly("A", "Z");
    }

    @Test
    void anAutomaticCategorySaysWhetherItHasAPimMapping() {
        // given
        CategoryDefinition gpu = category();
        gpu.setType(CategoryDefinitionType.Dynamic);
        gpu.setPimCategoryIds(List.of("pim-gpu"));

        // when
        CategoryPageModel model = CategoryPageModel.of(List.of(), gpu);

        // then
        assertThat(model.dynamic()).isTrue();
        assertThat(model.hasMapping()).isTrue();
        assertThat(model.hasLabels()).isFalse();
    }
}
