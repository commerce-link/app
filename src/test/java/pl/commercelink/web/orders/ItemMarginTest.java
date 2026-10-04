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
}
