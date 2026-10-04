package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMarginTest {

    @Test
    void theMarginIsTheShareOfThePriceTheStoreKeepsWithOneDecimal() {
        // when
        ItemMargin margin = ItemMargin.of(2269.00, 1928.64, 1.23);

        // then: 340,36 of 2 269,00; without 23% VAT 276,72
        assertThat(margin.tone()).isEqualTo(ItemMargin.Tone.OK);
        assertThat(margin.percent()).isEqualTo("15,0");
        assertThat(margin.profit()).isEqualTo("340,36");
        assertThat(margin.profitNet()).isEqualTo("276,72");
        assertThat(margin.cost()).isEqualTo("1 928,64");
    }

    @Test
    void aSaleBelowCostIsALoss() {
        // when
        ItemMargin loss = ItemMargin.of(500, 553.5, 1.23);
        ItemMargin free = ItemMargin.of(0, 10, 1.23);

        // then
        assertThat(loss.tone()).isEqualTo(ItemMargin.Tone.LOSS);
        assertThat(loss.percent()).isEqualTo("−10,7");
        assertThat(loss.profit()).isEqualTo("−53,50");
        assertThat(loss.profitNet()).isEqualTo("−43,50");
        assertThat(free.tone()).isEqualTo(ItemMargin.Tone.LOSS);
        assertThat(free.percent()).isNull();
    }

    @Test
    void anItemWithoutAPurchaseCostHasNoMarginRatherThanAHundredPercent() {
        // when
        ItemMargin margin = ItemMargin.of(299, 0, 1.23);

        // then
        assertThat(margin.tone()).isEqualTo(ItemMargin.Tone.UNKNOWN);
        assertThat(margin.percent()).isNull();
        assertThat(margin.cost()).isNull();
    }

    @Test
    void aMarginBelowItsThresholdIsLowAndOneAtItIsNot() {
        // when
        ItemMargin below = ItemMargin.of(100, 90.5, 1.23, new pl.commercelink.stores.MarginConfiguration.Threshold(10.0, null));
        ItemMargin at = ItemMargin.of(100, 90, 1.23, new pl.commercelink.stores.MarginConfiguration.Threshold(10.0, null));

        // then
        assertThat(below.tone()).isEqualTo(ItemMargin.Tone.LOW);
        assertThat(below.threshold()).isEqualTo("10");
        assertThat(below.messageKey()).isEqualTo("order.items.margin.low");
        assertThat(at.tone()).isEqualTo(ItemMargin.Tone.OK);
    }

    @Test
    void aLowMarginAgainstACategorysThresholdNamesTheCategory() {
        // when
        ItemMargin margin = ItemMargin.of(2269.00, 1928.64, 1.23, new pl.commercelink.stores.MarginConfiguration.Threshold(20, "CPU"));

        // then: 15,0% against 20% for CPU
        assertThat(margin.tone()).isEqualTo(ItemMargin.Tone.LOW);
        assertThat(margin.thresholdCategory()).isEqualTo("CPU");
        assertThat(margin.messageKey()).isEqualTo("order.items.margin.low.category");
    }

    @Test
    void aLossStaysALossWhateverTheThreshold() {
        // when
        ItemMargin margin = ItemMargin.of(500, 553.5, 1.23, new pl.commercelink.stores.MarginConfiguration.Threshold(10, null));

        // then
        assertThat(margin.tone()).isEqualTo(ItemMargin.Tone.LOSS);
        assertThat(margin.messageKey()).isEqualTo("order.items.margin.loss");
    }
}
