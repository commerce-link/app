package pl.commercelink.web.rma;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAStatus;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Words one RMA for the list: short numbers, client, return shipment, marketplace state, age and value. */
public class RmaRowMapper {

    // an RMA still being handled after two weeks is flagged: Allegro gives the buyer 14 days, so it has stalled
    static final int STALE_AFTER_DAYS = 14;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM");

    private final MessageSource messages;
    private final Locale locale;
    private final DecimalFormat amount;

    public RmaRowMapper(MessageSource messages, Locale locale) {
        this.messages = messages;
        this.locale = locale;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        this.amount = new DecimalFormat("#,##0.00", symbols);
    }

    public RmaRow map(RMA rma, LocalDate today) {
        RMAStatus status = rma.getStatus() == null ? RMAStatus.New : rma.getStatus();
        boolean marketplace = rma.isMarketplaceReturn();
        ShippingDetails details = rma.getShippingDetails();
        String person = details == null ? null : firstNonBlank(details.getCompanyName(),
                Stream.of(details.getName(), details.getSurname()).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ")));
        String clientName = person != null ? person : StringUtils.trimToNull(rma.getEmail());
        String email = person != null ? StringUtils.trimToNull(rma.getEmail()) : null;
        Long age = rma.getCreatedAt() == null || status.isClosed() ? null
                : Math.max(0, ChronoUnit.DAYS.between(rma.getCreatedAt().toLocalDate(), today));
        return new RmaRow(
                shortId(rma.getRmaId()),
                rma.getRmaId(),
                "/dashboard/rma/" + rma.getRmaId(),
                marketplace && StringUtils.isNotBlank(rma.getMarketplace()) ? rma.getMarketplace() : text("rma.list.source.store"),
                marketplace ? StringUtils.trimToNull(rma.getExternalReturnReference()) : null,
                clientName,
                details == null ? null : StringUtils.trimToNull(details.getCity()),
                email,
                shortId(StringUtils.trimToNull(rma.getOrderId())),
                StringUtils.trimToNull(rma.getOrderId()),
                text("RMAStatus." + status.name()),
                tone(status),
                track(rma.getShipments()),
                marketplace && rma.getExternalReturnStatus() != null
                        ? text("rma.list.marketplace.status", rma.getMarketplace(), text("MarketplaceReturnStatus." + rma.getExternalReturnStatus().name()))
                        : null,
                rma.getCreatedAt() == null ? null : DAY.format(rma.getCreatedAt()),
                age == null ? null : age == 0 ? text("rma.list.age.today") : age == 1 ? text("rma.list.age.one") : text("rma.list.age.days", age),
                age != null && age >= STALE_AFTER_DAYS ? "is-warn" : null,
                rma.getShippingInsurance() > 0 ? text("general.currency.amount", amount.format(rma.getShippingInsurance())) : null);
    }

    /** New and received RMAs wait for the operator; the rest wait for someone else or are done. */
    public static String tone(RMAStatus status) {
        if (status == null) return "is-warn";
        return switch (status) {
            case New, ItemsReceived -> "is-warn";
            case Approved, WaitingForItems, Processing -> "is-info";
            case Completed -> "is-ok";
            case Rejected -> "is-neutral";
        };
    }

    private String track(List<Shipment> shipments) {
        if (shipments == null || shipments.isEmpty()) {
            return null;
        }
        Shipment last = shipments.get(shipments.size() - 1);
        String carrier = StringUtils.isNotBlank(last.getCarrier()) ? last.getCarrier() : text("rma.list.track.carrier");
        if (last.getDeliveredAt() != null) {
            return text("rma.list.track.delivered", carrier, DAY_MONTH.format(last.getDeliveredAt()));
        }
        return last.getShippedAt() != null ? text("rma.list.track.inTransit", carrier) : text("rma.list.track.waiting", carrier);
    }

    private static String shortId(String id) {
        return id == null || id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static String firstNonBlank(String... values) {
        return Stream.of(values).filter(StringUtils::isNotBlank).map(String::trim).findFirst().orElse(null);
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
