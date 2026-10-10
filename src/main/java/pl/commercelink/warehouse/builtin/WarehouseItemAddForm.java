package pl.commercelink.warehouse.builtin;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** The "Add item" form: every field as typed, so a refused form comes back exactly as the operator left it. */
@Getter
@Setter
public class WarehouseItemAddForm {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");

    private String manufacturerCode;
    private String cost;
    private String priceType = "net";
    private String qty = "1";
    private String status = FulfilmentStatus.New.name();
    private String supplier;
    private String customSupplier;
    private String name;
    private String ean;
    private String category;
    /** The product data group was on the page this form was sent from, so empty name and EAN are errors now. */
    private boolean productDataShown;

    public Map<String, String> validate(boolean productDataNeeded) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (StringUtils.isBlank(manufacturerCode)) errors.put("manufacturerCode", "warehouse.item.new.error.mfn");
        if (amount() == null || amount() < 0) errors.put("cost", "warehouse.item.new.error.cost");
        if (quantity() < 1) errors.put("qty", "warehouse.item.new.error.qty");
        if (statusValue() == null) errors.put("status", "warehouse.item.new.error.status");
        if (productDataNeeded && StringUtils.isBlank(name)) errors.put("name", "warehouse.item.new.error.name");
        if (productDataNeeded && StringUtils.isBlank(ean)) errors.put("ean", "warehouse.item.new.error.ean");
        return errors;
    }

    /** The net unit cost; a gross price is converted at the default VAT rate, as the old modal did in the browser. */
    public double netCost() {
        double value = amount();
        return "gross".equals(priceType) ? Price.fromGross(value).netValue() : value;
    }

    public int quantity() {
        try {
            return Integer.parseInt(StringUtils.trimToEmpty(qty));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public FulfilmentStatus statusValue() {
        return WarehouseItemController.NEW_ITEM_STATUSES.stream()
                .filter(s -> s.name().equals(status))
                .findFirst()
                .orElse(null);
    }

    private Double amount() {
        String digits = StringUtils.deleteWhitespace(StringUtils.defaultString(cost).replace('\u00a0', ' ')).replace(',', '.');
        // plain decimals only: parseDouble would also take NaN, Infinity, exponents and hex floats
        if (!PLAIN_DECIMAL.matcher(digits).matches()) {
            return null;
        }
        double value = Double.parseDouble(digits);
        return Double.isFinite(value) ? value : null;
    }
}
