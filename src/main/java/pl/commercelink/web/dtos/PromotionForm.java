package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.discounts.DiscountScope;
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
    private List<String> conditionCategories = new ArrayList<>();
    private List<String> conditionBrands = new ArrayList<>();
    private List<TargetValue> conditionProducts = new ArrayList<>();
    private String conditionQuantity;
    private String minOrderValue;
    private String type;
    private String value;
    private List<String> categories = new ArrayList<>();
    private List<String> brands = new ArrayList<>();
    private List<TargetValue> products = new ArrayList<>();
    private String validFrom;
    private String validTo;
    private boolean active;

    public static PromotionForm empty() {
        PromotionForm form = new PromotionForm();
        form.conditionQuantity = "1";
        form.type = DiscountType.Percentage.name();
        form.active = true;
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "name", name, "store.promotions.name.required");
        if (!conditionScope().isEmpty()
                && FormNumbers.integer(conditionQuantity).filter(quantity -> quantity >= 1).isEmpty()) {
            errors.put("conditionQuantity", "store.promotions.conditionQuantity.invalid");
        }
        DiscountFormFields.validateMinOrderValue(errors, minOrderValue);
        DiscountFormFields.validateDiscount(errors, type, value);
        DiscountFormFields.validateDates(errors, validFrom, validTo);
        return errors;
    }

    public Promotion toPromotion(String storeId, LocalDateTime now) {
        DiscountScope condition = conditionScope();
        Promotion promotion = new Promotion();
        promotion.setStoreId(storeId);
        promotion.setPromotionId(UUID.randomUUID().toString());
        promotion.setName(name.trim());
        promotion.setConditionScope(condition);
        promotion.setConditionQuantity(condition.isEmpty() ? 0 : FormNumbers.integer(conditionQuantity).orElseThrow());
        promotion.setMinOrderValue(DiscountFormFields.minOrderValue(minOrderValue));
        promotion.setType(DiscountFormFields.type(type));
        promotion.setValue(DiscountFormFields.value(type, value));
        promotion.setScope(DiscountFormFields.scope(categories, brands, products));
        promotion.setValidFrom(DiscountFormFields.validFrom(validFrom));
        promotion.setValidTo(DiscountFormFields.validTo(validTo));
        promotion.setActive(active);
        promotion.setCreatedAt(now);
        return promotion;
    }

    private DiscountScope conditionScope() {
        return DiscountFormFields.scope(conditionCategories, conditionBrands, conditionProducts);
    }
}
