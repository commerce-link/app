package pl.commercelink.inventory.supplier.api;

import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ProductCategory;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomyCategoryKeyTest {

    @Test
    void sixArgSynthesizesCategoryKeyFromEnumName() {
        // given / when
        Taxonomy t = new Taxonomy("E", "M", "B", "N", ProductCategory.Laptops, 5);

        // then
        assertThat(t.categoryKey()).isEqualTo("Laptops");
    }

    @Test
    void eightArgWithOtherSynthesizesOtherAndIsNotProcessable() {
        // given / when
        Taxonomy t = new Taxonomy("E", "M", "B", "N", ProductCategory.Other, 5, 100, 200);

        // then
        assertThat(t.categoryKey()).isEqualTo("Other");
        assertThat(t.isProcessable()).isFalse();
    }

    @Test
    void nineArgKeepsExplicitNonEnumKeyAndIsProcessableEvenWhenEnumIsOther() {
        // given / when
        Taxonomy t = new Taxonomy("E", "M", "B", "N", ProductCategory.Other, 5, null, null, "Cables");

        // then
        assertThat(t.categoryKey()).isEqualTo("Cables");
        assertThat(t.isProcessable()).isTrue();
    }

    @Test
    void nineArgWithBlankKeySynthesizesFromEnum() {
        // given / when
        Taxonomy t = new Taxonomy("E", "M", "B", "N", ProductCategory.CPU, 5, null, null, "  ");

        // then
        assertThat(t.categoryKey()).isEqualTo("CPU");
    }

    @Test
    void emptyKeepsOtherKeyAndIsNotProcessable() {
        // given / when / then
        assertThat(Taxonomy.EMPTY.categoryKey()).isEqualTo("Other");
        assertThat(Taxonomy.EMPTY.isProcessable()).isFalse();
    }
}
