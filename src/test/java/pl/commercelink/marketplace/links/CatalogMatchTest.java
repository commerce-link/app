package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMatchTest {

    private static Product product(String mfn) {
        return product("5900000000055", mfn);
    }

    private static Product product(String ean, String mfn) {
        return new Product("c1", null, ean, mfn, "Brand", "Label", "Name", "Default");
    }

    @Test
    void oneEanCarriedByProductsWithDifferentCodesIsSharedInTheCatalog() {
        // given
        CatalogMatch.Ambiguous ambiguous = new CatalogMatch.Ambiguous(List.of(product(" 05900000000055", "ZG-1"),
                product("5900000000055", "ZG-2")));

        // when
        boolean shared = ambiguous.sharedEanInCatalog();

        // then
        assertThat(shared).isTrue();
    }

    @Test
    void theOffersEansLeadingToDifferentProductsAreNotSharedInTheCatalog() {
        // given each EAN leads to one product; only the offer's two EANs point at different ones
        CatalogMatch.Ambiguous ambiguous = new CatalogMatch.Ambiguous(List.of(product("5900000000055", "ZG-1"),
                product("5900000000055", "zg-1"), product("5900000000062", "LN-1")));

        // when
        boolean shared = ambiguous.sharedEanInCatalog();

        // then
        assertThat(shared).isFalse();
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
