package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceSplitThresholdsTest {

    @Test
    void pricesMoreThanTwiceApartAreTooFar() {
        // when / then
        assertThat(PriceSplitThresholds.areTooFarApart(100, 210)).isTrue();
    }

    @Test
    void pricesWithinTheRatioAreNotTooFar() {
        // when / then
        assertThat(PriceSplitThresholds.areTooFarApart(100, 190)).isFalse();
    }

    @Test
    void orderOfThePricesDoesNotMatter() {
        // when / then
        assertThat(PriceSplitThresholds.areTooFarApart(210, 100)).isTrue();
    }

    @Test
    void ratioOfExactlyTwoIsNotTooFar() {
        // when / then
        assertThat(PriceSplitThresholds.areTooFarApart(100, 200)).isFalse();
    }

    @Test
    void notANumberIsAlwaysTooFar() {
        // when / then
        assertThat(PriceSplitThresholds.areTooFarApart(Double.NaN, 100)).isTrue();
        assertThat(PriceSplitThresholds.areTooFarApart(100, Double.NaN)).isTrue();
    }

    @Test
    void nonPositivePriceIsAlwaysTooFar() {
        // when / then
        assertThat(PriceSplitThresholds.areTooFarApart(0, 50)).isTrue();
    }
}
