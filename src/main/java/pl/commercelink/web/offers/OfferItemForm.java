package pl.commercelink.web.offers;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.baskets.BasketItem;

import java.util.LinkedHashMap;
import java.util.Map;

/** One item edited on its own page (the way without JavaScript). Quantity and price stay as in the table. */
@Getter
@Setter
public class OfferItemForm {

    private String name;
    private String category;
    private String sku;
    private Double unitCost = 0.0;
    private boolean consolidated;

    /** A cleared cost posts "", which a primitive would refuse with a 400; the table counts it as 0 as well. */
    public void setUnitCost(Double unitCost) {
        this.unitCost = unitCost == null ? 0.0 : unitCost;
    }

    public static OfferItemForm from(BasketItem item) {
        OfferItemForm form = new OfferItemForm();
        form.name = item.getName();
        form.category = item.getCategory();
        form.sku = item.getSku();
        form.unitCost = item.getUnitCost();
        form.consolidated = item.isConsolidated();
        return form;
    }

    /**
     * What the table save would drop as incomplete, told to the user instead. A blank category is not among it: the
     * table posts it as "", which passes BasketItem.isComplete before it is stored as null.
     */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (StringUtils.isBlank(name)) {
            errors.put("name", "offers.details.item.name.required");
        }
        if (StringUtils.isBlank(sku)) {
            errors.put("sku", "offers.details.item.sku.required");
        }
        return errors;
    }

    void applyTo(BasketItem item) {
        item.setName(name);
        item.setCategory(StringUtils.isBlank(category) ? null : category);
        item.setSku(sku);
        item.setUnitCost(unitCost);
        item.setConsolidated(consolidated);
    }
}
