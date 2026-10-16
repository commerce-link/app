package pl.commercelink.payments.statements;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.BankStatementFormat;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BankStatementSnifferTest {

    @Test
    void guessesTheFormatOfAnMBankExport() {
        // when
        BankStatementFormat format = BankStatementSniffer.guess(StatementSamples.mbank());

        // then
        assertThat(format.getEncoding()).isEqualTo("windows-1250");
        assertThat(format.getSeparator()).isEqualTo(";");
        assertThat(format.getHeaderRow()).isEqualTo(9);
        assertThat(format.getDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(format.isDecimalComma()).isTrue();
        assertThat(format.getDateColumn()).isEqualTo("#Data księgowania");
        assertThat(format.getAmountColumn()).isEqualTo("#Kwota");
        assertThat(format.getTitleColumns()).containsExactly("#Opis operacji", "#Tytuł");
        assertThat(format.getSenderColumn()).isEqualTo("#Nadawca/Odbiorca");
        assertThat(format.getSenderAccountColumn()).isEqualTo("#Numer konta");
        assertThat(format.getTransactionNoColumn()).isNull();
    }

    @Test
    void guessedFormatReadsTheFile() {
        // given
        BankStatementFormat format = BankStatementSniffer.guess(StatementSamples.mbank());

        // when
        ParsedStatement statement = BankStatementParser.parse(StatementSamples.mbank(), format, "PL61");

        // then
        assertThat(statement.operations()).hasSize(4);
        assertThat(statement.problems()).isEmpty();
    }

    @Test
    void guessesAUtf8FileWithCommasAndDotDecimals() {
        // given
        String csv = "\ufeffDate,Description,Amount,Reference\n07.10.2026,Order 3f9a2c1e,1249.00,TRX-1\n08.10.2026,Fee,-9.00,TRX-2\n";

        // when
        BankStatementFormat format = BankStatementSniffer.guess(csv.getBytes(StandardCharsets.UTF_8));

        // then
        assertThat(format.getEncoding()).isEqualTo("UTF-8");
        assertThat(format.getSeparator()).isEqualTo(",");
        assertThat(format.getHeaderRow()).isEqualTo(1);
        assertThat(format.getDateFormat()).isEqualTo("dd.MM.yyyy");
        assertThat(format.isDecimalComma()).isFalse();
        assertThat(format.getDateColumn()).isEqualTo("Date");
        assertThat(format.getAmountColumn()).isEqualTo("Amount");
        assertThat(format.getTitleColumns()).containsExactly("Description");
        assertThat(format.getTransactionNoColumn()).isEqualTo("Reference");
    }
}
