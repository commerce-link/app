package pl.commercelink.warehouse.builtin;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The item page's form, every field as typed. The server decides which fields it takes from the status the item has in
 * the store, never from the form (spec §4.3): a destroyed item is an archive record and takes only the comment; a New
 * item has no document yet, so everything stays correctable; any other item keeps its quantity, cost and status, which
 * only documents change.
 */
@Getter
@Setter
public class WarehouseItemEditForm {

    static final int MAX_COMMENT = 2000;
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");

    private String name;
    private String category;
    private String condition;
    private String serialNo;
    private String unitSystemCost;
    private String comment;
    private String qty;
    private String cost;
    private String tax;
    private String ean;
    private String manufacturerCode;
    private String deliveryId;
    private String status;
    private String back;

    public static WarehouseItemEditForm of(WarehouseItem item) {
        WarehouseItemEditForm form = new WarehouseItemEditForm();
        form.name = item.getName();
        form.category = item.getCategory();
        form.condition = item.getCondition() == null ? ItemCondition.Sealed.name() : item.getCondition().name();
        form.serialNo = item.getSerialNo();
        form.unitSystemCost = item.hasUnitSystemCost() ? decimal(item.getUnitSystemCost()) : "";
        form.comment = item.getComment();
        form.qty = String.valueOf(item.getQty());
        form.cost = decimal(item.getCost());
        form.tax = String.valueOf(Math.round((item.getTax() - 1) * 100));
        form.ean = item.getEan();
        form.manufacturerCode = item.getManufacturerCode();
        form.deliveryId = item.getDeliveryId();
        form.status = item.getStatus().name();
        return form;
    }

    public Map<String, String> validate(FulfilmentStatus stored) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (StringUtils.length(comment) > MAX_COMMENT) errors.put("comment", "warehouse.item.error.comment");
        if (stored == FulfilmentStatus.Destroyed) {
            return errors;
        }
        if (StringUtils.isBlank(name)) errors.put("name", "warehouse.item.error.name");
        if (conditionValue() == null) errors.put("condition", "warehouse.item.error.condition");
        if (StringUtils.isNotBlank(unitSystemCost) && amount(unitSystemCost) == null) errors.put("unitSystemCost", "warehouse.item.error.systemCost");
        if (stored == FulfilmentStatus.New) {
            if (quantity() < 1) errors.put("qty", "warehouse.item.error.qty");
            if (amount(cost) == null) errors.put("cost", "warehouse.item.error.cost");
            Double percent = amount(tax);
            if (percent == null || percent > 100) errors.put("tax", "warehouse.item.error.tax");
            if (StringUtils.isBlank(ean)) errors.put("ean", "warehouse.item.error.ean");
            if (StringUtils.isBlank(manufacturerCode)) errors.put("manufacturerCode", "warehouse.item.error.mfn");
            if (statusValue() == null) errors.put("status", "warehouse.item.error.status");
        }
        return errors;
    }

    /** A copy of the stored item with the fields this status allows; call only after validate() returned no errors. */
    public WarehouseItem toUpdate(WarehouseItem stored) {
        WarehouseItem updated = copyOf(stored);
        updated.setComment(StringUtils.trimToNull(comment));
        if (stored.getStatus() == FulfilmentStatus.Destroyed) {
            return updated;
        }
        updated.setName(name.trim());
        updated.setCategory(StringUtils.defaultIfBlank(category, stored.getCategory()));
        updated.setCondition(conditionValue());
        updated.setSerialNo(StringUtils.trimToNull(serialNo));
        // "no system cost" is stored as 0 (hasUnitSystemCost() is unitSystemCost > 0)
        updated.setUnitSystemCost(StringUtils.isBlank(unitSystemCost) ? 0 : amount(unitSystemCost));
        if (stored.getStatus() == FulfilmentStatus.New) {
            updated.setQty(quantity());
            updated.setCost(amount(cost));
            updated.setTax(1 + amount(tax) / 100);
            updated.setEan(ean.trim());
            updated.setManufacturerCode(manufacturerCode.trim());
            updated.setDeliveryId(StringUtils.trimToNull(deliveryId));
            updated.setStatus(statusValue());
        }
        return updated;
    }

    private static WarehouseItem copyOf(WarehouseItem stored) {
        WarehouseItem copy = new WarehouseItem();
        copy.setStoreId(stored.getStoreId());
        copy.setItemId(stored.getItemId());
        copy.setCategory(stored.getCategory());
        copy.setName(stored.getName());
        copy.setQty(stored.getQty());
        copy.setComment(stored.getComment());
        copy.setEan(stored.getEan());
        copy.setManufacturerCode(stored.getManufacturerCode());
        copy.setCost(stored.getCost());
        copy.setTax(stored.getTax());
        copy.setDeliveryId(stored.getDeliveryId());
        copy.setClaimedDeliveryId(stored.getClaimedDeliveryId());
        copy.setSerialNo(stored.getSerialNo());
        copy.setCondition(stored.getCondition());
        copy.setStatus(stored.getStatus());
        copy.setDestroyedAt(stored.getDestroyedAt());
        copy.setUnitSystemCost(stored.getUnitSystemCost());
        return copy;
    }

    int quantity() {
        try {
            return Integer.parseInt(StringUtils.trimToEmpty(qty));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    FulfilmentStatus statusValue() {
        return WarehouseItemController.NEW_ITEM_STATUSES.stream().filter(s -> s.name().equals(status)).findFirst().orElse(null);
    }

    ItemCondition conditionValue() {
        return Arrays.stream(ItemCondition.values()).filter(c -> c.name().equals(condition)).findFirst().orElse(null);
    }

    private static Double amount(String text) {
        // non-breaking spaces come from copy-pasted thousands separators
        String digits = StringUtils.deleteWhitespace(StringUtils.defaultString(text).replace(' ', ' ')).replace(',', '.');
        // plain decimals only: parseDouble would also take NaN, Infinity, exponents and hex floats
        if (!PLAIN_DECIMAL.matcher(digits).matches()) {
            return null;
        }
        double value = Double.parseDouble(digits);
        return Double.isFinite(value) ? value : null;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
    }
}
