package pl.commercelink.web.settings;

import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.TargetValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record PromotionDetailsView(PromotionView summary, List<TargetValue> conditionValues,
                                   List<TargetValue> targetValues, String validFrom, String validTo, String createdAt,
                                   boolean active, String toggleHref, String deleteHref) {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    public static PromotionDetailsView of(Promotion promotion, LocalDateTime now, String basePath) {
        PromotionView summary = PromotionView.of(promotion, now, basePath);
        return new PromotionDetailsView(summary, copy(promotion.getConditionValues()), copy(promotion.getTargetValues()),
                dateTime(promotion.getValidFrom()), dateTime(promotion.getValidTo()), dateTime(promotion.getCreatedAt()),
                promotion.isActive(), summary.href() + "/toggle", summary.href() + "/delete");
    }

    private static List<TargetValue> copy(List<TargetValue> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }
}
