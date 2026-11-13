package pl.commercelink.web.settings;

import pl.commercelink.discounts.DiscountCodeStatus;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.web.dtos.FormNumbers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record PromotionView(String name, String href, DiscountTarget conditionTarget, List<String> conditionNames,
                            int moreConditions, int conditionQuantity, String minOrderValue, boolean percentage,
                            String value, DiscountTarget target, List<String> targetNames, int moreTargets,
                            String validFrom, String validTo, DiscountCodeStatus status) {

    private static final int SHOWN_VALUES = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static PromotionView of(Promotion promotion, LocalDateTime now, String basePath) {
        List<String> conditionNames = names(promotion.getConditionValues());
        List<String> targetNames = names(promotion.getTargetValues());
        boolean percentage = promotion.getType() == DiscountType.Percentage;
        return new PromotionView(promotion.getName(), basePath + "/" + promotion.getPromotionId(),
                promotion.getConditionTarget(), shown(conditionNames), more(conditionNames),
                promotion.getConditionQuantity(),
                promotion.getMinOrderValue() > 0 ? FormNumbers.format(promotion.getMinOrderValue()) : null,
                percentage,
                percentage ? DiscountCodeView.percent(promotion.getValue()) : FormNumbers.format(promotion.getValue()),
                promotion.getTarget(), shown(targetNames), more(targetNames),
                promotion.getValidFrom() == null ? null : DATE.format(promotion.getValidFrom()),
                promotion.getValidTo() == null ? null : DATE.format(promotion.getValidTo()),
                promotion.statusAt(now));
    }

    public String statusTone() {
        return DiscountCodeView.tone(status);
    }

    private static List<String> names(List<TargetValue> values) {
        return values == null ? List.of() : values.stream().map(TargetValue::getName).toList();
    }

    private static List<String> shown(List<String> names) {
        return names.stream().limit(SHOWN_VALUES).toList();
    }

    private static int more(List<String> names) {
        return Math.max(0, names.size() - SHOWN_VALUES);
    }
}
