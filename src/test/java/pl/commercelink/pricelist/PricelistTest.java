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

    @Test
    void firstRowWinsForRepeatedPimId() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(
                row("pim-1", "5900000000011", "first"),
                row("pim-1", "5900000000028", "second")));

        // when / then
        assertThat(pricelist.findByPimId("pim-1")).map(AvailabilityAndPrice::getName).contains("first");
    }

    @Test
    void findsRowByEanWrittenWithLeadingZeros() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(row("pim-1", "05900000000011", "first")));

        // when / then
        assertThat(pricelist.findByEan(" 5900000000011 ")).map(AvailabilityAndPrice::getPimId).contains("pim-1");
    }

    @Test
    void firstRowWinsForRepeatedEan() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(
                row("pim-1", "5900000000011", "first"),
                row("pim-2", "5900000000011", "second")));

        // when / then
        assertThat(pricelist.findByEan("5900000000011")).map(AvailabilityAndPrice::getName).contains("first");
    }

    @Test
    void blankEanFindsNoRowWithoutEan() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(row("pim-1", "", "first")));

        // when / then
        assertThat(pricelist.findByEan(" ")).isEmpty();
    }

    private AvailabilityAndPrice item(String category, String label, String name) {
        return new AvailabilityAndPrice("pim", "ean", "mfn", "brand", label, name, category, 100, 1, 1, 100, false);
    }

    private AvailabilityAndPrice row(String pimId, String ean, String name) {
        return new AvailabilityAndPrice(pimId, ean, "mfn", "brand", "label", name, "CPU", 100, 1, 1, 100, false);
    }
}
