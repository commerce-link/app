package pl.commercelink.web.orders;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;

/**
 * Binds a money amount typed into a text field to a double through {@link AmountParser}: "149,99" and "1 499,99" as
 * well as "149.99", a blank field as 0. Anything else is a binding error (IllegalArgumentException), which the caller
 * reads from its BindingResult.
 */
public class AmountEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        BigDecimal amount = AmountParser.parse(text);
        if (amount == null || !AmountParser.inRange(amount)) {
            throw new IllegalArgumentException("Not an amount: " + text);
        }
        setValue(amount.doubleValue());
    }

    @Override
    public String getAsText() {
        Object value = getValue();
        return value == null ? "" : BigDecimal.valueOf((Double) value).toPlainString();
    }
}
