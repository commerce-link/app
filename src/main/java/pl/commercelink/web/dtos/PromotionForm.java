package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.TargetValue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class PromotionForm {

    private String name;
    private String conditionTarget;
    private List<String> conditionCategories = new ArrayList<>();
    private List<TargetValue> conditionProducts = new ArrayList<>();
    private List<String> conditionBrands = new ArrayList<>();
    private String conditionQuantity;
    private String minOrderValue;
    private String type;
    private String value;
    private String target;
    private List<String> categories = new ArrayList<>();
    private List<TargetValue> products = new ArrayList<>();
    private List<String> brands = new ArrayList<>();
    private String validFrom;
    private String validTo;
    private boolean active;

    public static PromotionForm empty() {
        PromotionForm form = new PromotionForm();
        form.conditionTarget = "";
        form.conditionQuantity = "1";
        form.type = DiscountType.Percentage.name();
        form.target = "";
        form.active = true;
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "name", name, "store.promotions.name.required");
        DiscountFormFields.validateTarget(errors, "conditionTarget", conditionTarget, conditionValues());
        if (DiscountFormFields.target(conditionTarget) != null
                && FormNumbers.integer(conditionQuantity).filter(quantity -> quantity >= 1).isEmpty()) {
            errors.put("conditionQuantity", "store.promotions.conditionQuantity.invalid");
        }
        DiscountFormFields.validateMinOrderValue(errors, minOrderValue);
        DiscountFormFields.validateDiscount(errors, type, value);
        DiscountFormFields.validateTarget(errors, "target", target, targetValues());
        DiscountFormFields.validateDates(errors, validFrom, validTo);
        return errors;
    }

    public Promotion toPromotion(String storeId, LocalDateTime now) {
        DiscountTarget condition = DiscountFormFields.target(conditionTarget);
        Promotion promotion = new Promotion();
        promotion.setStoreId(storeId);
        promotion.setPromotionId(UUID.randomUUID().toString());
        promotion.setName(name.trim());
        promotion.setConditionTarget(condition);
        promotion.setConditionValues(conditionValues());
        promotion.setConditionQuantity(condition == null ? 0 : FormNumbers.integer(conditionQuantity).orElseThrow());
        promotion.setMinOrderValue(DiscountFormFields.minOrderValue(minOrderValue));
        promotion.setType(DiscountFormFields.type(type));
        promotion.setValue(DiscountFormFields.value(type, value));
        promotion.setTarget(DiscountFormFields.target(target));
        promotion.setTargetValues(targetValues());
        promotion.setValidFrom(DiscountFormFields.validFrom(validFrom));
        promotion.setValidTo(DiscountFormFields.validTo(validTo));
        promotion.setActive(active);
        promotion.setCreatedAt(now);
        return promotion;
    }

    private List<TargetValue> conditionValues() {
        return DiscountFormFields.targetValues(conditionTarget, conditionCategories, conditionProducts, conditionBrands);
    }

    private List<TargetValue> targetValues() {
        return DiscountFormFields.targetValues(target, categories, products, brands);
    }
}
