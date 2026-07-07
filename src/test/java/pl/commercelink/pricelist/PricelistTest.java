package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PricelistTest {

    @Test
    void findsItemByCategoryLabelAndName() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(
                item("CPU", "label1", "name1"),
                item("GPU", "label2", "name2")));

        // when
        Optional<AvailabilityAndPrice> found = pricelist.findByCategoryLabelAndName("GPU", "label2", "name2");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("name2");
    }

    @Test
    void findsNothingWhenCategoryDoesNotMatch() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(item("CPU", "label1", "name1")));

        // when
        Optional<AvailabilityAndPrice> found = pricelist.findByCategoryLabelAndName("Smartwatches", "label1", "name1");

        // then
        assertThat(found).isEmpty();
    }

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
        return new AvailabilityAndPrice("pim", "ean", "mfn", "brand", label, name, category, 100, 1, 1, 100);
    }
}
