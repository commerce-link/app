package pl.commercelink.web.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The pending payments of an order as JSON, for the choice of payment in the "Dodaj wpłatę" dialog
 * (add-payment-dialog.js): each with its index among the order's payments (AddPaymentForm.target), its method, whether
 * it waits for the rest, what it waits for and the fields the dialog fills in from it. Amounts are plain, with a dot.
 */
public final class PendingPaymentsJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PendingPaymentsJson() {
    }

    public static String of(Order order) {
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Payment> payments = order.getPayments();
        for (int index = 0; index < payments.size(); index++) {
            Payment payment = payments.get(index);
            if (!payment.isUnsettled()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            item.put("source", payment.getSource() == null ? "" : payment.getSource().name());
            item.put("rest", payment.waitsForRest());
            item.put("expected", plain(order.expectedAmountOf(payment)));
            item.put("name", StringUtils.defaultString(payment.getName()));
            item.put("referenceNo", StringUtils.defaultString(payment.getReferenceNo()));
            item.put("fee", plain(payment.getFee()));
            pending.add(item);
        }
        try {
            return JSON.writeValueAsString(pending);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String plain(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
