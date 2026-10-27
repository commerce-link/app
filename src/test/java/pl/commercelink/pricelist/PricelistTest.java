package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Test
    void findsTheRowByBothUnifiedCodesWhateverItsPimId() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(
                row("other", "5900000000099", "OTHER"),
                row("clone-C", "05900000000011", "ab-1 ")));

        // when / then
        assertThat(pricelist.findByCodes("5900000000011", "AB-1", Set.of())).map(AvailabilityAndPrice::getPimId)
                .contains("clone-C");
    }

    @Test
    void doesNotMatchByASingleCodeOrWhenACodeDiffers() {
        // given
        Pricelist pricelist = new Pricelist("id", List.of(row("p", "5900000000011", "AB-1")));

        // when / then
        assertThat(pricelist.findByCodes("5900000000011", "OTHER", Set.of())).isEmpty();
        assertThat(pricelist.findByCodes("5900000000099", "AB-1", Set.of())).isEmpty();
        assertThat(pricelist.findByCodes("5900000000011", null, Set.of())).isEmpty();
        assertThat(pricelist.findByCodes(null, "AB-1", Set.of())).isEmpty();
        assertThat(pricelist.findByCodes("", " ", Set.of())).isEmpty();
    }

    @Test
    void neverTakesTheRowOfAnExcludedPimId() {
        // given the same codes on the row of another product of the export and on the stale row of a deleted clone
        Pricelist pricelist = new Pricelist("id", List.of(
                row("pim-A", "5900000000011", "AB-1"),
                row("clone-C", "5900000000011", "AB-1")));

        // when / then
        assertThat(pricelist.findByCodes("5900000000011", "AB-1", Set.of("pim-A"))).map(AvailabilityAndPrice::getPimId)
                .contains("clone-C");
        assertThat(pricelist.findByCodes("5900000000011", "AB-1", Set.of("pim-A", "clone-C"))).isEmpty();
        assertThat(pricelist.findByCodes("5900000000011", "AB-1", Set.of())).map(AvailabilityAndPrice::getPimId)
                .contains("pim-A");
    }

    private AvailabilityAndPrice row(String pimId, String ean, String mfn) {
        return new AvailabilityAndPrice(pimId, ean, mfn, "brand", "label", "name", "CPU", 100, 1, 1, 100, false);
    }

    private AvailabilityAndPrice item(String category, String label, String name) {
        return new AvailabilityAndPrice("pim", "ean", "mfn", "brand", label, name, category, 100, 1, 1, 100, false);
    }
}
