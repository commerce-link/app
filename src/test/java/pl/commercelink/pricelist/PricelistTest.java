package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricelistTest {

    @Test
    void listsDistinctAvailableCategories() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(
                item("CPU", "label1", "name1"),
                item("CPU", "label2", "name2"),
                item("Smartwatches", "label3", "name3")));

        // when
        List<String> categories = pricelist.getAvailableCategories();

        // then
        assertThat(categories).containsExactly("CPU", "Smartwatches");
    }

    private AvailabilityAndPrice item(String category, String label, String name) {
        return new AvailabilityAndPrice("pim", "ean", "mfn", "brand", label, name, category, 100, 1, 1, 100, false);
    }
}
