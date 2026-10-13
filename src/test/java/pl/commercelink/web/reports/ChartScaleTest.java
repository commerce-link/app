package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartScaleTest {

    @Test
    void theAxisEndsAtTheSmallestRoundValueHoldingTheMaximum() {
        // then
        assertThat(ChartScale.fitting(49_000, false).top()).isEqualTo(60_000);
        assertThat(ChartScale.fitting(809_000, false).top()).isEqualTo(1_000_000);
        assertThat(ChartScale.fitting(40_000, false).top()).isEqualTo(40_000);
        assertThat(ChartScale.fitting(20, true).top()).isEqualTo(20);
    }

    @Test
    void countsGetWholeTicks() {
        // when
        ChartScale scale = ChartScale.fitting(5, true);

        // then
        assertThat(scale.ticks()).allMatch(tick -> tick == Math.floor(tick));
        assertThat(scale.top()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void anEmptyOrNegativeSeriesStillHasAnAxisAndNoNegativeHeights() {
        // when
        ChartScale scale = ChartScale.fitting(0, false);

        // then
        assertThat(scale.top()).isPositive();
        assertThat(scale.percent(-120)).isZero();
        assertThat(scale.percent(Double.NaN)).isZero();
        assertThat(scale.percent(scale.top() * 2)).isEqualTo(100);
    }
}
