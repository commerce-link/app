package pl.commercelink.payments.statements;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.BankStatementFormat;
import pl.commercelink.stores.PayoutChannel;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.payments.statements.StatementOrders.order;
import static pl.commercelink.payments.statements.StatementOrders.pending;

/**
 * mBank's "Lista operacji" export (statements/mbank-lista-operacji.csv, made-up data in the bank's layout): a long
 * preamble, no sender column (the sender, its account and the operation kind are inside "#Opis operacji"), the store's
 * own account in "#Rachunek" and amounts with " PLN".
 */
class MbankOperationsListTest {

    @Test
    void theFormatIsGuessedFromTheFile() throws Exception {
        // when
        BankStatementFormat format = BankStatementSniffer.guess(file());

        // then
        assertThat(format.getEncoding()).isEqualTo("windows-1250");
        assertThat(format.getHeaderRow()).isEqualTo(26);
        assertThat(format.getDateColumn()).isEqualTo("#Data operacji");
        assertThat(format.getAmountColumn()).isEqualTo("#Kwota");
        assertThat(format.getTitleColumns()).containsExactly("#Opis operacji");
        assertThat(format.getSenderColumn()).isNull();
        assertThat(format.getSenderAccountColumn()).isNull();
    }

    @Test
    void everyOperationIsRead() throws Exception {
        // given
        BankStatementFormat format = BankStatementSniffer.guess(file());

        // when
        ParsedStatement statement = BankStatementParser.parse(file(), format, "PL61114020040000340276554567");

        // then
        assertThat(statement.operations()).hasSize(16);
        assertThat(statement.incoming()).hasSize(9);
        assertThat(statement.problems()).isEmpty();
        BankOperation first = statement.operations().get(0);
        assertThat(first.amount()).isEqualByComparingTo("3998.00");
        assertThat(first.title()).startsWith("JAN TESTOWY UL. TESTOWA 1").contains("Zamowienie 00000000-1000-0000-0000-000000000001");
        List<BankOperation> identical = statement.incoming().stream().filter(operation -> operation.title().contains("MARIA KOWALSKA")).toList();
        assertThat(identical).hasSize(2);
        assertThat(identical.get(0).number()).isNotEqualTo(identical.get(1).number());
    }

    @Test
    void theOrderNumberInTheDescriptionAndTheBanksLoanPayoutAreMatched() throws Exception {
        // given
        BankStatementFormat format = BankStatementSniffer.guess(file());
        List<BankOperation> incoming = BankStatementParser.parse(file(), format, "PL61").incoming();
        Order seeded = order("00000000-1000-0000-0000-000000000001", 3998.00, pending(PaymentSource.BankTransfer));
        Order loan = order("c42d91f0-1111-4222-8333-444455556666", 2150.00, pending(PaymentSource.Installments));
        PayoutChannel santander = new PayoutChannel("SANTANDER", null, PaymentSource.Installments, null, false);

        // when
        List<OperationMatch> matches = new StatementMatcher(List.of(seeded, loan), List.of(santander), token -> List.of())
                .match(incoming);

        // then
        OperationMatch transfer = matches.stream().filter(match -> match.operation().amount().intValue() == 3998).findFirst().orElseThrow();
        assertThat(transfer.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(transfer.allocations()).extracting(Allocation::orderId).containsExactly(seeded.getOrderId());
        OperationMatch payout = matches.stream().filter(match -> match.operation().title().startsWith("Santander")).findFirst().orElseThrow();
        assertThat(payout.kind()).isEqualTo(MatchKind.PAYOUT);
        assertThat(payout.allocations()).extracting(Allocation::orderId).containsExactly(loan.getOrderId());
    }

    @Test
    void theClientIsRecognisedInTheDescriptionWithoutASenderColumn() throws Exception {
        // given
        BankStatementFormat format = BankStatementSniffer.guess(file());
        List<BankOperation> incoming = BankStatementParser.parse(file(), format, "PL61").incoming();
        Order hers = StatementOrders.billedTo(order("e04b3d19-0000-4000-8000-000000000001", 159.00,
                pending(PaymentSource.BankTransfer)), "Maria", "Kowalska");
        Order other = StatementOrders.billedTo(order("a12f77c3-0000-4000-8000-000000000002", 159.00,
                pending(PaymentSource.BankTransfer)), "Michał", "Lis");

        // when
        List<OperationMatch> matches = new StatementMatcher(List.of(other, hers), List.of(), token -> List.of()).match(incoming);

        // then
        OperationMatch first = matches.stream().filter(match -> match.operation().title().contains("MARIA KOWALSKA")).findFirst().orElseThrow();
        assertThat(first.rule()).isEqualTo(MatchRule.AMOUNT_AND_SENDER);
        assertThat(first.allocations()).extracting(Allocation::orderId).containsExactly(hers.getOrderId());
    }

    private static byte[] file() throws Exception {
        try (InputStream stream = MbankOperationsListTest.class.getResourceAsStream("/statements/mbank-lista-operacji.csv")) {
            return stream.readAllBytes();
        }
    }
}
