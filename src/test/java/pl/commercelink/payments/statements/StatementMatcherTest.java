package pl.commercelink.payments.statements;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.PayoutChannel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.payments.statements.StatementOrders.*;

class StatementMatcherTest {

    private static final String JAN = "3f9a2c1e-7b4d-4e1a-9c2f-5d8e6a7b9c01";
    private static final String ANNA = "c42d91f0-1111-4222-8333-444455556666";

    @Test
    void matchesTheOrderNumberInTheTitleWhenTheTransferPaysExactlyWhatIsOwed() {
        // given
        Order order = order(JAN, 1249.00, pending(PaymentSource.BankTransfer));

        // when
        OperationMatch match = single(List.of(order), operation("WB-1", "1249.00", "Zamowienie " + JAN, "JAN KOWALSKI"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(match.rule()).isEqualTo(MatchRule.ORDER_NUMBER);
        assertThat(match.allocations()).containsExactly(new Allocation(JAN, new BigDecimal("1249.00"), new BigDecimal("0.00")));
        assertThat(match.matchedTexts()).containsExactly(JAN);
    }

    @Test
    void recognisesTheShortOrderNumberAndProposesAnUnderpaymentForChecking() {
        // given
        Order order = order(ANNA, 399.99, pending(PaymentSource.BankTransfer));

        // when
        OperationMatch match = single(List.of(order), operation("WB-1", "389.99", "Platnosc za zamowienie c42d91f0", "ANNA"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.CHECK);
        assertThat(match.warnings()).containsExactly(MatchWarning.UNDERPAID);
        assertThat(match.difference()).isEqualByComparingTo("10.00");
        assertThat(match.allocations()).extracting(Allocation::amount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("389.99"));
    }

    @Test
    void splitsOneTransferOverTheOrdersOfTheInvoicesInItsTitle() {
        // given
        Order first = withDocument(order("e19a4b70-0000-4000-8000-000000000001", 4305.00, pending(PaymentSource.BankTransfer)), "FV 0398/10/2026");
        Order second = withDocument(order("7c0d2f11-0000-4000-8000-000000000002", 3075.00, pending(PaymentSource.BankTransfer)), "FV 0401/10/2026");

        // when
        OperationMatch match = single(List.of(first, second),
                operation("WB-1", "7380.00", "FV 0398/10/2026, FV0401/10/2026", "ELEKTRO-SERWIS S.C."));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(match.rule()).isEqualTo(MatchRule.DOCUMENT_NUMBER);
        assertThat(match.allocations()).extracting(Allocation::orderId).containsExactly(first.getOrderId(), second.getOrderId());
    }

    @Test
    void doesNotTakeOneDocumentNumberForAnotherThatEndsTheSame() {
        // given
        Order order = withDocument(order("e19a4b70-0000-4000-8000-000000000001", 100.00, pending(PaymentSource.BankTransfer)), "FV 1/10/2026");

        // when
        OperationMatch match = single(List.of(order), operation("WB-1", "100.00", "FV 11/10/2026", "X"));

        // then
        assertThat(match.rule()).isNotEqualTo(MatchRule.DOCUMENT_NUMBER);
    }

    @Test
    void matchesTheReferenceNumberOfThePendingPayment() {
        // given
        Order order = order(JAN, 500.00, pending(PaymentSource.BankTransfer, null, "PRO-0391/10/2026", 0));

        // when
        OperationMatch match = single(List.of(order), operation("WB-1", "500.00", "proforma PRO-0391/10/2026", "BUDMAX"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(match.rule()).isEqualTo(MatchRule.REFERENCE_NUMBER);
    }

    @Test
    void showsAnOperationWhoseNumberIsOnAPaymentAsBooked() {
        // given
        Order order = order(JAN, 879.00, booked(879.00, "WB-ABC"));

        // when
        OperationMatch match = single(List.of(order), operation("wb-abc", "879.00", "Zamowienie " + JAN, "X"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.BOOKED);
        assertThat(match.candidates()).containsExactly(JAN);
    }

    @Test
    void readsAClosedOrderNamedInTheTitleToRecogniseARepeatedUpload() {
        // given
        Order completed = order("a07c5d38-0000-4000-8000-000000000003", 1530.00, booked(1530.00, "WB-OLD"));
        List<String> lookups = new ArrayList<>();
        StatementMatcher matcher = new StatementMatcher(List.of(), List.of(), token -> {
            lookups.add(token);
            return token.equals("a07c5d38") ? List.of(completed) : List.of();
        });

        // when
        OperationMatch match = matcher.match(List.of(operation("WB-OLD", "1530.00", "Zamowienie a07c5d38", "IWONA"))).get(0);

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.BOOKED);
        assertThat(lookups).containsExactly("a07c5d38");
    }

    @Test
    void warnsAboutMoneyForAnOrderThatIsAlreadyPaid() {
        // given
        Order paid = order(JAN, 100.00, booked(100.00, "WB-OTHER"));

        // when
        OperationMatch match = single(List.of(paid), operation("WB-NEW", "100.00", "Zamowienie 3f9a2c1e", "X"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.UNMATCHED);
        assertThat(match.warnings()).containsExactly(MatchWarning.ORDER_ALREADY_PAID);
        assertThat(match.candidates()).containsExactly(JAN);
    }

    @Test
    void doesNotLookUpEightDigitsThatAreNoOrderNumber() {
        // given
        List<String> lookups = new ArrayList<>();
        StatementMatcher matcher = new StatementMatcher(List.of(), List.of(), token -> {
            lookups.add(token);
            return List.of();
        });

        // when
        matcher.match(List.of(operation("WB-1", "10.00", "Przelewy24 wyplata 18822731", "PAYPRO SA")));

        // then
        assertThat(lookups).isEmpty();
    }

    @Test
    void proposesTheRunOfAPayoutChannelsOrdersThatAddsUpToTheTransfer() {
        // given
        PayoutChannel allegro = new PayoutChannel("ALLEGRO", "Allegro", null, null, true);
        Order first = orderedOn(fromMarketplace(order("91c2e7a0-0000-4000-8000-000000000001", 649.00,
                pending(PaymentSource.OnlinePayment, "", "7f2c9e10", 64.90)), "Allegro"), 3);
        Order second = orderedOn(fromMarketplace(order("5d0b8f13-0000-4000-8000-000000000002", 219.00,
                pending(PaymentSource.OnlinePayment, "", "b41d0a73", 23.13)), "Allegro"), 4);
        Order later = orderedOn(fromMarketplace(order("6e7f1a28-0000-4000-8000-000000000003", 329.00,
                pending(PaymentSource.OnlinePayment, "", "6b28d3e1", 32.90)), "Allegro"), 7);
        Order shop = orderedOn(order("d48c0b57-0000-4000-8000-000000000004", 780.00, pending(PaymentSource.BankTransfer)), 2);
        StatementMatcher matcher = new StatementMatcher(List.of(later, shop, second, first), List.of(allegro), token -> List.of());

        // when
        OperationMatch match = matcher.match(List.of(operation("WB-1", "779.97",
                "Wyplata srodkow Allegro Finanse 2026-10-08", "ALLEGRO SP. Z O.O."))).get(0);

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.PAYOUT);
        assertThat(match.payout()).containsExactly(allegro);
        assertThat(match.allocations()).containsExactly(
                new Allocation(first.getOrderId(), new BigDecimal("649.00"), new BigDecimal("64.90")),
                new Allocation(second.getOrderId(), new BigDecimal("219.00"), new BigDecimal("23.13")));
        assertThat(match.candidates()).containsExactly(first.getOrderId(), second.getOrderId(), later.getOrderId());
    }

    @Test
    void leavesAPayoutToTheOperatorWhenNoRunAddsUp() {
        // given
        PayoutChannel furgonetka = new PayoutChannel("FURGONETKA", null, PaymentSource.CashOnDelivery, null, false);
        Order cod = order(JAN, 500.00, pending(PaymentSource.CashOnDelivery));

        // when
        OperationMatch match = new StatementMatcher(List.of(cod), List.of(furgonetka), token -> List.of())
                .match(List.of(operation("WB-1", "488.00", "Pobrania 41/2026", "FURGONETKA SP. Z O.O."))).get(0);

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.PAYOUT);
        assertThat(match.allocations()).isEmpty();
        assertThat(match.warnings()).containsExactly(MatchWarning.NO_PAYOUT_PROPOSAL);
        assertThat(match.candidates()).containsExactly(JAN);
    }

    @Test
    void aBankPaysForLoansTakenInTheShopAndOverThePhoneInOneTransfer() {
        // given
        List<PayoutChannel> santander = List.of(
                new PayoutChannel("SANTANDER", null, PaymentSource.Installments, null, false),
                new PayoutChannel("SANTANDER", PayoutChannel.SHOP, PaymentSource.OnlinePayment, "Santander eRaty", false));
        Order shop = orderedOn(order(JAN, 1000.00, pending(PaymentSource.OnlinePayment, "Santander eRaty", "SR-1", 0)), 3);
        Order phone = orderedOn(order(ANNA, 500.00, pending(PaymentSource.Installments)), 4);
        Order transfer = orderedOn(order("b71e04d2-0000-4000-8000-000000000003", 1500.00, pending(PaymentSource.BankTransfer)), 5);
        StatementMatcher matcher = new StatementMatcher(List.of(shop, phone, transfer), santander, token -> List.of());

        // when
        OperationMatch match = matcher.match(List.of(operation("WB-1", "1500.00", "Wyplata kredytow", "SANTANDER CONSUMER BANK"))).get(0);

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.PAYOUT);
        assertThat(match.payout()).isEqualTo(santander);
        assertThat(match.allocations()).extracting(Allocation::orderId).containsExactly(JAN, ANNA);
        assertThat(match.candidates()).containsExactly(JAN, ANNA);
    }

    @Test
    void oneGatewayPayingOutForTwoMarketplacesIsToldApartByTheSumAndTheirFees() {
        // given
        List<PayoutChannel> paypro = List.of(
                new PayoutChannel("PAYPRO", "Allegro", null, null, true),
                new PayoutChannel("PAYPRO", "Morele", null, null, false));
        Order allegro = orderedOn(fromMarketplace(order(JAN, 100.00, pending(PaymentSource.OnlinePayment, "", "A-1", 10.00)), "Allegro"), 3);
        Order morele = orderedOn(fromMarketplace(order(ANNA, 200.00, pending(PaymentSource.OnlinePayment, "", "M-1", 20.00)), "Morele"), 4);

        // when
        List<OperationMatch> matches = new StatementMatcher(List.of(allegro, morele), paypro, token -> List.of()).match(List.of(
                operation("WB-1", "200.00", "Przelewy24 wyplata 1", "PAYPRO SA"),
                operation("WB-2", "90.00", "Przelewy24 wyplata 2", "PAYPRO SA")));

        // then
        assertThat(matches.get(0).allocations()).containsExactly(new Allocation(ANNA, new BigDecimal("200.00"), new BigDecimal("0.00")));
        assertThat(matches.get(1).allocations()).containsExactly(new Allocation(JAN, new BigDecimal("100.00"), new BigDecimal("10.00")));
    }

    @Test
    void aGatewayPayingOutEachPaymentLessItsFeeIsMatchedByThePaymentNumberWithTheFeeAsTheDifference() {
        // given
        PayoutChannel paynow = new PayoutChannel("PAYNOW", PayoutChannel.SHOP, PaymentSource.OnlinePayment, "PayNow", true);
        Order order = order(JAN, 15600.00, pending(PaymentSource.OnlinePayment, "PayNow", "P53V-ON8-ZAS-ESB", 0));
        BankOperation transfer = operation("WB-1", "15564.85",
                "MBANK S.A. PAYNOW, /OPF/X///// PLATNOSC NR: P53V-ON8-ZAS-ESB (klient@example.com)", "");

        // when
        OperationMatch match = new StatementMatcher(List.of(order), List.of(paynow), token -> List.of()).match(List.of(transfer)).get(0);

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.PAYOUT);
        assertThat(match.rule()).isEqualTo(MatchRule.REFERENCE_NUMBER);
        assertThat(match.warnings()).isEmpty();
        assertThat(match.allocations()).containsExactly(new Allocation(JAN, new BigDecimal("15600.00"), new BigDecimal("35.15")));
        assertThat(match.matchedTexts()).containsExactly("P53V-ON8-ZAS-ESB");
    }

    @Test
    void withoutItsPayoutRowAGatewaysTransferLooksLikeAnUnderpaymentByAnotherMethod() {
        // given
        Order order = order(JAN, 15600.00, pending(PaymentSource.OnlinePayment, "PayNow", "P53V-ON8-ZAS-ESB", 0));

        // when
        OperationMatch match = single(List.of(order), operation("WB-1", "15564.85", "PAYNOW PLATNOSC NR: P53V-ON8-ZAS-ESB", ""));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.CHECK);
        assertThat(match.warnings()).containsExactlyInAnyOrder(MatchWarning.UNDERPAID, MatchWarning.METHOD_MISMATCH);
    }

    @Test
    void prefersTheOrderOfTheClientWhoSentTheMoneyAmongOrdersOfTheSameAmount() {
        // given
        Order theirs = billedTo(order(JAN, 159.00, pending(PaymentSource.BankTransfer)), "Magdalena", "Król");
        Order other = billedTo(order(ANNA, 159.00, pending(PaymentSource.BankTransfer)), "Michał", "Lis");

        // when
        OperationMatch match = single(List.of(other, theirs), operation("WB-1", "159.00", "zakupy", "MAGDALENA KROL"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.CHECK);
        assertThat(match.rule()).isEqualTo(MatchRule.AMOUNT_AND_SENDER);
        assertThat(match.warnings()).containsExactly(MatchWarning.AMBIGUOUS);
        assertThat(match.allocations()).extracting(Allocation::orderId).containsExactly(JAN);
        assertThat(match.candidates()).startsWith(JAN);
    }

    @Test
    void offersOrdersOfTheSameAmountWithoutChoosingWhenNothingTellsThemApart() {
        // given
        Order first = order(JAN, 159.00, pending(PaymentSource.BankTransfer));
        Order second = order(ANNA, 159.00, pending(PaymentSource.BankTransfer));

        // when
        OperationMatch match = single(List.of(first, second), operation("WB-1", "159.00", "zakupy", "TOMASZ"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.CHECK);
        assertThat(match.allocations()).isEmpty();
        assertThat(match.candidates()).containsExactlyInAnyOrder(JAN, ANNA);
    }

    @Test
    void asksToCheckATransferForAnOrderWaitingForAnotherMethod() {
        // given
        Order order = order(JAN, 2150.00, pending(PaymentSource.OnlinePayment, "Przelewy24", "P24-1877", 5.00));

        // when
        OperationMatch match = single(List.of(order), operation("WB-1", "2150.00", "zamowienie 3f9a2c1e", "KAMIL"));

        // then
        assertThat(match.kind()).isEqualTo(MatchKind.CHECK);
        assertThat(match.warnings()).containsExactly(MatchWarning.METHOD_MISMATCH);
    }

    @Test
    void doesNotProposeTheSameDebtTwice() {
        // given
        Order order = order(JAN, 200.00, pending(PaymentSource.BankTransfer));
        StatementMatcher matcher = new StatementMatcher(List.of(order), List.of(), token -> List.of());

        // when
        List<OperationMatch> matches = matcher.match(List.of(
                operation("WB-1", "120.00", "zamowienie 3f9a2c1e czesc 1", "X"),
                operation("WB-2", "80.00", "zamowienie 3f9a2c1e czesc 2", "X")));

        // then
        assertThat(matches.get(0).warnings()).containsExactly(MatchWarning.UNDERPAID);
        assertThat(matches.get(1).kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(matches.get(1).allocations()).extracting(Allocation::amount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("80.00"));
    }

    @Test
    void leavesOutTheMoneyThatWentOut() {
        // given
        StatementMatcher matcher = new StatementMatcher(List.of(), List.of(), token -> List.of());

        // when
        List<OperationMatch> matches = matcher.match(List.of(operation("WB-1", "-9.00", "Oplata", "BANK")));

        // then
        assertThat(matches).isEmpty();
    }

    @Test
    void recognisesTheSenderAsTheClientByCompanyOrFullName() {
        // given
        Order person = billedTo(order(JAN, 1, pending(PaymentSource.BankTransfer)), "Kamil", "Zieliński");
        Order company = order(ANNA, 1, pending(PaymentSource.BankTransfer));
        BillingDetails billing = new BillingDetails();
        billing.setCompanyName("Budmax");
        company.setBillingDetails(billing);

        // when / then
        assertThat(StatementMatcher.senderIsClient("KAMIL ZIELINSKI UL. POLNA 1", person)).isTrue();
        assertThat(StatementMatcher.senderIsClient("KAMIL NOWAK", person)).isFalse();
        assertThat(StatementMatcher.senderIsClient("BUDMAX PIOTR NOWAK", company)).isTrue();
    }

    private static OperationMatch single(List<Order> orders, BankOperation operation) {
        return new StatementMatcher(orders, List.of(), token -> List.of()).match(List.of(operation)).get(0);
    }

    private static Order withDocument(Order order, String number) {
        order.addDocument(new Document("doc-" + number, number, null, DocumentType.Proforma));
        return order;
    }
}
