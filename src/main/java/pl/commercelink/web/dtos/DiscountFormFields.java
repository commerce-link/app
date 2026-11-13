package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class DiscountFormFields {

    private static final double MIN_PERCENTAGE = 1;
    private static final double MAX_PERCENTAGE = 99;

    private DiscountFormFields() {
    }

    static void validateDiscount(Map<String, String> errors, String type, String value) {
        DiscountType chosen = type(type);
        if (chosen == null) {
            errors.put("type", "store.discounts.type.required");
        }
        if (!FormRules.requireText(errors, "value", value, "store.discounts.value.required")) {
            return;
        }
        Optional<Double> parsed = FormNumbers.decimal(value);
        if (parsed.isEmpty()) {
            errors.put("value", "store.discounts.value.invalid");
            return;
        }
        double number = parsed.get();
        if (chosen == DiscountType.Percentage && (number < MIN_PERCENTAGE || number > MAX_PERCENTAGE)) {
            errors.put("value", "store.discounts.value.percentage");
        }
        if (chosen == DiscountType.Amount && toGrosz(number) <= 0) {
            errors.put("value", "store.discounts.value.amount");
        }
    }

    static void validateTarget(Map<String, String> errors, String field, String target, List<TargetValue> values) {
        DiscountTarget chosen = target(target);
        if (StringUtils.isNotBlank(target) && chosen == null) {
            errors.put(field, "store.discounts.target.invalid");
        } else if (chosen != null && values.isEmpty()) {
            errors.put(field + "Values", "store.discounts.targetValues.required." + chosen.name());
        }
    }

    static void validateMinOrderValue(Map<String, String> errors, String minOrderValue) {
        if (StringUtils.isNotBlank(minOrderValue) && FormNumbers.decimal(minOrderValue).filter(v -> v >= 0).isEmpty()) {
            errors.put("minOrderValue", "store.discounts.minOrderValue.invalid");
        }
    }

    static void validateDates(Map<String, String> errors, String validFrom, String validTo) {
        if (!isBlankOrReadable(validFrom)) {
            errors.put("validFrom", "store.discounts.validFrom.invalid");
        }
        if (!isBlankOrReadable(validTo)) {
            errors.put("validTo", "store.discounts.validTo.invalid");
        }
        Optional<LocalDateTime> from = dateTime(validFrom);
        Optional<LocalDateTime> to = dateTime(validTo);
        if (from.isPresent() && to.isPresent() && to.get().isBefore(from.get())) {
            errors.put("validTo", "store.discounts.validTo.beforeFrom");
        }
    }

    static DiscountType type(String type) {
        for (DiscountType candidate : DiscountType.values()) {
            if (candidate.name().equals(type)) {
                return candidate;
            }
        }
        return null;
    }

    static double value(String type, String value) {
        double parsed = FormNumbers.decimal(value).orElseThrow();
        return type(type) == DiscountType.Amount ? toGrosz(parsed) : parsed;
    }

    static DiscountTarget target(String target) {
        for (DiscountTarget candidate : DiscountTarget.values()) {
            if (candidate.name().equals(target)) {
                return candidate;
            }
        }
        return null;
    }

    static List<TargetValue> targetValues(String target, List<String> categories, List<TargetValue> products,
                                          List<String> brands) {
        DiscountTarget chosen = target(target);
        if (chosen == null) {
            return new ArrayList<>();
        }
        return switch (chosen) {
            case Category -> byName(categories);
            case Brand -> byName(brands);
            case Product -> byId(products);
        };
    }

    static double minOrderValue(String value) {
        return FormNumbers.decimal(value).map(DiscountFormFields::toGrosz).orElse(0.0);
    }

    static LocalDateTime validFrom(String value) {
        return dateTime(value).orElse(null);
    }

    static LocalDateTime validTo(String value) {
        return dateTime(value).map(end -> end.withSecond(59)).orElse(null);
    }

    private static double toGrosz(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static List<TargetValue> byName(List<String> names) {
        if (names == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(names.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .map(name -> new TargetValue(name, name))
                .toList());
    }

    private static List<TargetValue> byId(List<TargetValue> picked) {
        Map<String, TargetValue> unique = new LinkedHashMap<>();
        if (picked != null) {
            for (TargetValue value : picked) {
                String id = value == null ? null : StringUtils.trimToNull(value.getId());
                if (id != null) {
                    unique.putIfAbsent(id, new TargetValue(id, StringUtils.defaultIfBlank(StringUtils.trim(value.getName()), id)));
                }
            }
        }
        return new ArrayList<>(unique.values());
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
}
