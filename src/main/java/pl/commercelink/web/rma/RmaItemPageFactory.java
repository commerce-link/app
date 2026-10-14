package pl.commercelink.web.rma;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.SerialNumbers;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.*;
import pl.commercelink.web.dtos.RmaItemForm;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderLinks;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class RmaItemPageFactory {

    // items added by hand carry this delivery id; there is nothing to link or show
    private static final String UNKNOWN_DELIVERY = "Unknown";

    private final MessageSource messageSource;

    /**
     * @param posted the form of a refused post (errors, conflict, closed RMA), shown with what was typed; null renders the stored item
     * @param deliverySupplier the label of the delivery's supplier, or null when the delivery was not found
     */
    public RmaItemPage page(String storeId, RMA rma, RMAItem item, OrderItem orderItem, Delivery delivery, String deliverySupplier,
                            RmaItemForm posted, Map<String, String> errors, String errorMessage, String savedMessage, Locale locale) {
        RMAStatus rmaStatus = rma.getStatus() == null ? RMAStatus.New : rma.getStatus();
        RMAItemStatus status = item.getStatus() == null ? RMAItemStatus.New : item.getStatus();
        RmaItemForm form = posted != null ? posted : RmaItemForm.of(item);
        boolean readOnly = rmaStatus.isClosed() && posted == null;
        String rmaShort = shortId(rma.getRmaId());
        return new RmaItemPage(rma.getRmaId(), item.getRmaItemId(), "/dashboard/rma/" + rma.getRmaId(),
                text(locale, "rma.item.page.back", rmaShort),
                item.getName(), text(locale, "RMAItemStatus." + status.name()), tone(status),
                text(locale, "rma.item.page.meta.qty", item.getQty()),
                StringUtils.trimToNull(item.getEan()), StringUtils.trimToNull(item.getMfn()), shortId(item.getRmaItemId()),
                readOnly, rmaStatus.isClosed() ? text(locale, "rma.item.page.closed." + rmaStatus.name()) : null,
                form, errors, errorMessage,
                serialHint(form.getSerialNo(), orderItem, status, locale),
                new RmaItemPage.Resolution(resolution(item.getDesiredResolution(), locale), resolution(item.getActualResolution(), locale)),
                values(item, locale), amount(locale, item.getTotalPrice()),
                context(storeId, rma, rmaStatus, item, delivery, deliverySupplier, form, locale),
                savedMessage);
    }

    /** New and sent-for-repair items wait for someone else, a received one waits for the operator, the rest are done. */
    public static String tone(RMAItemStatus status) {
        if (status == null) return "is-info";
        return switch (status) {
            case New, SentForRepair -> "is-info";
            case Received -> "is-warn";
            case ReturnedToClient, MovedToWarehouse -> "is-ok";
        };
    }

    private RmaItemPage.SerialHint serialHint(String typed, OrderItem orderItem, RMAItemStatus status, Locale locale) {
        String hint;
        boolean warn = false;
        if (orderItem == null) {
            hint = text(locale, "rma.item.page.serial.plain");
        } else {
            List<String> inOrder = SerialNumbers.parse(orderItem.getSerialNo());
            if (inOrder.isEmpty()) {
                hint = text(locale, "rma.item.page.serial.orderEmpty");
            } else {
                List<String> outside = SerialNumbers.parse(typed).stream().filter(sn -> !inOrder.contains(sn)).toList();
                warn = !outside.isEmpty();
                hint = warn
                        ? text(locale, "rma.item.page.serial.mismatch", String.join(", ", outside), String.join(", ", inOrder))
                        : text(locale, "rma.item.page.serial.order", String.join(", ", inOrder));
            }
        }
        // the number has already gone on to a warehouse document or a replacement order item, which this page does not edit
        if (status == RMAItemStatus.MovedToWarehouse || status == RMAItemStatus.ReturnedToClient) {
            hint = hint + " " + text(locale, "rma.item.page.serial.settled");
        }
        return new RmaItemPage.SerialHint(hint, warn);
    }

    private List<RmaItemPage.Fact> values(RMAItem item, Locale locale) {
        return List.of(
                new RmaItemPage.Fact(text(locale, "rma.item.page.value.qty"), text(locale, "rma.item.page.meta.qty", item.getQty())),
                new RmaItemPage.Fact(text(locale, "rma.item.page.value.price"), amount(locale, item.getPrice())),
                new RmaItemPage.Fact(text(locale, "rma.item.page.value.cost"), amount(locale, item.getCost())),
                new RmaItemPage.Fact(text(locale, "rma.item.page.value.vat"), Math.round((item.getTax() - 1) * 100) + "%"));
    }

    private RmaItemPage.Context context(String storeId, RMA rma, RMAStatus rmaStatus, RMAItem item, Delivery delivery,
                                        String deliverySupplier, RmaItemForm form, Locale locale) {
        String orderId = StringUtils.trimToNull(rma.getOrderId());
        String deliveryId = StringUtils.trimToNull(item.getDeliveryId());
        boolean hasDelivery = deliveryId != null && !UNKNOWN_DELIVERY.equalsIgnoreCase(deliveryId);
        return new RmaItemPage.Context("/dashboard/rma/" + rma.getRmaId(), shortId(rma.getRmaId()),
                text(locale, "RMAStatus." + rmaStatus.name()), RmaRowMapper.tone(rmaStatus),
                source(rma, locale), client(rma),
                orderId == null ? null : OrderLinks.detailsOf(storeId, orderId, false), orderId == null ? null : shortId(orderId),
                hasDelivery && delivery != null ? "/dashboard/deliveries/details?deliveryId=" + URLEncoder.encode(deliveryId, StandardCharsets.UTF_8) : null,
                hasDelivery ? item.getShortenedDeliveryId() : null,
                hasDelivery && delivery != null ? deliverySupplier : null,
                OrderLinks.serialHistory(form.getSerialNo()));
    }

    private String source(RMA rma, Locale locale) {
        if (!rma.isMarketplaceReturn() || StringUtils.isBlank(rma.getMarketplace())) {
            return text(locale, "rma.list.source.store");
        }
        String reference = StringUtils.firstNonBlank(rma.getExternalReturnReference(), rma.getExternalReturnId());
        return reference == null ? rma.getMarketplace() : text(locale, "rma.item.page.context.source.marketplace", rma.getMarketplace(), reference);
    }

    /** The same person the RMA list shows: company, else first and last name, with the city; an e-mail when there is no address. */
    private static String client(RMA rma) {
        ShippingDetails details = rma.getShippingDetails();
        String person = details == null ? null : StringUtils.firstNonBlank(details.getCompanyName(),
                StringUtils.trimToNull(Stream.of(details.getName(), details.getSurname()).filter(StringUtils::isNotBlank).collect(Collectors.joining(" "))));
        if (person == null) {
            return StringUtils.trimToNull(rma.getEmail());
        }
        String city = StringUtils.trimToNull(details.getCity());
        return city == null ? person : person + " · " + city;
    }

    private String resolution(RMAResolutionType type, Locale locale) {
        return type == null ? null : text(locale, "RMAResolutionType." + type.name());
    }

    private String amount(Locale locale, double value) {
        return text(locale, "general.currency.amount", Money.format(value));
    }

    private static String shortId(String id) {
        return id == null ? null : id.length() > 8 ? id.substring(0, 8) : id;
    }

    private String text(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
