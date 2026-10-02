package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.web.orders.AmountParser;
import pl.commercelink.web.orders.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The delivery header edited in the "Terminy i koszty" and "Komentarz" dialogs. Every field is text, so a value the
 * browser cannot turn into a number ("19,90", "abc") comes back as an error at the field instead of an HTTP 400. VAT is
 * typed as a percentage and stored as the multiplier the delivery keeps (23 -> 1.23). The store is never part of the
 * form: the controller takes it from the session or the path.
 */
@Getter
@Setter
@NoArgsConstructor
public class DeliveryTermsForm {

    public static final String TERMS = "terms";
    public static final String COMMENT = "comment";
    private static final int MAX_PAYMENT_TERMS = 365;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private String source = TERMS;
    private String deliveryId;
    private String estimatedDeliveryAt;
    private String paymentTerms;
    private String shippingCost;
    private String paymentCost;
    private String vat;
    private String comment;

    public static DeliveryTermsForm of(Delivery delivery) {
        DeliveryTermsForm form = new DeliveryTermsForm();
        form.deliveryId = delivery.getDeliveryId();
        form.estimatedDeliveryAt = delivery.getEstimatedDeliveryAt() == null ? "" : delivery.getEstimatedDeliveryAt().toString();
        form.paymentTerms = String.valueOf(delivery.getPaymentTerms());
        form.shippingCost = Money.input(delivery.getShippingCost());
        form.paymentCost = Money.input(delivery.getPaymentCost());
        form.vat = vatPercent(delivery.getTax());
        form.comment = delivery.getComment();
        return form;
    }

    /** An unset VAT (below 1.0, e.g. 0.0 on deliveries created from a purchase) is an empty field, not "-100". */
    public static String vatPercent(double tax) {
        if (tax < 1.0) {
            return "";
        }
        return BigDecimal.valueOf(tax).subtract(BigDecimal.ONE).movePointRight(2)
                .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public boolean isComment() {
        return COMMENT.equals(source);
    }

    public Map<String, String> validate(boolean dateRequired) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (StringUtils.isBlank(estimatedDeliveryAt) ? dateRequired : date() == null) {
            errors.put("estimatedDeliveryAt", "deliveries.details.terms.error.date");
        }
        Integer terms = terms();
        if (terms == null || terms < 0 || terms > MAX_PAYMENT_TERMS) {
            errors.put("paymentTerms", "deliveries.details.terms.error.paymentTerms");
        }
        if (!isCost(shippingCost)) {
            errors.put("shippingCost", "deliveries.details.terms.error.shippingCost");
        }
        if (!isCost(paymentCost)) {
            errors.put("paymentCost", "deliveries.details.terms.error.paymentCost");
        }
        BigDecimal percent = StringUtils.isBlank(vat) ? null : AmountParser.parse(vat);
        if (percent == null || percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
            errors.put("vat", "deliveries.details.terms.error.vat");
        }
        return errors;
    }

    public Delivery toDelivery(String storeId) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(storeId);
        delivery.setDeliveryId(deliveryId);
        delivery.setEstimatedDeliveryAt(StringUtils.isBlank(estimatedDeliveryAt) ? null : date());
        delivery.setPaymentTerms(terms());
        delivery.setShippingCost(AmountParser.parse(shippingCost).doubleValue());
        delivery.setPaymentCost(AmountParser.parse(paymentCost).doubleValue());
        delivery.setTax(BigDecimal.ONE.add(AmountParser.parse(vat).movePointLeft(2)).doubleValue());
        delivery.setComment(StringUtils.isBlank(comment) ? null : comment);
        return delivery;
    }

    private LocalDate date() {
        try {
            return LocalDate.parse(estimatedDeliveryAt.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer terms() {
        try {
            return StringUtils.isBlank(paymentTerms) ? null : Integer.valueOf(paymentTerms.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isCost(String value) {
        BigDecimal amount = AmountParser.parse(value);
        return amount != null && amount.signum() >= 0 && AmountParser.inRange(amount);
    }
}
