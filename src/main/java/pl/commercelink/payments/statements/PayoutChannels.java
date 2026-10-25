package pl.commercelink.payments.statements;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.PayoutChannel;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Which payout rows an operation comes from and which orders such a row settles. */
public final class PayoutChannels {

    private PayoutChannels() {
    }

    /**
     * The rows of the payer that sent the operation: every row whose text is in its sender or title, ignoring case and
     * punctuation. Empty for a transfer from a customer.
     */
    public static List<PayoutChannel> payer(BankOperation operation, List<PayoutChannel> channels) {
        String sender = compact(operation.sender());
        String title = compact(operation.title());
        return channels.stream()
                .filter(channel -> StringUtils.isNotBlank(compact(channel.getMatch())))
                .filter(channel -> sender.contains(compact(channel.getMatch())) || title.contains(compact(channel.getMatch())))
                .toList();
    }

    /** The first of the payer's rows that describes the order, so its fees are counted the way that row says. */
    public static Optional<PayoutChannel> rowOf(List<PayoutChannel> rows, Order order) {
        return rows.stream().filter(row -> pays(row, order)).findFirst();
    }

    /**
     * Whether the row describes the order: its source (the shop or a marketplace) and the method its payment waits
     * for. A criterion left empty takes any order.
     */
    public static boolean pays(PayoutChannel row, Order order) {
        Payment payment = paymentOf(order);
        boolean marketplace = order.getSource() != null && order.getSource().getType() == OrderSourceType.Marketplace;
        String source = StringUtils.trimToNull(row.getOrderSource());
        if (source != null) {
            boolean sourceMatches = PayoutChannel.SHOP.equals(source) ? !marketplace
                    : marketplace && StringUtils.equalsIgnoreCase(order.getSource().getName(), source);
            if (!sourceMatches) {
                return false;
            }
        }
        return row.getMethod() == null || (payment != null && payment.getSource() == row.getMethod());
    }

    /** What the payer transfers for an order that still owes remaining: less the fee it keeps, when it keeps one. */
    public static BigDecimal payout(PayoutChannel row, BigDecimal remaining, Payment pending) {
        return remaining.subtract(fee(row, pending));
    }

    public static BigDecimal fee(PayoutChannel row, Payment pending) {
        return row != null && row.isNetOfFees() && pending != null
                ? StatementAmounts.of(pending.getFee()) : BigDecimal.ZERO.setScale(2);
    }

    /** The method of a payment booked from a payout on an order that has no pending payment to fill. */
    public static PaymentSource sourceOf(PayoutChannel row) {
        return row != null && row.getMethod() != null ? row.getMethod() : PaymentSource.OnlinePayment;
    }

    /** The payment the order waits for, else its latest one: what the customer chose to pay with. */
    static Payment paymentOf(Order order) {
        Payment pending = order.getPendingPayment();
        return pending != null ? pending : order.getLatestPayment();
    }

    /** Upper case Latin letters and digits only: "PayPro S.A." and "PAYPRO SA" are the same payer. */
    static String compact(String text) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
