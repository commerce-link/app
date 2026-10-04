package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLowMarginControllerTest {

    @Test
    void theThresholdIsReadWithACommaADotOrAPercentSignAndBlankClearsIt() {
        assertThat(OrderLowMarginController.parse("12,5")).isEqualByComparingTo("12.5");
        assertThat(OrderLowMarginController.parse(" 12.50 % ")).isEqualByComparingTo("12.5");
        assertThat(OrderLowMarginController.parse("10")).isEqualByComparingTo("10");
        assertThat(OrderLowMarginController.parse("")).isNull();
        assertThat(OrderLowMarginController.parse(null)).isNull();
    }

    @Test
    void anythingOutsideZeroToAHundredOrWithMoreThanTwoDecimalsIsRefused() {
        for (String value : new String[]{"0", "-5", "100", "150", "abc", "10,555", "1,2,3"}) {
            assertThatThrownBy(() -> OrderLowMarginController.parse(value)).as(value)
                    .isInstanceOf(NumberFormatException.class);
        }
    }
}
