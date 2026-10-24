package pl.commercelink.web.settings;

import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.orders.DiscountedOrderEntry;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.web.dtos.FormNumbers;
import pl.commercelink.web.orders.OrderLabels;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DiscountCodeDetailsView(DiscountCodeView summary, List<TargetValue> targetValues, String validFrom,
                                      String validTo, boolean active, boolean deletable, String toggleHref,
                                      String deleteHref, Orders orders) {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static DiscountCodeDetailsView of(DiscountCode code, LocalDateTime now, Map<String, String> gatewayNames,
                                             String basePath, Orders orders) {
        DiscountCodeView summary = DiscountCodeView.of(code, now, gatewayNames, basePath);
        return new DiscountCodeDetailsView(summary,
                code.getTargetValues() == null ? List.of() : List.copyOf(code.getTargetValues()),
                code.getValidFrom() == null ? null : DATE_TIME.format(code.getValidFrom()),
                code.getValidTo() == null ? null : DATE_TIME.format(code.getValidTo()),
                code.isActive(), code.getUsedCount() == 0, summary.href() + "/toggle", summary.href() + "/delete",
                orders);
    }

    /**
     * The orders placed with the code and their sums; not available while the index cannot be read yet. average is
     * null without orders. The sums count every listed order, cancelled ones too, as the code's usage counter does.
     */
    public record Orders(boolean available, String from, String to, String fromLabel, String toLabel, int count,
                         String discount, String value, String average, List<OrderRow> rows) {

        /** ordersPath: where an order's page is, null when this user cannot open it (a super admin). */
        public static Orders of(Optional<List<DiscountedOrderEntry>> entries, LocalDate from, LocalDate to,
                                String ordersPath) {
            List<DiscountedOrderEntry> listed = entries.orElse(List.of());
            BigDecimal discount = sum(listed.stream().map(DiscountedOrderEntry::discountAmount).toList());
            BigDecimal value = sum(listed.stream().map(DiscountedOrderEntry::totalPrice).toList());
            String average = listed.isEmpty() ? null
                    : money(value.divide(BigDecimal.valueOf(listed.size()), 2, RoundingMode.HALF_UP));
            return new Orders(entries.isPresent(),
                    from == null ? null : from.toString(), to == null ? null : to.toString(),
                    from == null ? null : DATE.format(from), to == null ? null : DATE.format(to),
                    listed.size(), money(discount), money(value), average,
                    listed.stream().map(entry -> OrderRow.of(entry, ordersPath)).toList());
        }

        public boolean narrowed() {
            return from != null || to != null;
        }

        private static BigDecimal sum(List<Double> amounts) {
            return amounts.stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private static String money(BigDecimal amount) {
            return FormNumbers.format(amount.setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
    }

    public record OrderRow(String shortId, String href, String orderedAt, String email, String value, String discount,
                           String statusKey, String statusTone) {

        static OrderRow of(DiscountedOrderEntry entry, String ordersPath) {
            return new OrderRow(ConversionUtil.getShortenedId(entry.orderId()),
                    ordersPath == null ? null : ordersPath + "/" + entry.orderId(),
                    entry.orderedAt() == null ? null : DATE_TIME.format(entry.orderedAt()), entry.email(),
                    FormNumbers.format(entry.totalPrice()), FormNumbers.format(entry.discountAmount()),
                    OrderLabels.status(entry.status()), OrderLabels.tone(entry.status()));
        }
    }
}
