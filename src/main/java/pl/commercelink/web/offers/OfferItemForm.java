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
    private String mfn;
    private double unitCost;
    private boolean consolidated;

    public static OfferItemForm from(BasketItem item) {
        OfferItemForm form = new OfferItemForm();
        form.name = item.getName();
        form.category = item.getCategory();
        form.mfn = item.getMfn();
        form.unitCost = item.getUnitCost();
        form.consolidated = item.isConsolidated();
        return form;
    }

    /** The same completeness the table save enforces by dropping an item; here the user is told instead. */
    public Map<String, String> validate(BasketItem item) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (StringUtils.isBlank(name)) {
            errors.put("name", "offers.details.item.name.required");
        }
        if (StringUtils.isBlank(mfn)) {
            errors.put("mfn", "offers.details.item.mfn.required");
        }
        if (!item.isService() && StringUtils.isBlank(category)) {
            errors.put("category", "offers.details.item.category.required");
        }
        return errors;
    }

    void applyTo(BasketItem item) {
        item.setName(name);
        item.setCategory(StringUtils.isBlank(category) ? null : category);
        item.setMfn(mfn);
        item.setUnitCost(unitCost);
        item.setConsolidated(consolidated);
    }
}
