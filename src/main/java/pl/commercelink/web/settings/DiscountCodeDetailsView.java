package pl.commercelink.web.settings;

import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.TargetValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public record DiscountCodeDetailsView(DiscountCodeView summary, List<TargetValue> targetValues, String validFrom,
                                      String validTo, boolean active, boolean deletable, String toggleHref,
                                      String deleteHref) {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    public static DiscountCodeDetailsView of(DiscountCode code, LocalDateTime now, Map<String, String> gatewayNames,
                                             String basePath) {
        DiscountCodeView summary = DiscountCodeView.of(code, now, gatewayNames, basePath);
        return new DiscountCodeDetailsView(summary,
                code.getTargetValues() == null ? List.of() : List.copyOf(code.getTargetValues()),
                code.getValidFrom() == null ? null : DATE_TIME.format(code.getValidFrom()),
                code.getValidTo() == null ? null : DATE_TIME.format(code.getValidTo()),
                code.isActive(), code.getUsedCount() == 0, summary.href() + "/toggle", summary.href() + "/delete");
    }
}
