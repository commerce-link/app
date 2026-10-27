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
    void aSaleAtCostIsALossToo() {
        // when
        ItemMargin even = ItemMargin.of(500, 500, 1.23);
        ItemMargin grosz = ItemMargin.of(500.01, 500, 1.23);

        // then — client 2026-10-27: red at a margin of 0 or less, grey above it, no other threshold
        assertThat(even.tone()).isEqualTo(ItemMargin.Tone.LOSS);
        assertThat(even.percent()).isEqualTo("0,0");
        assertThat(grosz.tone()).isEqualTo(ItemMargin.Tone.OK);
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
