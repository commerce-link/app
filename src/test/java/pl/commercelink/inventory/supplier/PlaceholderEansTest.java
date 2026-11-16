package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderEansTest {

    @Test
    void threeCodesUnderOneEanMakeItAPlaceholder() {
        // given
        List<InventoryItem> rows = List.of(offer("9999999999994", "ZT51042-T1E0000Z", 5),
                offer("9999999999994", "ZT61046-T0E01C0Z", 5), offer("9999999999994", "CBL-DC-392A1-02", 5));

        // when
        PlaceholderEans eans = PlaceholderEans.of(rows);

        // then
        assertThat(eans.contains("9999999999994")).isTrue();
        assertThat(eans.size()).isEqualTo(1);
    }

    @Test
    void twoCodesUnderOneEanAreNotAPlaceholder() {
        // given
        List<InventoryItem> rows = List.of(offer("8596375178328", "A-1", 5), offer("8596375178328", "B-2", 5));

        // when
        PlaceholderEans eans = PlaceholderEans.of(rows);

        // then
        assertThat(eans.contains("8596375178328")).isFalse();
        assertThat(eans.size()).isZero();
    }

    @Test
    void codesDifferingOnlyInNotationCountOnce() {
        // given
        List<InventoryItem> rows = List.of(offer("8596375178328", "3JA26AE", 5),
                offer("8596375178328", "3ja26ae", 5), offer("8596375178328", "3JA-26AE", 5));

        // when
        PlaceholderEans eans = PlaceholderEans.of(rows);

        // then
        assertThat(eans.contains("8596375178328")).isFalse();
    }

    @Test
    void rowsWithoutManufacturerCodeAreIgnored() {
        // given
        List<InventoryItem> rows = List.of(offer("9999999999994", "A-1", 5),
                offer("9999999999994", "B-2", 5), offer("9999999999994", null, 5));

        // when
        PlaceholderEans eans = PlaceholderEans.of(rows);

        // then
        assertThat(eans.contains("9999999999994")).isFalse();
    }

    @Test
    void noneContainsNothing() {
        // when
        PlaceholderEans eans = PlaceholderEans.none();

        // then
        assertThat(eans.contains("9999999999994")).isFalse();
        assertThat(eans.contains(null)).isFalse();
        assertThat(eans.size()).isZero();
    }

    private static InventoryItem offer(String ean, String mfn, int qty) {
        return new InventoryItem(ean, mfn, 10.0, "PLN", qty, 1, "Acme");
    }
}
