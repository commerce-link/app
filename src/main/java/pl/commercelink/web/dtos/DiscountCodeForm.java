package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
public class DiscountCodeForm {

    private static final double MIN_PERCENTAGE = 1;
    private static final double MAX_PERCENTAGE = 99;

    private String code;
    private String name;
    private String type;
    private String value;
    private String target;
    private List<String> categories = new ArrayList<>();
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
        if (discountType() == null) {
            errors.put("type", "store.discounts.type.required");
        }
        validateValue(errors);
        if (StringUtils.isNotBlank(target) && discountTarget() == null) {
            errors.put("target", "store.discounts.target.invalid");
        } else if (discountTarget() != null && targetValues().isEmpty()) {
            errors.put("targetValues", "store.discounts.targetValues.required");
        }
        if (StringUtils.isNotBlank(paymentProvider) && store.getPaymentIntegration(paymentProvider.trim()) == null) {
            errors.put("paymentProvider", "store.discounts.paymentProvider.invalid");
        }
        if (StringUtils.isNotBlank(minOrderValue) && FormNumbers.decimal(minOrderValue).filter(v -> v >= 0).isEmpty()) {
            errors.put("minOrderValue", "store.discounts.minOrderValue.invalid");
        }
        if (StringUtils.isNotBlank(maxUses) && FormNumbers.integer(maxUses).filter(v -> v >= 0).isEmpty()) {
            errors.put("maxUses", "store.discounts.maxUses.invalid");
        }
        validateDates(errors);
        return errors;
    }

    public DiscountCode toDiscountCode(String storeId) {
        DiscountCode discountCode = new DiscountCode();
        discountCode.setStoreId(storeId);
        discountCode.setCode(DiscountCode.normalize(code));
        discountCode.setName(name.trim());
        discountCode.setType(discountType());
        double parsedValue = FormNumbers.decimal(value).orElseThrow();
        discountCode.setValue(discountType() == DiscountType.Amount ? toGrosz(parsedValue) : parsedValue);
        discountCode.setTarget(discountTarget());
        discountCode.setTargetValues(targetValues());
        discountCode.setPaymentProvider(StringUtils.trimToNull(paymentProvider));
        discountCode.setMinOrderValue(FormNumbers.decimal(minOrderValue).map(DiscountCodeForm::toGrosz).orElse(0.0));
        discountCode.setMaxUses(FormNumbers.integer(maxUses).orElse(0));
        discountCode.setValidFrom(dateTime(validFrom).orElse(null));
        discountCode.setValidTo(dateTime(validTo).map(end -> end.withSecond(59)).orElse(null));
        discountCode.setActive(active);
        return discountCode;
    }

    private void validateValue(Map<String, String> errors) {
        if (!FormRules.requireText(errors, "value", value, "store.discounts.value.required")) {
            return;
        }
        Optional<Double> parsed = FormNumbers.decimal(value);
        if (parsed.isEmpty()) {
            errors.put("value", "store.discounts.value.invalid");
            return;
        }
        double number = parsed.get();
        if (discountType() == DiscountType.Percentage && (number < MIN_PERCENTAGE || number > MAX_PERCENTAGE)) {
            errors.put("value", "store.discounts.value.percentage");
        }
        if (discountType() == DiscountType.Amount && toGrosz(number) <= 0) {
            errors.put("value", "store.discounts.value.amount");
        }
    }

    private void validateDates(Map<String, String> errors) {
        boolean fromReadable = isBlankOrReadable(validFrom);
        boolean toReadable = isBlankOrReadable(validTo);
        if (!fromReadable) {
            errors.put("validFrom", "store.discounts.validFrom.invalid");
        }
        if (!toReadable) {
            errors.put("validTo", "store.discounts.validTo.invalid");
        }
        Optional<LocalDateTime> from = dateTime(validFrom);
        Optional<LocalDateTime> to = dateTime(validTo);
        if (from.isPresent() && to.isPresent() && to.get().isBefore(from.get())) {
            errors.put("validTo", "store.discounts.validTo.beforeFrom");
        }
    }

    private List<TargetValue> targetValues() {
        if (discountTarget() != DiscountTarget.Category || categories == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(categories.stream()
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .map(category -> new TargetValue(category, category))
                .toList());
    }

    private DiscountType discountType() {
        for (DiscountType candidate : DiscountType.values()) {
            if (candidate.name().equals(type)) {
                return candidate;
            }
        }
        return null;
    }

    private DiscountTarget discountTarget() {
        for (DiscountTarget candidate : DiscountTarget.values()) {
            if (candidate.name().equals(target)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isBlankOrReadable(String value) {
        return StringUtils.isBlank(value) || dateTime(value).isPresent();
    }

    private static Optional<LocalDateTime> dateTime(String value) {
        if (StringUtils.isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value.trim()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static double toGrosz(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
