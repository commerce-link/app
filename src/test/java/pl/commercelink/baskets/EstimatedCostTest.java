package pl.commercelink.baskets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstimatedCostTest {

    @Test
    void theEstimateIsHalfwayBetweenTheLowestAndTheMedian() {
        // when / then
        assertThat(EstimatedCost.between(372.06, 389.82)).isEqualTo(380.94);
    }

    @Test
    void theEstimateIsRoundedHalfUpToGrosze() {
        // when / then
        assertThat(EstimatedCost.between(10.01, 10.02)).isEqualTo(10.02);
    }

    @Test
    void anUnknownCostGivesNoEstimate() {
        // when / then
        assertThat(EstimatedCost.between(0, 389.82)).isZero();
        assertThat(EstimatedCost.between(372.06, 0)).isZero();
    }
}
