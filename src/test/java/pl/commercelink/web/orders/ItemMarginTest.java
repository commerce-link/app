package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMarginTest {

    @Test
    void theMarginIsTheShareOfThePriceTheStoreKeepsWithOneDecimal() {
        // when
        ItemMargin margin = ItemMargin.of(2269.00, 1928.64, null);

        // then: 340,36 of 2 269,00
        assertThat(margin.tone()).isEqualTo(ItemMargin.Tone.OK);
        assertThat(margin.percent()).isEqualTo("15,0");
        assertThat(margin.profit()).isEqualTo("340,36");
        assertThat(margin.cost()).isEqualTo("1 928,64");
        assertThat(margin.threshold()).isNull();
        assertThat(margin.marked()).isFalse();
    }

    @Test
    void aMarginBelowTheStoresThresholdIsLowAndOneAtItIsNot() {
        // when
        ItemMargin below = ItemMargin.of(100, 90.5, 10.0);
        ItemMargin at = ItemMargin.of(100, 90, 10.0);

        // then
        assertThat(below.tone()).isEqualTo(ItemMargin.Tone.LOW);
        assertThat(below.percent()).isEqualTo("9,5");
        assertThat(below.threshold()).isEqualTo("10");
        assertThat(below.marked()).isTrue();
        assertThat(at.tone()).isEqualTo(ItemMargin.Tone.OK);
    }

    @Test
    void aSaleBelowCostIsALossWithOrWithoutAThreshold() {
        // when
        ItemMargin loss = ItemMargin.of(500, 553.5, null);
        ItemMargin free = ItemMargin.of(0, 10, 12.5);

        // then
        assertThat(loss.tone()).isEqualTo(ItemMargin.Tone.LOSS);
        assertThat(loss.percent()).isEqualTo("−10,7");
        assertThat(loss.profit()).isEqualTo("−53,50");
        assertThat(free.tone()).isEqualTo(ItemMargin.Tone.LOSS);
        assertThat(free.percent()).isNull();
        assertThat(free.threshold()).isEqualTo("12,5");
    }

    @Test
    void anItemWithoutAPurchaseCostHasNoMarginRatherThanAHundredPercent() {
        // when
        ItemMargin margin = ItemMargin.of(299, 0, 10.0);

        // then
        assertThat(margin.tone()).isEqualTo(ItemMargin.Tone.UNKNOWN);
        assertThat(margin.percent()).isNull();
        assertThat(margin.cost()).isNull();
        assertThat(margin.marked()).isFalse();
    }
}
