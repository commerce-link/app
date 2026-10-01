package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountParserTest {

    @Test
    void readsCommaDotAndSpaces() {
        // then
        assertThat(AmountParser.parse("149,99")).isEqualByComparingTo("149.99");
        assertThat(AmountParser.parse("149.99")).isEqualByComparingTo("149.99");
        assertThat(AmountParser.parse("1 499,99")).isEqualByComparingTo("1499.99");
        assertThat(AmountParser.parse("1 499,99")).isEqualByComparingTo("1499.99");
        assertThat(AmountParser.parse("1 499,99")).isEqualByComparingTo("1499.99");
        assertThat(AmountParser.parse(" 1 499.50 ")).isEqualByComparingTo("1499.50");
        assertThat(AmountParser.parse("-100")).isEqualByComparingTo("-100");
        assertThat(AmountParser.parse("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(AmountParser.parse(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void refusesAThousandsDotWithADecimalComma() {
        // then
        assertThat(AmountParser.parse("1.000,50")).isNull();
        assertThat(AmountParser.parse("1,000.50")).isNull();
        assertThat(AmountParser.parse("1,2,3")).isNull();
        assertThat(AmountParser.parse("abc")).isNull();
    }

    @Test
    void refusesMoreThanTwoDecimalPlaces() {
        // then: "1.000" / "1,000" is most likely a thousand typed with a separator, never silently one złoty
        assertThat(AmountParser.parse("1.000")).isNull();
        assertThat(AmountParser.parse("1,000")).isNull();
        assertThat(AmountParser.parse("100,125")).isNull();
        assertThat(AmountParser.parse("0,001")).isNull();
        assertThat(AmountParser.parse("1,5")).isEqualByComparingTo("1.50");
        assertThat(AmountParser.parse("1,50")).isEqualByComparingTo("1.50");
    }

    @Test
    void roundsAnExponentHalfUpToTheGrosz() {
        // then
        assertThat(AmountParser.parse("5e-3")).isEqualByComparingTo("0.01");
        assertThat(AmountParser.parse("-5e-3")).isEqualByComparingTo("-0.01");
        assertThat(AmountParser.parse("1e-4")).isEqualByComparingTo("0.00");
    }
}
