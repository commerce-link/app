package pl.commercelink.payments.statements;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatementAmountsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1 249,00|true|1249.00",
            "-2 310,50|true|-2310.50",
            "+640,00 PLN|true|640.00",
            "2 310,50-|true|-2310.50",
            "1.234,56|true|1234.56",
            "1,234.56|false|1234.56",
            "-1234.5|false|-1234.50",
            "\u22129,00|true|-9.00"
    })
    void readsAmountsAsBanksPrintThem(String cell, boolean decimalComma, String expected) {
        // when / then
        assertThat(StatementAmounts.parse(cell, decimalComma)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"abc|true", "''|true", "12,3,4|true", "#Saldo|true"})
    void readsNoNumberAsNull(String cell, boolean decimalComma) {
        // when / then
        assertThat(StatementAmounts.parse(cell, decimalComma)).isNull();
    }
}
