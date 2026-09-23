package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormNumbersTest {

    @Test
    void parsesIntegersWithSpacesAndRejectsGarbage() {
        // when / then
        assertThat(FormNumbers.integer(" 1 193 ")).contains(1193);
        assertThat(FormNumbers.integer("12")).contains(12);
        assertThat(FormNumbers.integer("")).isEmpty();
        assertThat(FormNumbers.integer(null)).isEmpty();
        assertThat(FormNumbers.integer("1,5")).isEmpty();
        assertThat(FormNumbers.integer("abc")).isEmpty();
    }

    @Test
    void parsesDecimalsWithCommaOrDot() {
        // when / then
        assertThat(FormNumbers.decimal("1,10")).contains(1.10);
        assertThat(FormNumbers.decimal("1.05")).contains(1.05);
        assertThat(FormNumbers.decimal("2 500,00")).contains(2500.0);
        assertThat(FormNumbers.decimal("x")).isEmpty();
    }

    @Test
    void formatsPolishStyle() {
        // when / then
        assertThat(FormNumbers.format(1312)).isEqualTo("1 312,00");
        assertThat(FormNumbers.format(0.5)).isEqualTo("0,50");
        assertThat(FormNumbers.formatInt(1193)).isEqualTo("1 193");
        assertThat(FormNumbers.formatInt(58)).isEqualTo("58");
    }

    /** RF-2: a multiplier or a markup is shown as saved, so a save that does not touch the field keeps the price. */
    @Test
    void decimalFieldsKeepTwoDecimalsAtLeastAndNeverCutTheRest() {
        // when / then
        assertThat(FormNumbers.formatDecimalField(1.1)).isEqualTo("1,10");
        assertThat(FormNumbers.formatDecimalField(1)).isEqualTo("1,00");
        assertThat(FormNumbers.formatDecimalField(1.125)).isEqualTo("1,125");
        assertThat(FormNumbers.formatDecimalField(1.075)).isEqualTo("1,075");
        assertThat(FormNumbers.formatDecimalField(0.123456)).isEqualTo("0,123456");
        assertThat(FormNumbers.decimal(FormNumbers.formatDecimalField(1.125))).contains(1.125);
    }
}
