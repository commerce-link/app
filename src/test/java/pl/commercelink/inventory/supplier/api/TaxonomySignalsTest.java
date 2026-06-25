package pl.commercelink.inventory.supplier.api;

import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxonomySignalsTest {

    @Test
    void existingCtorsDefaultSignalsToEmptyList() {
        // given / when
        Taxonomy sixArg = new Taxonomy("E", "M", "B", "N", ProductCategory.CPU, 5);
        Taxonomy eightArg = new Taxonomy("E", "M", "B", "N", ProductCategory.CPU, 5, 100, 200);
        Taxonomy nineArg = new Taxonomy("E", "M", "B", "N", ProductCategory.Other, 5, null, null, "Cables");

        // then
        assertThat(sixArg.signals()).isEmpty();
        assertThat(eightArg.signals()).isEmpty();
        assertThat(nineArg.signals()).isEmpty();
    }

    @Test
    void tenArgCarriesSignals() {
        // given / when
        Taxonomy t = new Taxonomy("E", "M", "B", "N", ProductCategory.Other, 5, null, null, "Cables",
                List.of("VENDOR_CATEGORY:Kable", "BRAND:Acme"));

        // then
        assertThat(t.signals()).containsExactly("VENDOR_CATEGORY:Kable", "BRAND:Acme");
        // additive: categoryKey unaffected by adding signals
        assertThat(t.categoryKey()).isEqualTo("Cables");
        assertThat(t.isProcessable()).isTrue();
    }

    @Test
    void nullSignalsNormalizeToEmptyList() {
        // given / when
        Taxonomy t = new Taxonomy("E", "M", "B", "N", ProductCategory.CPU, 5, null, null, "CPU", null);

        // then
        assertThat(t.signals()).isNotNull().isEmpty();
    }

    @Test
    void signalsAreImmutable() {
        // given
        Taxonomy t = new Taxonomy("E", "M", "B", "N", ProductCategory.CPU, 5, null, null, "CPU",
                List.of("VENDOR_CATEGORY:CPU"));

        // when / then
        assertThatThrownBy(() -> t.signals().add("MUTATED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyHasNoSignals() {
        // given / when / then
        assertThat(Taxonomy.EMPTY.signals()).isEmpty();
    }
}
