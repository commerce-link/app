package pl.commercelink.web.settings;

import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodeStatus;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.web.dtos.FormNumbers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public record DiscountCodeView(String code, String name, String href, boolean percentage, String value,
                               String minOrderValue, DiscountTarget target, List<String> targetNames, int moreTargets,
                               String paymentProvider, int usedCount, int maxUses, int usagePercent, String validFrom,
                               String validTo, DiscountCodeStatus status) {

    private static final int SHOWN_TARGETS = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static DiscountCodeView of(DiscountCode code, LocalDateTime now, Map<String, String> gatewayNames,
                                      String basePath) {
        List<String> names = code.getTargetValues() == null ? List.of()
                : code.getTargetValues().stream().map(TargetValue::getName).toList();
        boolean percentage = code.getType() == DiscountType.Percentage;
        return new DiscountCodeView(code.getCode(), code.getName(), basePath + "/" + code.getCode(), percentage,
                percentage ? percent(code.getValue()) : FormNumbers.format(code.getValue()),
                code.getMinOrderValue() > 0 ? FormNumbers.format(code.getMinOrderValue()) : null,
                code.getTarget(), names.stream().limit(SHOWN_TARGETS).toList(),
                Math.max(0, names.size() - SHOWN_TARGETS),
                code.getPaymentProvider() == null ? null
                        : gatewayNames.getOrDefault(code.getPaymentProvider(), code.getPaymentProvider()),
                code.getUsedCount(), code.getMaxUses(), usagePercent(code),
                code.getValidFrom() == null ? null : DATE.format(code.getValidFrom()),
                code.getValidTo() == null ? null : DATE.format(code.getValidTo()),
                code.statusAt(now));
    }

    public static String tone(DiscountCodeStatus status) {
        return switch (status) {
            case Active -> "is-ok";
            case Scheduled -> "is-info";
            case Exhausted -> "is-warn";
            case Expired -> "is-bad";
            case Disabled -> "is-neutral";
        };
    }

    public String statusTone() {
        return tone(status);
    }

    public boolean limited() {
        return maxUses > 0;
    }

    static String percent(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static int usagePercent(DiscountCode code) {
        if (code.getMaxUses() <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(code.getUsedCount() * 100.0 / code.getMaxUses()));
    }
}
