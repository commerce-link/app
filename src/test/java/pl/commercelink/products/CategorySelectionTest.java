package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.Taxonomy;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CategorySelectionTest {

    private static Taxonomy taxonomy(String category, String categoryId) {
        return new Taxonomy("5901234567890", "MFN-1", "Brand", "Name",
                category, 1, null, null, null, categoryId);
    }

    @Test
    void matchesWhenCategoryIdIsSelected() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234"));

        // when / then
        assertThat(selection.matches(taxonomy("Klawiatury", "1234"))).isTrue();
    }

    @Test
    void doesNotMatchWhenCategoryIdIsPresentButNotSelected() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234"));

        // when / then
        assertThat(selection.matches(taxonomy("Klawiatury", "9999"))).isFalse();
    }

    @Test
    void rowWithSelectedCategoryNameButNoCategoryIdDoesNotMatch() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234"));

        // when / then
        assertThat(selection.matches(taxonomy("Klawiatury", null))).isFalse();
    }

    @Test
    void rowWithSelectedCategoryNameButBlankCategoryIdDoesNotMatch() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234"));

        // when / then
        assertThat(selection.matches(taxonomy("Klawiatury", "  "))).isFalse();
    }

    @Test
    void matchesAnyOfSeveralSelectedIds() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234", "5678"));

        // when / then
        assertThat(selection.matches(taxonomy("Myszki", "5678"))).isTrue();
    }

    @Test
    void doesNotMatchTaxonomyWithoutCategoryOrId() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234"));

        // when / then
        assertThat(selection.matches(taxonomy(null, null))).isFalse();
    }

    @Test
    void doesNotMatchEmptyTaxonomySentinel() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234"));

        // when / then
        assertThat(selection.matches(Taxonomy.EMPTY)).isFalse();
    }

    @Test
    void doesNotMatchNullTaxonomy() {
        // given
        CategorySelection selection = CategorySelection.of(List.of("1234"));

        // when / then
        assertThat(selection.matches(null)).isFalse();
    }

    @Test
    void emptySelectionMatchesNothing() {
        // when / then
        assertThat(CategorySelection.EMPTY.matches(taxonomy("Klawiatury", "1234"))).isFalse();
    }

    @Test
    void emptySelectionIsEmpty() {
        // when / then
        assertThat(CategorySelection.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void selectionWithIdsIsNotEmpty() {
        // when / then
        assertThat(CategorySelection.of(List.of("1234")).isEmpty()).isFalse();
    }

    @Test
    void ofIgnoresNullAndBlankEntries() {
        // given
        CategorySelection selection = CategorySelection.of(
                java.util.Arrays.asList("1234", null, "  ", ""));

        // then
        assertThat(selection.categoryIds()).containsExactly("1234");
    }

    @Test
    void ofTreatsNullCollectionAsEmpty() {
        // when
        CategorySelection selection = CategorySelection.of(null);

        // then
        assertThat(selection.isEmpty()).isTrue();
    }

    @Test
    void selectionIsImmutable() {
        // given
        Set<String> ids = new java.util.HashSet<>(List.of("1234"));
        CategorySelection selection = CategorySelection.of(ids);

        // when
        ids.add("5678");

        // then
        assertThat(selection.categoryIds()).containsExactly("1234");
    }
}
