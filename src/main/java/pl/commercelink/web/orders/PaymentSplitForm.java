package pl.commercelink.web.orders;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * "Podziel" of a pending payment: parts with their methods and amounts, and the method of the last part, which waits
 * for what the parts leave of the split payment (Order.split). methods and amounts are the parts' fields in order.
 */
@Getter
@Setter
public class PaymentSplitForm {

    private String version;
    private List<PaymentSource> methods = new ArrayList<>();
    private List<String> amounts = new ArrayList<>();
    private PaymentSource restMethod;

    /** Why the payment cannot be split so, as a message key, or null. */
    public String validate(Order order, Payment pending) {
        if (methods.isEmpty() || methods.size() != amounts.size() || restMethod == null
                || methods.stream().anyMatch(method -> method == null)) {
            return "order.payments.split.error.parts";
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (String amount : amounts) {
            BigDecimal typed = AmountParser.parse(amount);
            if (typed == null || typed.signum() <= 0) {
                return "order.payments.split.error.amount";
            }
            if (!AmountParser.inRange(typed)) {
                return "order.payments.error.range";
            }
            sum = sum.add(typed);
        }
        return BigDecimal.valueOf(order.expectedAmountOf(pending)).subtract(sum).compareTo(new BigDecimal("0.005")) < 0
                ? "order.payments.split.error.over" : null;
    }

    /** The parts as pending payments with their own amounts; call only after {@link #validate} found nothing. */
    public List<Payment> parts() {
        List<Payment> parts = new ArrayList<>();
        for (int i = 0; i < methods.size(); i++) {
            Payment part = new Payment(methods.get(i));
            part.setExpectedAmount(AmountParser.parse(amounts.get(i)).doubleValue());
            parts.add(part);
        }
        return parts;
    }
}
