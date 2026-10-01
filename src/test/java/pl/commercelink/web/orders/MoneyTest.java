package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void groupsThousandsWithANonBreakingSpaceAndAlwaysShowsTwoDecimals() {
        // when / then
        assertThat(Money.format(1234)).isEqualTo("1\u00A0234,00");
        assertThat(Money.format(13307.5)).isEqualTo("13\u00A0307,50");
        assertThat(Money.format(0)).isEqualTo("0,00");
    }

    @Test
    void roundsHalfUpOnTheDecimalValueNotTheBinaryOne() {
        // when / then
        assertThat(Money.format(0.1 + 0.2)).isEqualTo("0,30");
        assertThat(Money.format(2.675)).isEqualTo("2,68");
        assertThat(Money.format(1.005)).isEqualTo("1,01");
    }

    @Test
    void negativeAmountsUseTheMinusSignAndZeroIsNeverNegative() {
        // when / then
        assertThat(Money.format(-12.5)).isEqualTo("\u221212,50");
        assertThat(Money.format(-0.001)).isEqualTo("0,00");
    }
}
