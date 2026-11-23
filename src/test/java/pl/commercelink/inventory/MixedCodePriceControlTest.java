package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MixedCodePriceControlTest {

    private final MixedCodePriceControl control = new MixedCodePriceControl();

    @Test
    void uniformGroupIsLeftAsItIs() {
        // given
        List<InventoryItem> offers = List.of(
                item("EAN1", "A", 100, "S1", "Brand"),
                item("EAN1", "B", 110, "S2", "Brand"));

        // when
        Optional<List<List<InventoryItem>>> refined = control.refine(offers);

        // then
        assertThat(refined).isEmpty();
    }

    @Test
    void splitPriceBelowFiftyLeavesTheGroup() {
        // given
        List<InventoryItem> offers = List.of(
                item("EAN1", "A", 40, "S1", "Brand"),
                item("EAN1", "A", 45, "S2", "Brand"),
                item("EAN2", "B", 90, "S3", "Brand"));

        // when
        Optional<List<List<InventoryItem>>> refined = control.refine(offers);

        // then
        assertThat(refined).isEmpty();
    }

    @Test
    void distinctCodesInPriceGroupsBecomeTwoGroups() {
        // given
        List<InventoryItem> offers = List.of(
                item("EAN1", "A", 100, "S1", "Brand"),
                item("EAN1", "A", 110, "S2", "Brand"),
                item("EAN2", "B", 400, "S3", "Brand"),
                item("EAN2", "B", 420, "S4", "Brand"));

        // when
        Optional<List<List<InventoryItem>>> refined = control.refine(offers);

        // then
        assertThat(prices(refined)).containsExactly(List.of(100.0, 110.0), List.of(400.0, 420.0));
    }

    @Test
    void singleOfferPartIsDropped() {
        // given
        List<InventoryItem> offers = List.of(
                item("EAN1", "A", 100, "S1", "Brand"),
                item("EAN2", "B", 400, "S3", "Brand"),
                item("EAN2", "B", 420, "S4", "Brand"));

        // when
        Optional<List<List<InventoryItem>>> refined = control.refine(offers);

        // then
        assertThat(prices(refined)).containsExactly(List.of(400.0, 420.0));
    }

    @Test
    void offersOutsideHalfToOneAndAHalfOfTheAverageAreDropped() {
        // given: the cheap part averages 80, the offer at 30 is below half of it
        List<InventoryItem> offers = List.of(
                item("EAN1", "A", 30, "S5", "Brand"),
                item("EAN1", "A", 100, "S1", "Brand"),
                item("EAN1", "A", 110, "S2", "Brand"),
                item("EAN2", "B", 400, "S3", "Brand"),
                item("EAN2", "B", 420, "S4", "Brand"));

        // when
        Optional<List<List<InventoryItem>>> refined = control.refine(offers);

        // then
        assertThat(prices(refined)).containsExactly(List.of(100.0, 110.0), List.of(400.0, 420.0));
    }

    @Test
    void mixedCodePriceControlKeepsAnEmptyPartInItsAnswer() {
        // given: the cheap part averages 55, 10 is below half of it and 100 above one and a half of it
        List<InventoryItem> offers = List.of(
                item("EAN1", "A", 10, "S1", "Brand"),
                item("EAN1", "A", 100, "S2", "Brand"),
                item("EAN2", "B", 400, "S3", "Brand"),
                item("EAN2", "B", 420, "S4", "Brand"));

        // when
        Optional<List<List<InventoryItem>>> refined = control.refine(offers);

        // then
        assertThat(prices(refined)).containsExactly(List.of(), List.of(400.0, 420.0));
    }

    private static List<List<Double>> prices(Optional<List<List<InventoryItem>>> refined) {
        assertThat(refined).isPresent();
        return refined.get().stream().map(set -> set.stream().map(InventoryItem::netPrice).toList()).toList();
    }

    private InventoryItem item(String ean, String mfn, double netPrice, String supplier, String brand) {
        return new InventoryItem(brand, null, ean, mfn, supplier, netPrice, "PLN", 5, 1, true, false, false);
    }
}
