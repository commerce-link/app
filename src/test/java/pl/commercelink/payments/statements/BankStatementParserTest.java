package pl.commercelink.payments.statements;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.BankStatementFormat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankStatementParserTest {

    @Test
    void readsTheOperationsBelowThePreambleOfAWindows1250Export() {
        // when
        ParsedStatement statement = BankStatementParser.parse(StatementSamples.mbank(), StatementSamples.mbankFormat(), "PL61");

        // then
        assertThat(statement.operations()).hasSize(4);
        assertThat(statement.incoming()).hasSize(3);
        assertThat(statement.outgoingCount()).isEqualTo(1);
        BankOperation first = statement.operations().get(0);
        assertThat(first.line()).isEqualTo(10);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(first.amount()).isEqualByComparingTo("1249.00");
        assertThat(first.title()).isEqualTo("PRZELEW ZEWNĘTRZNY PRZYCHODZĄCY Zamowienie 3f9a2c1e-7b4d-4e1a-9c2f-5d8e6a7b9c01");
        assertThat(first.sender()).isEqualTo("JAN KOWALSKI UL. LIPOWA 4");
        assertThat(first.senderAccount()).isEqualTo("83102018110000030201459921");
        assertThat(statement.operations().get(1).amount()).isEqualByComparingTo("-2310.50");
    }

    @Test
    void leavesOutTheClosingBalanceWithoutReportingIt() {
        // when
        ParsedStatement statement = BankStatementParser.parse(StatementSamples.mbank(), StatementSamples.mbankFormat(), "PL61");

        // then
        assertThat(statement.problems()).isEmpty();
        assertThat(statement.skippedRows()).isEqualTo(1);
    }

    @Test
    void givesIdenticalOperationsOfOneDayDifferentNumbersThatStayTheSameOnTheNextUpload() {
        // given
        BankStatementFormat format = StatementSamples.mbankFormat();

        // when
        List<BankOperation> first = BankStatementParser.parse(StatementSamples.mbank(), format, "PL61").operations();
        List<BankOperation> again = BankStatementParser.parse(StatementSamples.mbank(), format, "PL61").operations();

        // then
        assertThat(first.get(2).number()).startsWith("WB-").hasSize(13).isNotEqualTo(first.get(3).number());
        assertThat(again).extracting(BankOperation::number).isEqualTo(first.stream().map(BankOperation::number).toList());
    }

    @Test
    void numbersOperationsOfAnotherAccountDifferently() {
        // given
        BankStatementFormat format = StatementSamples.mbankFormat();

        // when
        String here = BankStatementParser.parse(StatementSamples.mbank(), format, "PL61").operations().get(0).number();
        String there = BankStatementParser.parse(StatementSamples.mbank(), format, "PL27").operations().get(0).number();

        // then
        assertThat(here).isNotEqualTo(there);
    }

    @Test
    void takesTheBanksOwnNumberWhenTheExportHasOne() {
        // given
        String csv = "Data,Kwota,Tytul,Nr\n07.10.2026,640.00,FV 0377/09/2026,TRX-881\n";
        BankStatementFormat format = format("UTF-8", ",", 1, "dd.MM.yyyy", false);
        format.setDateColumn("Data");
        format.setAmountColumn("Kwota");
        format.setTitleColumns(List.of("Tytul"));
        format.setTransactionNoColumn("Nr");

        // when
        BankOperation operation = BankStatementParser.parse(csv.getBytes(StandardCharsets.UTF_8), format, "PL61").operations().get(0);

        // then
        assertThat(operation.number()).isEqualTo("TRX-881");
        assertThat(operation.amount()).isEqualByComparingTo("640.00");
    }

    @Test
    void readsCreditsAndDebitsFromTwoColumns() {
        // given
        String csv = "Data;Uznania;Obciazenia;Opis\n2026-10-07;100,00;;wplata\n2026-10-08;;50,00;oplata\n";
        BankStatementFormat format = format("UTF-8", ";", 1, "yyyy-MM-dd", true);
        format.setDateColumn("Data");
        format.setCreditColumn("Uznania");
        format.setDebitColumn("Obciazenia");
        format.setTitleColumns(List.of("Opis"));

        // when
        List<BankOperation> operations = BankStatementParser.parse(csv.getBytes(StandardCharsets.UTF_8), format, "x").operations();

        // then
        assertThat(operations).extracting(BankOperation::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("-50.00"));
    }

    @Test
    void namesTheColumnsOfAFileWithoutAHeaderByTheirPosition() {
        // given
        String csv = "2026-10-07;\"zakupy\";159,00\n";
        BankStatementFormat format = format("UTF-8", ";", 0, "yyyy-MM-dd", true);
        format.setDateColumn("[1]");
        format.setAmountColumn("[3]");
        format.setTitleColumns(List.of("[2]"));

        // when
        ParsedStatement statement = BankStatementParser.parse(csv.getBytes(StandardCharsets.UTF_8), format, "x");

        // then
        assertThat(statement.headers()).containsExactly("[1]", "[2]", "[3]");
        assertThat(statement.operations().get(0).title()).isEqualTo("zakupy");
    }

    @Test
    void reportsARowWhoseAmountCannotBeRead() {
        // given
        String csv = "Data;Kwota;Tytul\n2026-10-07;abc;x\n2026-10-08;12,00;y\n";
        BankStatementFormat format = format("UTF-8", ";", 1, "yyyy-MM-dd", true);
        format.setDateColumn("Data");
        format.setAmountColumn("Kwota");
        format.setTitleColumns(List.of("Tytul"));

        // when
        ParsedStatement statement = BankStatementParser.parse(csv.getBytes(StandardCharsets.UTF_8), format, "x");

        // then
        assertThat(statement.problems()).containsExactly(new ParsedStatement.RowProblem(2, ParsedStatement.Problem.AMOUNT));
        assertThat(statement.operations()).hasSize(1);
    }

    @Test
    void refusesAFileWithoutAConfiguredColumn() {
        // given
        BankStatementFormat format = StatementSamples.mbankFormat();
        format.setSenderColumn("#Kontrahent");

        // when / then
        assertThatThrownBy(() -> BankStatementParser.parse(StatementSamples.mbank(), format, "x"))
                .isInstanceOf(StatementFileException.class)
                .extracting("messageKey").isEqualTo("statement.file.error.column");
    }

    private static BankStatementFormat format(String encoding, String separator, int headerRow, String dateFormat,
                                              boolean decimalComma) {
        BankStatementFormat format = new BankStatementFormat();
        format.setEncoding(encoding);
        format.setSeparator(separator);
        format.setHeaderRow(headerRow);
        format.setDateFormat(dateFormat);
        format.setDecimalComma(decimalComma);
        return format;
    }
}
