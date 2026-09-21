package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.stores.DeliveryOption;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A delivery option customers choose in an offer or at the online-store checkout. The price is the gross amount in
 * złoty, read as text so "19,99" is accepted and an empty value gets a message at the field.
 */
@Getter
@Setter
public class DeliveryOptionForm {

    private static final Pattern PRICE = Pattern.compile("\\d{1,6}([.,]\\d{1,2})?");

    private String name;
    private String description;
    private String price;
    private String type;

    public static DeliveryOptionForm empty() {
        DeliveryOptionForm form = new DeliveryOptionForm();
        form.type = ShipmentType.Courier.name();
        return form;
    }

    public static DeliveryOptionForm from(DeliveryOption option) {
        DeliveryOptionForm form = new DeliveryOptionForm();
        form.name = option.getName();
        form.description = option.getDescription();
        form.price = BigDecimal.valueOf(option.getPrice()).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        form.type = option.getType() != null ? option.getType().name() : ShipmentType.Courier.name();
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "name", name, "store.payments.delivery.name.required");
        if (FormRules.requireText(errors, "price", price, "store.payments.delivery.price.required")
                && !PRICE.matcher(price.trim()).matches()) {
            errors.put("price", "store.payments.delivery.price.invalid");
        }
        if (shipmentType() == null) {
            errors.put("type", "store.payments.delivery.type.required");
        }
        return errors;
    }

    public DeliveryOption toNewDeliveryOption() {
        DeliveryOption option = new DeliveryOption();
        applyTo(option);
        return option;
    }

    /** Edits the option in place; its id stays, so offers that chose it follow the change. */
    public void applyTo(DeliveryOption option) {
        option.setName(name.trim());
        option.setDescription(StringUtils.trimToNull(description));
        option.setPrice(new BigDecimal(price.trim().replace(',', '.')).doubleValue());
        option.setType(shipmentType());
    }

    private ShipmentType shipmentType() {
        for (ShipmentType candidate : ShipmentType.values()) {
            if (candidate.name().equals(type)) {
                return candidate;
            }
        }
        return null;
    }
}
