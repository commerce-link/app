package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.stores.Store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DiscountCodeForm {

    private String code;
    private String name;
    private String type;
    private String value;
    private String target;
    private List<String> categories = new ArrayList<>();
    private List<TargetValue> products = new ArrayList<>();
    private List<String> brands = new ArrayList<>();
    private String paymentProvider;
    private String minOrderValue;
    private String maxUses;
    private String validFrom;
    private String validTo;
    private boolean active;

    public static DiscountCodeForm empty() {
        DiscountCodeForm form = new DiscountCodeForm();
        form.type = DiscountType.Percentage.name();
        form.target = "";
        form.active = true;
        return form;
    }

    public Map<String, String> validate(Store store) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (FormRules.requireText(errors, "code", code, "store.discounts.code.required")
                && !DiscountCode.isWellFormed(DiscountCode.normalize(code))) {
            errors.put("code", "store.discounts.code.invalid");
        }
        FormRules.requireText(errors, "name", name, "store.discounts.name.required");
        DiscountFormFields.validateDiscount(errors, type, value);
        DiscountFormFields.validateTarget(errors, "target", target, targetValues());
        if (StringUtils.isNotBlank(paymentProvider) && store.getPaymentIntegration(paymentProvider.trim()) == null) {
            errors.put("paymentProvider", "store.discounts.paymentProvider.invalid");
        }
        if (StringUtils.isNotBlank(minOrderValue) && FormNumbers.decimal(minOrderValue).filter(v -> v >= 0).isEmpty()) {
            errors.put("minOrderValue", "store.discounts.minOrderValue.invalid");
        }
        if (StringUtils.isNotBlank(maxUses) && FormNumbers.integer(maxUses).filter(v -> v >= 0).isEmpty()) {
            errors.put("maxUses", "store.discounts.maxUses.invalid");
        }
        DiscountFormFields.validateDates(errors, validFrom, validTo);
        return errors;
    }

    public DiscountCode toDiscountCode(String storeId) {
        DiscountCode discountCode = new DiscountCode();
        discountCode.setStoreId(storeId);
        discountCode.setCode(DiscountCode.normalize(code));
        discountCode.setName(name.trim());
        discountCode.setType(DiscountFormFields.type(type));
        discountCode.setValue(DiscountFormFields.value(type, value));
        discountCode.setTarget(DiscountFormFields.target(target));
        discountCode.setTargetValues(targetValues());
        discountCode.setPaymentProvider(StringUtils.trimToNull(paymentProvider));
        discountCode.setMinOrderValue(FormNumbers.decimal(minOrderValue).map(DiscountFormFields::toGrosz).orElse(0.0));
        discountCode.setMaxUses(FormNumbers.integer(maxUses).orElse(0));
        discountCode.setValidFrom(DiscountFormFields.validFrom(validFrom));
        discountCode.setValidTo(DiscountFormFields.validTo(validTo));
        discountCode.setActive(active);
        return discountCode;
    }

    private List<TargetValue> targetValues() {
        return DiscountFormFields.targetValues(target, categories, products, brands);
    }
}
