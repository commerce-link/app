package pl.commercelink.web.settings;

import pl.commercelink.discounts.Promotion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record PromotionDetailsView(PromotionView summary, String validFrom, String validTo, String createdAt,
                                   boolean active, String toggleHref, String deleteHref) {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    public static PromotionDetailsView of(Promotion promotion, LocalDateTime now, String basePath) {
        PromotionView summary = PromotionView.of(promotion, now, basePath);
        return new PromotionDetailsView(summary,
                dateTime(promotion.getValidFrom()), dateTime(promotion.getValidTo()), dateTime(promotion.getCreatedAt()),
                promotion.isActive(), summary.href() + "/toggle", summary.href() + "/delete");
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }
}
