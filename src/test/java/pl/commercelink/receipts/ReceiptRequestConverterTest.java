package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.receipts.api.LineKind;
import pl.commercelink.receipts.api.PaymentForm;
import pl.commercelink.receipts.api.ReceiptRequest;
import pl.commercelink.receipts.api.VatRate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptRequestConverterTest {

    private static final String KEY = ORDER_ID + ":R1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 13, 0);

    private final ReceiptRequestConverter converter = new ReceiptRequestConverter();
    private final FakeReceiptProvider provider = new FakeReceiptProvider();

    private ReceiptRequestSnapshot converted(Order order, java.util.List<pl.commercelink.orders.OrderItem> items) {
        ReceiptConversion conversion = converter.convert(order, items, KEY, provider, NOW);
        assertThat(conversion).isInstanceOf(ReceiptConversion.Converted.class);
        return ((ReceiptConversion.Converted) conversion).snapshot();
    }

    private ReceiptConversion.Blocked blocked(Order order, java.util.List<pl.commercelink.orders.OrderItem> items) {
        ReceiptConversion conversion = converter.convert(order, items, KEY, provider, NOW);
        assertThat(conversion).isInstanceOf(ReceiptConversion.Blocked.class);
        return (ReceiptConversion.Blocked) conversion;
    }

    @Test
    void goodsAndPaidShippingBecomeLinesWithOneTransferPayment() {
        ReceiptRequestSnapshot snapshot = converted(b2cOrder(4014.99),
                items(item("Laptop X15", 1, 3999.00, 1.23), delivery("Kurier InPost", 15.99, 1.23)));

        ReceiptRequest request = snapshot.toRequest(KEY);
        assertThat(request.lines()).hasSize(2);
        assertThat(request.lines().get(0).kind()).isEqualTo(LineKind.GOODS);
        assertThat(request.lines().get(0).vatRate()).isEqualTo(VatRate.VAT_23);
        assertThat(request.lines().get(1).kind()).isEqualTo(LineKind.SHIPPING);
        assertThat(request.lines().get(1).name()).isEqualTo("Dostawa 23% Kurier InPost");
        assertThat(request.payments()).singleElement().satisfies(p -> {
            assertThat(p.form()).isEqualTo(PaymentForm.TRANSFER);
            assertThat(p.amount().grosze()).isEqualTo(401499);
            assertThat(p.label()).isEqualTo("Przelewy24");
        });
        assertThat(request.buyer().email()).isEqualTo("jan@example.com");
        assertThat(request.buyer().taxId()).isNull();
        assertThat(request.saleDate()).isEqualTo(DELIVERED_AT);
    }

    @Test
    void freeShippingIsLeftOut() {
        ReceiptRequestSnapshot snapshot = converted(b2cOrder(100.00),
                items(item("Mysz", 1, 100.00, 1.23), delivery("Odbiór osobisty", 0, 1.23)));

        assertThat(snapshot.lines()).singleElement().extracting(ReceiptRequestSnapshot.Line::name).isEqualTo("Mysz");
    }

    @Test
    void floatingPointPricesAddUpToTheGrosz() {
        ReceiptRequestSnapshot snapshot = converted(b2cOrder(99.99), items(item("Kabel", 3, 33.33, 1.23)));

        assertThat(snapshot.toRequest(KEY).totalGross().grosze()).isEqualTo(9999);
    }

    @Test
    void negativeLinesBlockTheAttempt() {
        assertThat(blocked(b2cOrder(90.00), items(item("Mysz", 1, 100.00, 1.23), item("Rabat", 1, -10.00, 1.23))).reason())
                .isEqualTo(ReceiptBlockReason.NEGATIVE_LINE);
    }

    @Test
    void unknownVatBlocksTheAttempt() {
        assertThat(blocked(b2cOrder(100.00), items(item("Mysz", 1, 100.00, 1.07))).reason())
                .isEqualTo(ReceiptBlockReason.UNKNOWN_VAT);
    }

    @Test
    void itemsThatDoNotAddUpToTheOrderTotalBlockTheAttempt() {
        assertThat(blocked(b2cOrder(150.00), items(item("Mysz", 1, 100.00, 1.23))).reason())
                .isEqualTo(ReceiptBlockReason.TOTAL_MISMATCH);
    }

    @Test
    void missingBuyerEmailBlocksWhenTheProviderNeedsIt() {
        Order order = b2cOrder(100.00);
        order.getBillingDetails().setEmail(null);
        order.setEmail(null);

        assertThat(blocked(order, items(item("Mysz", 1, 100.00, 1.23))).reason())
                .isEqualTo(ReceiptBlockReason.MISSING_EMAIL);
    }

    @Test
    void mixedPaymentFormsThatDoNotAddUpToTheTotalBlock() {
        // Two settled forms whose amounts fall short of the total: unlike a zero-amount placeholder, this is not
        // something the converter can safely ignore, so it blocks rather than guessing.
        Order order = order(100.00, payment(PaymentSource.BankTransfer, 60.00), payment(PaymentSource.Card, 10.00));

        assertThat(blocked(order, items(item("Mysz", 1, 100.00, 1.23))).reason())
                .isEqualTo(ReceiptBlockReason.MIXED_PAYMENTS);
    }

    @Test
    void mixedPaymentFormsWithAmountsThatAddUpSplitThePayment() {
        Order order = b2cOrder(100.00);
        order.getPayments().clear();
        order.getPayments().add(new Payment("a", "przelew", PaymentSource.BankTransfer, 60.00, 0));
        order.getPayments().add(new Payment("b", "karta", PaymentSource.Card, 40.00, 0));

        assertThat(converted(order, items(item("Mysz", 1, 100.00, 1.23))).payments())
                .extracting(ReceiptRequestSnapshot.Pay::form, ReceiptRequestSnapshot.Pay::grosze)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(PaymentForm.TRANSFER, 6000L),
                        org.assertj.core.groups.Tuple.tuple(PaymentForm.CARD, 4000L));
    }

    @Test
    void refundsAreNotPaymentForms() {
        Order order = b2cOrder(100.00);
        Payment refund = new Payment("r", "zwrot", PaymentSource.Cash, 10.00, 0);
        refund.setDirection(PaymentDirection.Outgoing);
        order.getPayments().add(refund);

        assertThat(converted(order, items(item("Mysz", 1, 100.00, 1.23))).payments())
                .singleElement().extracting(ReceiptRequestSnapshot.Pay::form).isEqualTo(PaymentForm.TRANSFER);
    }

    @Test
    void cashOnDeliveryIsCashAndDirectDebitIsTransfer() {
        Order cod = order(100.00, payment(PaymentSource.CashOnDelivery, 100.00));
        Order debit = order(100.00, payment(PaymentSource.DirectDebit, 100.00));

        assertThat(converted(cod, items(item("Mysz", 1, 100.00, 1.23))).payments().get(0).form()).isEqualTo(PaymentForm.CASH);
        assertThat(converted(debit, items(item("Mysz", 1, 100.00, 1.23))).payments().get(0).form()).isEqualTo(PaymentForm.TRANSFER);
    }

    @Test
    void namesAreCutToTheProviderLengthKeepingTheShippingRateUpFront() {
        provider.lineNameLength = 20;

        ReceiptRequestSnapshot snapshot = converted(b2cOrder(115.99),
                items(item("Bardzo długa nazwa laptopa gamingowego", 1, 100.00, 1.23),
                        delivery("Dostawa – Kurier DPD Pickup", 15.99, 1.08)));

        assertThat(snapshot.lines().get(0).name()).hasSizeLessThanOrEqualTo(20);
        assertThat(snapshot.lines().get(1).name()).startsWith("Dostawa 8% ").hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void returnedItemsAreNotSold() {
        pl.commercelink.orders.OrderItem returned = item("Mysz", 1, 50.00, 1.23);
        returned.setStatus(pl.commercelink.orders.FulfilmentStatus.Returned);

        ReceiptRequestSnapshot snapshot = converted(b2cOrder(100.00), items(item("Klawiatura", 1, 100.00, 1.23), returned));

        assertThat(snapshot.lines()).extracting(ReceiptRequestSnapshot.Line::name).containsExactly("Klawiatura");
    }

    @Test
    void providerWithoutElectronicReceiptsBlocks() {
        FakeReceiptProvider paperOnly = new FakeReceiptProvider() {
            @Override
            public java.util.Set<pl.commercelink.receipts.api.ReceiptMedium> supportedMedia() {
                return java.util.Set.of();
            }
        };

        assertThat(((ReceiptConversion.Blocked) converter.convert(b2cOrder(100.00),
                items(item("Mysz", 1, 100.00, 1.23)), KEY, paperOnly, NOW)).reason())
                .isEqualTo(ReceiptBlockReason.MEDIUM_UNSUPPORTED);
    }

    @Test
    void zeroPlaceholderPaymentOfAnotherFormIsIgnored() {
        // Card 100.00 settled + an unsettled 0.00 BankTransfer placeholder -> one CARD payment of the whole total
        Order order = order(100.00, payment(PaymentSource.Card, 100.00), payment(PaymentSource.BankTransfer, 0.00));

        assertThat(converted(order, items(item("Mysz", 1, 100.00, 1.23))).payments())
                .containsExactly(new ReceiptRequestSnapshot.Pay(PaymentForm.CARD, 10000, null));
    }

    @Test
    void quantityIsCarriedAsDecimal() {
        assertThat(converted(b2cOrder(30.00), items(item("Kabel", 3, 10.00, 1.23))).lines().get(0).quantity())
                .isEqualByComparingTo(new BigDecimal("3"));
    }
}
