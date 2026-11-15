package pl.commercelink.web.settings;

import pl.commercelink.discounts.DiscountCodeStatus;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.web.dtos.FormNumbers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record PromotionView(String name, String href, DiscountScopeView condition, int conditionQuantity,
                            String minOrderValue, boolean percentage, String value, DiscountScopeView scope,
                            String validFrom, String validTo, DiscountCodeStatus status) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static PromotionView of(Promotion promotion, LocalDateTime now, String basePath) {
        boolean percentage = promotion.getType() == DiscountType.Percentage;
        return new PromotionView(promotion.getName(), basePath + "/" + promotion.getPromotionId(),
                DiscountScopeView.of(promotion.getConditionScope()), promotion.getConditionQuantity(),
                promotion.getMinOrderValue() > 0 ? FormNumbers.format(promotion.getMinOrderValue()) : null,
                percentage,
                percentage ? DiscountCodeView.percent(promotion.getValue()) : FormNumbers.format(promotion.getValue()),
                DiscountScopeView.of(promotion.getScope()),
                promotion.getValidFrom() == null ? null : DATE.format(promotion.getValidFrom()),
                promotion.getValidTo() == null ? null : DATE.format(promotion.getValidTo()),
                promotion.statusAt(now));
    }

    public String statusTone() {
        return DiscountCodeView.tone(status);
    }
}
