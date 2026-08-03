package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfflinePimCatalogTest {

    private final OfflinePimCatalog catalog = new OfflinePimCatalog();

    @Test
    void servesTheBundledIcecatCategoryTree() {
        // when
        List<PimCategory> categories = catalog.allCategories();

        // then
        assertThat(categories).hasSize(6798);
        assertThat(categories).allMatch(category -> "pl".equals(category.lang()));
    }

    @Test
    void bundledTreeContainsKnownLeavesWithPolishNames() {
        // when
        List<PimCategory> categories = catalog.allCategories();

        // then
        assertThat(categories).contains(new PimCategory("1584", "8760", "Telewizory", "pl"));
        assertThat(categories).contains(new PimCategory("194", "191", "Klawiatury", "pl"));
        assertThat(categories.stream().filter(c -> "989".equals(c.id())).findFirst().orElseThrow().name())
                .isEqualTo("Procesory");
    }

    @Test
    void everythingBesidesCategoriesStaysEmpty() {
        // when / then
        assertThat(catalog.findAll()).isEmpty();
        assertThat(catalog.findByPimId("any")).isEmpty();
        assertThat(catalog.findByGtinOrMpn("1", "2")).isEmpty();
        assertThat(catalog.allBrands()).isEmpty();
    }
}
