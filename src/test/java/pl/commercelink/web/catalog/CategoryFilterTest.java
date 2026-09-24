package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N5: {@link CategoryFilter} re-validates everything a request hands it before it is echoed back into a link or a
 * redirect -- nothing of the address reaches one unchecked.
 */
class CategoryFilterTest {

    @Test
    void statusFallsBackToTheDefaultUnlessItIsAKnownStatusOrAll() {
        assertThat(CategoryFilter.of("active", null, null, null).status()).isEqualTo("active");
        assertThat(CategoryFilter.of("disabled", null, null, null).status()).isEqualTo("disabled");
        assertThat(CategoryFilter.of("nopim", null, null, null).status()).isEqualTo("nopim");
        assertThat(CategoryFilter.of("all", null, null, null).status()).isEqualTo("all");
        assertThat(CategoryFilter.of("deleted", null, null, null).status()).isEqualTo(CategoryFilter.DEFAULT_STATUS);
        assertThat(CategoryFilter.of(null, null, null, null).status()).isEqualTo(CategoryFilter.DEFAULT_STATUS);
        assertThat(CategoryFilter.of("<script>", null, null, null).status()).isEqualTo(CategoryFilter.DEFAULT_STATUS);
    }

    @Test
    void featureIsKeptOnlyWhenItIsOneOfTheKnownOnes() {
        assertThat(CategoryFilter.of(null, "marketplace", null, null).feature()).isEqualTo("marketplace");
        assertThat(CategoryFilter.of(null, "stock", null, null).feature()).isEqualTo("stock");
        assertThat(CategoryFilter.of(null, "srp", null, null).feature()).isEqualTo("srp");
        assertThat(CategoryFilter.of(null, "mrp", null, null).feature()).isEqualTo("mrp");
        assertThat(CategoryFilter.of(null, "service", null, null).feature()).isEqualTo("service");
        assertThat(CategoryFilter.of(null, "colour", null, null).feature()).isNull();
        assertThat(CategoryFilter.of(null, null, null, null).feature()).isNull();
    }

    @Test
    void labelIsTrimmedAndAllMeansNoLabel() {
        assertThat(CategoryFilter.of(null, null, "  RTX 5070  ", null).label()).isEqualTo("RTX 5070");
        assertThat(CategoryFilter.of(null, null, "all", null).label()).isNull();
        assertThat(CategoryFilter.of(null, null, "   ", null).label()).isNull();
        assertThat(CategoryFilter.of(null, null, null, null).label()).isNull();
    }

    @Test
    void searchIsTrimmed() {
        assertThat(CategoryFilter.of(null, null, null, "  msi  ").search()).isEqualTo("msi");
        assertThat(CategoryFilter.of(null, null, null, "   ").search()).isNull();
        assertThat(CategoryFilter.of(null, null, null, null).search()).isNull();
    }

    /** A label or a search the page could not have produced -- longer than MAX_TEXT (200) -- is dropped, not echoed. */
    @Test
    void labelAndSearchLongerThanMaxTextAreDropped() {
        String exactly200 = "a".repeat(200);
        String over200 = "a".repeat(201);

        assertThat(CategoryFilter.of(null, null, exactly200, null).label()).isEqualTo(exactly200);
        assertThat(CategoryFilter.of(null, null, over200, null).label()).isNull();
        assertThat(CategoryFilter.of(null, null, null, exactly200).search()).isEqualTo(exactly200);
        assertThat(CategoryFilter.of(null, null, null, over200).search()).isNull();
    }

    @Test
    void queryEncodesEveryValueItCarries() {
        // given
        CategoryFilter filter = CategoryFilter.of("all", "stock", "R&D + 5070", "Wąż \r\n łódź");

        // when
        String query = filter.query();

        // then -- '&' and '+' of the values are encoded, so they cannot be read as extra parameters or as spaces;
        // a literal space becomes '+', Polish letters and CR/LF are percent-encoded (UTF-8).
        assertThat(query).isEqualTo("?status=all&feature=stock&label=R%26D+%2B+5070&q=W%C4%85%C5%BC+%0D%0A+%C5%82%C3%B3d%C5%BA");
        assertThat(query).startsWith("?status=");
        assertThat(query.chars().filter(c -> c == '?').count()).isEqualTo(1);
    }

    /** Without feature, label or search the query carries only the status, which is always stated. */
    @Test
    void queryDropsAbsentParametersAndAlwaysStatesTheStatus() {
        assertThat(CategoryFilter.of(null, null, null, null).query()).isEqualTo("?status=" + CategoryFilter.DEFAULT_STATUS);
    }
}
