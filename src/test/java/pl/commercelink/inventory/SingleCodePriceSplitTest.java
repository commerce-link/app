package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SingleCodePriceSplitTest {

    private static final String CODE = "38S0910";

    @Test
    void lexmarkSplitsIntoOneGroupPerEan() {
        // given
        List<InventoryItem> lexmark = List.of(
                offer("734646731461", 2283.68, "A"), offer("734646731461", 2290.00, "B"),
                offer("734646763349", 6249.53, "A"), offer("734646763349", 6300.00, "B"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(lexmark);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(group -> assertThat(group).hasSize(2));
    }

    @Test
    void ratioOfExactlyTwoDoesNotSplit() {
        // given
        List<InventoryItem> items = List.of(offer("E1", 100, "A"), offer("E2", 200, "B"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(items);

        // then
        assertThat(result).containsExactly(items);
    }

    @Test
    void ratioBelowTwoDoesNotSplit() {
        // given
        List<InventoryItem> items = List.of(offer("E1", 100, "A"), offer("E2", 190, "B"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(items);

        // then
        assertThat(result).containsExactly(items);
    }

    @Test
    void everythingBelowFiftyDoesNotSplit() {
        // given
        List<InventoryItem> items = List.of(offer("E1", 20, "A"), offer("E2", 49, "B"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(items);

        // then
        assertThat(result).containsExactly(items);
    }

    @Test
    void highestMeanOfExactlyFiftySplits() {
        // given
        List<InventoryItem> items = List.of(offer("E1", 20, "A"), offer("E2", 50, "B"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(items);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void averageIsTakenPerEan() {
        // given
        List<InventoryItem> items = List.of(offer("EA", 100, "A"), offer("EA", 300, "B"), offer("EB", 150, "C"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(items);

        // then
        assertThat(result).containsExactly(items);
    }

    @Test
    void oneOutlierAmongThreeEansSplitsAllThree() {
        // given
        List<InventoryItem> items = List.of(offer("E1", 384, "A"), offer("E2", 385, "B"), offer("E3", 7120, "C"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(items);

        // then
        assertThat(result).hasSize(3);
    }

    @Test
    void singleEanIsReturnedAsIs() {
        // given
        List<InventoryItem> items = List.of(offer("E1", 100, "A"), offer("E1", 900, "B"));

        // when
        List<List<InventoryItem>> result = SingleCodePriceSplit.byEan(items);

        // then
        assertThat(result).containsExactly(items);
    }

    private InventoryItem offer(String ean, double netPrice, String supplier) {
        return new InventoryItem("Lexmark", null, ean, CODE, supplier, netPrice, "PLN", 5, 1, true, false, false);
    }
}
