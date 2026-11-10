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
    }

    @Test
    void noNameRowIsNotIndexed() {
        // given
        index.link("11T105", "NoName", "111");

        // when / then
        assertThat(index.eansOf("11T105", "NoName")).isEmpty();
    }

    @Test
    void lookupWithoutCodeOrBrandFindsNothing() {
        // given
        index.link("920", "Logitech", "111");

        // when / then
        assertThat(index.eansOf("920", null)).isEmpty();
        assertThat(index.eansOf(" ", "Logitech")).isEmpty();
    }

    @Test
    void blockedEanIsNotGivenForItsCodeAndBrand() {
        // given
        index.link("BRAK", "Brand", "111");
        index.link("BRAK", "Brand", "222");
        index.link("BRAK", "Other", "111");

        // when
        index.block("BRAK", " brand ", "111");

        // then
        assertThat(index.eansOf("BRAK", "Brand")).containsExactly("222");
        assertThat(index.eansOf("BRAK", "Other")).containsExactly("111");
    }

    @Test
    void blockWithoutCodeOrBrandBlocksNothing() {
        // given
        index.link("920", "Logitech", "111");

        // when
        index.block("920", null, "111");
        index.block(null, "Logitech", "111");

        // then
        assertThat(index.eansOf("920", "Logitech")).containsExactly("111");
    }

    @Test
    void clearForgetsTheBlocksToo() {
        // given
        index.block("920", "Logitech", "111");

        // when
        index.clear();
        index.link("920", "Logitech", "111");

        // then
        assertThat(index.eansOf("920", "Logitech")).containsExactly("111");
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
