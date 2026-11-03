package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMatchTest {

    private static Product product(String mfn) {
        return new Product("c1", null, "5900000000055", mfn, "Brand", "Label", "Name", "Default");
    }

    @Test
    void aConflictOfOneCodeWithProductsWithoutACodeIsOnlyMissingCodes() {
        // given
        CatalogMatch.Ambiguous ambiguous = new CatalogMatch.Ambiguous(List.of(product("ZG-1"), product(null),
                product(" zg-1 ")));

        // when
        boolean onlyMissingCodes = ambiguous.onlyMissingCodes();

        // then
        assertThat(onlyMissingCodes).isTrue();
    }

    @Test
    void aConflictOfTwoCodesIsNotOnlyMissingCodes() {
        // given
        CatalogMatch.Ambiguous ambiguous = new CatalogMatch.Ambiguous(List.of(product("ZG-1"), product("ZG-2"),
                product(null)));

        // when
        boolean onlyMissingCodes = ambiguous.onlyMissingCodes();

        // then
        assertThat(onlyMissingCodes).isFalse();
    }
}
