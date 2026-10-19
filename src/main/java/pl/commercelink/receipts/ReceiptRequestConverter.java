package pl.commercelink.receipts;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.receipts.api.LineKind;
import pl.commercelink.receipts.api.Money;
import pl.commercelink.receipts.api.PaymentForm;
import pl.commercelink.receipts.api.ReceiptLineNames;
import pl.commercelink.receipts.api.ReceiptMedium;
import pl.commercelink.receipts.api.ReceiptProvider;
import pl.commercelink.receipts.api.ReceiptValidationException;
import pl.commercelink.receipts.api.VatRate;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Turns a delivered consumer order into the receipt request of one attempt. Anything it cannot map with certainty
 * blocks the attempt for the operator instead of guessing: a wrong receipt is a wrong fiscal record.
 */
@Component
public class ReceiptRequestConverter {

    private static final Pattern LEADING_SHIPPING_WORD = Pattern.compile("^(?i)dostawa\\s*[-–—:]*\\s*");
    private static final long ONE_GROSZ = 1;

    /** {@code storeEmail} is the store's own e-mail, never a point-of-sale buyer's (see {@link BuyerEmail}). */
    public ReceiptConversion convert(Order order, List<OrderItem> items, String receiptKey, ReceiptProvider provider,
                                     LocalDateTime fallbackSaleDate, String storeEmail) {
        if (!provider.supportedMedia().contains(ReceiptMedium.ELECTRONIC)) {
            return new ReceiptConversion.Blocked(ReceiptBlockReason.MEDIUM_UNSUPPORTED, null);
        }
        int maxName = provider.maxLineNameLength();
        List<ReceiptRequestSnapshot.Line> lines = new ArrayList<>();
        long linesTotal = 0;
        for (OrderItem item : items) {
            if (item.getQty() <= 0 || item.isReturned() || item.getPrice() == 0) {
                continue;
            }
            if (item.getPrice() < 0) {
                return new ReceiptConversion.Blocked(ReceiptBlockReason.NEGATIVE_LINE, item.getName());
            }
            VatRate rate;
            try {
                rate = VatRate.fromMultiplier(item.getTax());
            } catch (IllegalArgumentException e) {
                return new ReceiptConversion.Blocked(ReceiptBlockReason.UNKNOWN_VAT, item.getName() + " (" + item.getTax() + ")");
            }
            long unit = Money.of(BigDecimal.valueOf(item.getPrice())).grosze();
            BigDecimal quantity = BigDecimal.valueOf(item.getQty());
            boolean shipping = OrderItem.DELIVERY_CATEGORY.equals(item.getCategory());
            LineKind kind = shipping && item.getQty() == 1 ? LineKind.SHIPPING
                    : shipping || item.isService() ? LineKind.SERVICE : LineKind.GOODS;
            String name = ReceiptLineNames.normalize(shipping ? shippingName(item.getName(), rate) : item.getName(), maxName);
            if (name.isBlank()) {
                return new ReceiptConversion.Blocked(ReceiptBlockReason.EMPTY_NAME, item.getName());
            }
            lines.add(new ReceiptRequestSnapshot.Line(kind, name, quantity, unit, rate,
                    kind == LineKind.GOODS ? item.getSku() : null, kind == LineKind.GOODS ? item.getEan() : null));
            linesTotal += Money.ofGrosze(unit).times(quantity).grosze();
        }
        if (lines.isEmpty()) {
            return new ReceiptConversion.Blocked(ReceiptBlockReason.NO_LINES, null);
        }
        long orderTotal = Money.of(BigDecimal.valueOf(order.getTotalPrice())).grosze();
        if (Math.abs(orderTotal - linesTotal) > ONE_GROSZ) {
            return new ReceiptConversion.Blocked(ReceiptBlockReason.TOTAL_MISMATCH,
                    Money.ofGrosze(linesTotal).toBigDecimal() + " ≠ " + Money.ofGrosze(orderTotal).toBigDecimal());
        }
        String email = BuyerEmail.of(order, storeEmail);
        // the second line of defence: the order page greys the actions and ReceiptAttemptService refuses them for the
        // same predicate, but an automatic attempt (delivery) still comes this way
        if (blocksPosWithoutCustomerEmail(order, storeEmail)) {
            return new ReceiptConversion.Blocked(ReceiptBlockReason.POS_NO_CUSTOMER_EMAIL, null);
        }
        if (provider.requiresBuyerEmail() && email == null) {
            return new ReceiptConversion.Blocked(ReceiptBlockReason.MISSING_EMAIL, null);
        }
        List<Payment> paid = order.getPayments().stream()
                .filter(p -> p.getDirection() != PaymentDirection.Outgoing)
                .filter(p -> !p.isUnsettled())
                .toList();
        List<ReceiptRequestSnapshot.Pay> payments = payments(paid, linesTotal);
        if (payments == null) {
            return new ReceiptConversion.Blocked(ReceiptBlockReason.MIXED_PAYMENTS, null);
        }
        ReceiptRequestSnapshot snapshot = new ReceiptRequestSnapshot(order.getOrderId(),
                saleDate(order, fallbackSaleDate), email, List.copyOf(lines), payments);
        try {
            snapshot.toRequest(receiptKey);
        } catch (ReceiptValidationException e) {
            return new ReceiptConversion.Blocked(ReceiptBlockReason.INVALID_REQUEST, e.getMessage());
        }
        return new ReceiptConversion.Converted(snapshot);
    }

    /**
     * A sale at the counter without the customer's own e-mail may already have its receipt from the shop's cash
     * register: whatever the provider accepts, issuing now could register the sale twice. The one predicate behind the
     * blocked attempt, the greyed "Wystaw ponownie" / "E-paragon" of the order page and the server's refusal of both.
     * {@code storeEmail} is the store's own e-mail (a walk-in buyer copies it, see {@link BuyerEmail}).
     */
    public static boolean blocksPosWithoutCustomerEmail(Order order, String storeEmail) {
        return order.isPointOfSale() && BuyerEmail.of(order, storeEmail) == null;
    }

    /** {@link #blocksPosWithoutCustomerEmail(Order, String)} with the store's own e-mail read from the store. */
    public static boolean blocksPosWithoutCustomerEmail(Order order, Store store) {
        return blocksPosWithoutCustomerEmail(order,
                store == null || store.getBillingDetails() == null ? null : store.getBillingDetails().getEmail());
    }

    /**
     * Printers refuse a name previously sold at a lower VAT rate, so the rate is part of the shipping name, at the
     * start, where cutting the name to the printer's length cannot remove it.
     */
    static String shippingName(String optionName, VatRate rate) {
        String rest = optionName == null ? "" : LEADING_SHIPPING_WORD.matcher(optionName.strip()).replaceFirst("");
        return ("Dostawa " + ratePercent(rate) + "% " + rest).strip();
    }

    private static String ratePercent(VatRate rate) {
        return switch (rate) {
            case VAT_23 -> "23";
            case VAT_8 -> "8";
            case VAT_5 -> "5";
            case VAT_0 -> "0";
            case EXEMPT -> "zw";
        };
    }

    /**
     * What the buyer has actually paid, for the receipt. Nothing paid yet gives no payments: a fiscal receipt does not
     * need a payment form, and the provider then marks nothing as paid. The method chosen at checkout (the unsettled
     * placeholder payment, amount 0) is deliberately not used — declaring it would record a payment that has not
     * happened. A partly paid order declares what was paid, by form; a fully paid one the whole total.
     */
    private static List<ReceiptRequestSnapshot.Pay> payments(List<Payment> paid, long total) {
        if (paid.isEmpty()) {
            return List.of();
        }
        long paidTotal = paid.stream().mapToLong(p -> Money.of(BigDecimal.valueOf(p.getAppliedAmount())).grosze()).sum();
        if (paidTotal >= total - ONE_GROSZ) {
            return settledPayments(paid, total);
        }
        Map<PaymentForm, Long> byForm = new EnumMap<>(PaymentForm.class);
        for (Payment payment : paid) {
            long amount = Money.of(BigDecimal.valueOf(payment.getAppliedAmount())).grosze();
            if (amount <= 0) {
                return null;
            }
            byForm.merge(form(payment.getSource()), amount, Long::sum);
        }
        String label = byForm.size() == 1
                ? paid.stream().map(Payment::getName).filter(StringUtils::isNotBlank).findFirst().orElse(null)
                : null;
        return byForm.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ReceiptRequestSnapshot.Pay(e.getKey(), e.getValue(), label))
                .toList();
    }

    /** One form covers the whole total; several forms only with positive amounts that add up to it. */
    private static List<ReceiptRequestSnapshot.Pay> settledPayments(List<Payment> incoming, long total) {
        List<PaymentForm> forms = incoming.stream().map(p -> form(p.getSource())).distinct().toList();
        if (forms.size() == 1) {
            String label = incoming.stream().map(Payment::getName).filter(StringUtils::isNotBlank).findFirst().orElse(null);
            return List.of(new ReceiptRequestSnapshot.Pay(forms.get(0), total, label));
        }
        Map<PaymentForm, Long> byForm = new EnumMap<>(PaymentForm.class);
        for (Payment payment : incoming) {
            long amount = Money.of(BigDecimal.valueOf(payment.getAppliedAmount())).grosze();
            if (amount <= 0) {
                return null;
            }
            byForm.merge(form(payment.getSource()), amount, Long::sum);
        }
        long sum = byForm.values().stream().mapToLong(Long::longValue).sum();
        if (sum != total) {
            return null;
        }
        return byForm.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ReceiptRequestSnapshot.Pay(e.getKey(), e.getValue(), null))
                .toList();
    }

    static PaymentForm form(PaymentSource source) {
        if (source == null) {
            return PaymentForm.OTHER;
        }
        return switch (source) {
            case BankTransfer, OnlinePayment, DirectDebit -> PaymentForm.TRANSFER;
            case Card -> PaymentForm.CARD;
            case Cash, CashOnDelivery -> PaymentForm.CASH;
            case Installments -> PaymentForm.CREDIT;
        };
    }

    private static LocalDateTime saleDate(Order order, LocalDateTime fallback) {
        return order.getShipments().stream()
                .map(Shipment::getDeliveredAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(fallback);
    }
}
