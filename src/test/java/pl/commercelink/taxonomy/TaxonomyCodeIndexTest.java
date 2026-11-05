package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomyCodeIndexTest {

    private final TaxonomyCodeIndex index = new TaxonomyCodeIndex();

    @Test
    void givesTheEansThatCameWithTheCodeAndBrand() {
        // given
        index.link("920", "Logitech", "111");
        index.link("920", "Logitech", "222");
        index.link("920", "Natec", "333");

        // when / then
        assertThat(index.eansOf("920", "Logitech")).containsExactly("111", "222");
    }

    @Test
    void comparesBrandsIgnoringCaseAndSurroundingSpaces() {
        // given
        index.link("920", "Logitech", "111");

        // when / then
        assertThat(index.eansOf("920", " logitech ")).containsExactly("111");
    }

    @Test
    void listsAnEanOnceForTheSameCodeAndBrand() {
        // given
        index.link("920", "Logitech", "111");
        index.link("920", "LOGITECH", "111");

        // when / then
        assertThat(index.eansOf("920", "Logitech")).containsExactly("111");
    }

    @Test
    void rowWithoutCodeOrBrandIsNotIndexed() {
        // given
        index.link("920", null, "111");
        index.link("920", " ", "222");
        index.link(null, "Logitech", "333");

        // when / then
        assertThat(index.eansOf("920", "Logitech")).isEmpty();
        assertThat(index.eansOfSingleBrand("920")).isEmpty();
    }

    @Test
    void lookupWithoutCodeOrBrandFindsNothing() {
        // given
        index.link("920", "Logitech", "111");

        // when / then
        assertThat(index.eansOf("920", null)).isEmpty();
        assertThat(index.eansOf(null, "Logitech")).isEmpty();
        assertThat(index.eansOfSingleBrand(" ")).isEmpty();
    }

    @Test
    void codeWithOneBrandGivesAllItsEans() {
        // given
        index.link("920", "Logitech", "111");
        index.link("920", "logitech", "222");

        // when / then
        assertThat(index.eansOfSingleBrand("920")).containsExactly("111", "222");
    }

    @Test
    void codeThatCameWithTwoBrandsGivesNothing() {
        // given
        index.link("920", "Logitech", "111");
        index.link("920", "Natec", "111");

        // when / then
        assertThat(index.eansOfSingleBrand("920")).isEmpty();
    }

    @Test
    void clearForgetsEverything() {
        // given
        index.link("920", "Logitech", "111");

        // when
        index.clear();

        // then
        assertThat(index.eansOf("920", "Logitech")).isEmpty();
    }
}
