package pl.commercelink.web.rma;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.rma.MarketplaceDecision;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderLinks;
import pl.commercelink.web.orders.PluralForm;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Builds the RMA details page (RmaDetailPage) from the RMA, its items and the order items still left to add. */
@Component
@RequiredArgsConstructor
public class RmaDetailPageFactory {

    static final int HISTORY_VISIBLE = 4;
    static final String REJECT_BLOCKED_KEY = "rma.detail.reject.blocked";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Set<String> DIALOGS = Set.of("next", "status", "reject", "add", "shipment-new", "split");
    private static final String SHIPMENT_DIALOG = "shipment-";
    // the marketplace has no status scale of its own on this page; the list and the mock-ups show it as information
    private static final String MARKETPLACE_TONE = "is-info";
    private static final Set<String> KNOWN_EVENTS = Set.of("RMA_CARRIER_ARRANGEMENT", "RMA_CARRIER_CONFIRMATION",
            "RMA_REJECTED", "RMA_ITEMS_RECEIVED", "RMA_PROCESSING_STARTED", "RMA_ITEMS_ACCEPTED",
            "RMA_ITEMS_SEND_TO_CLIENT", RMA.EVENT_REFUND_REQUESTED, RMA.EVENT_REJECTION_SENT,
            RMA.EVENT_REFUNDED_BY_MARKETPLACE, "SHIPMENT_TRACKING_EXPIRED", "SHIPMENT_TRACKING_UNAVAILABLE");

    private final MessageSource messageSource;

    /**
     * @param remaining     the order items that can still be added to the RMA
     * @param orderAddress  the order's shipping address, null when the order has none (the address dialog offers it)
     * @param clientLink    the client's return page, RMA#createClientRmaUrl
     * @param open          the ?open= dialog asked for, accepted only when the page offers it
     * @param splitItemId   the ?item= of ?open=split
     */
    public RmaDetailPage page(String storeId, RMA rma, List<RMAItem> items, List<OrderItem> remaining,
                              ShippingDetails orderAddress, boolean refundDeliveryDefault, String clientLink,
                              String open, String splitItemId, Locale locale) {
        RMAStatus status = rma.getStatus() == null ? RMAStatus.New : rma.getStatus();
        boolean closed = status.isClosed();
        boolean marketplace = rma.isMarketplaceReturn();
        List<RMAItem> rmaItems = items == null ? List.of() : items;
        List<OrderItem> left = remaining == null ? List.of() : remaining;
        double total = rmaItems.stream().mapToDouble(RMAItem::getTotalPrice).sum();
        String resendHref = marketplace && !decisions(rma).isEmpty()
                ? "/dashboard/rma/" + rma.getRmaId() + "/resend-marketplace-decision" : null;

        RmaDetailPage.Header header = header(storeId, rma, status, rmaItems, total, clientLink, resendHref, locale);
        RmaDetailPage.Items itemsCard = new RmaDetailPage.Items(rows(rma, rmaItems, locale), !closed,
                !closed && !left.isEmpty(), amount(locale, total), marketplace, refundDeliveryDefault);
        RmaDetailPage.SplitTarget split = "split".equals(open) ? splitTarget(rma, closed, rmaItems, splitItemId) : null;

        return new RmaDetailPage(rma.getRmaId(), RmaAddressPage.shortId(rma.getRmaId()), closed, marketplace,
                openDialog(open, rma, header, itemsCard, closed, split),
                header, itemsCard, media(rma, closed), settings(rma, marketplace, locale),
                marketplace ? marketplaceCard(rma, resendHref, locale) : null,
                address(rma, closed, orderAddress, locale), history(rma, locale), current(rma, status),
                new RmaDetailPage.AddDialog(left.stream().map(oi -> addOption(oi, locale)).toList()), split);
    }

    private RmaDetailPage.Header header(String storeId, RMA rma, RMAStatus status, List<RMAItem> items, double total,
                                        String clientLink, String resendHref, Locale locale) {
        boolean closed = status.isClosed();
        boolean rejectBlocked = !closed && rma.blocksRejectionAfterRefund(RMAStatus.Rejected);
        String orderId = StringUtils.trimToNull(rma.getOrderId());
        String person = RmaRowMapper.person(rma.getShippingDetails());
        return new RmaDetailPage.Header(text(locale, "RMAStatus." + status.name()), RmaRowMapper.tone(status),
                next(rma, status, items), person != null ? person : StringUtils.trimToNull(rma.getEmail()),
                source(rma, locale), orderId, orderId == null ? null : OrderLinks.detailsOf(storeId, orderId, false),
                dateTime(rma.getCreatedAt()),
                text(locale, "rma.detail.items.count." + PluralForm.of(items.size()), items.size()),
                amount(locale, total), rma.isMarketplaceReturn() ? marketplaceLine(rma, locale) : null, clientLink,
                !closed, !closed && !rejectBlocked, rejectBlocked ? REJECT_BLOCKED_KEY : null, resendHref,
                statusOptions(rma, status, locale), StringUtils.trimToNull(rma.getRejectionReason()));
    }

    /** The next step of the process (decision D1); a status the operator waits in, or a closed RMA, has none. */
    private static RmaDetailPage.NextStatus next(RMA rma, RMAStatus status, List<RMAItem> items) {
        boolean emails = rma.isEmailNotificationsEnabled();
        return switch (status) {
            case New -> new RmaDetailPage.NextStatus(RMAStatus.Approved.name(), "rma.detail.next.approve", "fa-check",
                    "rma.detail.next.approve.title", emails ? "rma.detail.next.approve.effect" : "rma.detail.next.approve.effect.noEmail", 0);
            case ItemsReceived -> new RmaDetailPage.NextStatus(RMAStatus.Processing.name(), "rma.detail.next.process", "fa-play",
                    "rma.detail.next.process.title", emails ? "rma.detail.next.process.effect" : "rma.detail.next.process.effect.noEmail", 0);
            case Processing -> new RmaDetailPage.NextStatus(RMAStatus.Completed.name(), "rma.detail.next.complete", "fa-flag-checkered",
                    "rma.detail.next.complete.title", "rma.detail.next.complete.effect",
                    (int) items.stream().filter(i -> i.getStatus() == null || i.hasOneOfTheStatuses(RMAItemStatus.New, RMAItemStatus.Received)).count());
            case Approved, WaitingForItems, Rejected, Completed -> null;
        };
    }

    /** Every status but Rejected, which is its own action; ItemsReceived cannot be gone back to from Processing. */
    private List<RmaDetailPage.StatusOption> statusOptions(RMA rma, RMAStatus current, Locale locale) {
        return Arrays.stream(RMAStatus.values())
                .filter(s -> s != RMAStatus.Rejected)
                .map(s -> new RmaDetailPage.StatusOption(s.name(), text(locale, "RMAStatus." + s.name()),
                        statusEffectKey(rma, s, current), s == current,
                        s == RMAStatus.ItemsReceived && current == RMAStatus.Processing))
                .toList();
    }

    /** RMALifecycle e-mails the client on Approved, ItemsReceived and Processing, once each, while notifications are on. */
    private static String statusEffectKey(RMA rma, RMAStatus option, RMAStatus current) {
        if (option == current) {
            return "rma.detail.status.effect.current";
        }
        boolean emails = option == RMAStatus.Approved || option == RMAStatus.ItemsReceived || option == RMAStatus.Processing;
        if (emails && !rma.isEmailNotificationsEnabled()) {
            return "rma.detail.status.effect.noEmail";
        }
        return "rma.detail.status.effect." + option.name();
    }

    private String source(RMA rma, Locale locale) {
        return rma.isMarketplaceReturn() && StringUtils.isNotBlank(rma.getMarketplace())
                ? rma.getMarketplace() : text(locale, "rma.list.source.store");
    }

    private RmaDetailPage.MarketplaceLine marketplaceLine(RMA rma, Locale locale) {
        String reference = reference(rma);
        String text = StringUtils.isBlank(rma.getMarketplace())
                ? text(locale, "rma.detail.marketplace.return.unnamed", reference)
                : text(locale, "rma.detail.marketplace.return", rma.getMarketplace(), reference);
        return new RmaDetailPage.MarketplaceLine(text, marketplaceStatus(rma, locale), MARKETPLACE_TONE,
                RmaRowMapper.untrackedCarrier(rma.getShipments()));
    }

    private RmaDetailPage.MarketplaceCard marketplaceCard(RMA rma, String resendHref, Locale locale) {
        List<RmaDetailPage.Decision> decisions = decisions(rma).stream().map(d -> decision(d, locale)).toList();
        return new RmaDetailPage.MarketplaceCard(StringUtils.defaultIfBlank(rma.getMarketplace(), text(locale, "rma.detail.marketplace.unnamed")),
                marketplaceStatus(rma, locale), MARKETPLACE_TONE, reference(rma), decisions, resendHref);
    }

    private RmaDetailPage.Decision decision(MarketplaceDecision decision, Locale locale) {
        String type = StringUtils.defaultString(decision.getType());
        String commandId = decision.getCommandId();
        String commandShort = commandId == null || commandId.length() <= 8 ? commandId
                : commandId.substring(0, 4) + "…" + commandId.substring(commandId.length() - 4);
        return new RmaDetailPage.Decision(messageSource.getMessage("rma.detail.decision." + type, null, type, locale),
                dateTime(decision.getRecordedAt()), commandShort, commandId);
    }

    private static List<MarketplaceDecision> decisions(RMA rma) {
        return rma.getMarketplaceDecisions() == null ? List.of() : rma.getMarketplaceDecisions();
    }

    private String marketplaceStatus(RMA rma, Locale locale) {
        return rma.getExternalReturnStatus() == null ? null
                : text(locale, "MarketplaceReturnStatus." + rma.getExternalReturnStatus().name());
    }

    private static String reference(RMA rma) {
        return StringUtils.firstNonBlank(rma.getExternalReturnReference(), rma.getExternalReturnId());
    }

    private List<RmaDetailPage.Row> rows(RMA rma, List<RMAItem> items, Locale locale) {
        return IntStream.range(0, items.size()).mapToObj(i -> row(rma, i, items.get(i), locale)).toList();
    }

    private RmaDetailPage.Row row(RMA rma, int index, RMAItem item, Locale locale) {
        RMAItemStatus status = item.getStatus() == null ? RMAItemStatus.New : item.getStatus();
        return new RmaDetailPage.Row(index, item.getRmaItemId(), RmaAddressPage.shortId(item.getRmaItemId()),
                "/dashboard/rma/" + rma.getRmaId() + "/items/" + item.getRmaItemId(), item.getName(),
                StringUtils.trimToNull(item.getEan()), StringUtils.trimToNull(item.getSerialNo()),
                StringUtils.trimToNull(item.getReason()), status.name(), text(locale, "RMAItemStatus." + status.name()),
                RmaItemPageFactory.tone(status), resolution(item.getDesiredResolution(), locale),
                resolution(item.getActualResolution(), locale), item.getQty(), amount(locale, item.getPrice()),
                amount(locale, item.getTotalPrice()), item.getTotalPrice());
    }

    private static RmaDetailPage.SplitTarget splitTarget(RMA rma, boolean closed, List<RMAItem> items, String splitItemId) {
        if (closed || StringUtils.isBlank(splitItemId)) {
            return null;
        }
        return items.stream()
                .filter(i -> splitItemId.equals(i.getRmaItemId()) && i.getQty() >= 2)
                .findFirst()
                .map(i -> new RmaDetailPage.SplitTarget(i.getRmaItemId(), i.getName(), i.getQty(),
                        "/dashboard/rma/" + rma.getRmaId() + "/items/" + i.getRmaItemId() + "/split"))
                .orElse(null);
    }

    /** The dialog asked for, kept only when the page renders it: an unknown or unavailable one is ignored. */
    private static String openDialog(String open, RMA rma, RmaDetailPage.Header header, RmaDetailPage.Items items,
                                     boolean closed, RmaDetailPage.SplitTarget split) {
        if (open == null || closed) {
            return null;
        }
        if (open.startsWith(SHIPMENT_DIALOG) && !DIALOGS.contains(open)) {
            int shipments = rma.getShipments() == null ? 0 : rma.getShipments().size();
            String number = open.substring(SHIPMENT_DIALOG.length());
            return number.matches("[1-9]\\d{0,5}") && Integer.parseInt(number) <= shipments ? open : null;
        }
        boolean offered = switch (open) {
            case "next" -> header.next() != null;
            case "status" -> header.canChangeStatus();
            case "reject" -> header.canReject();
            case "add" -> items.canAdd();
            case "shipment-new" -> true;
            case "split" -> split != null;
            default -> false;
        };
        return offered ? open : null;
    }

    private static RmaDetailPage.Media media(RMA rma, boolean closed) {
        List<RmaDetailPage.Tile> tiles = mediaKeys(rma).stream().map(key -> {
            String fileName = key.substring(key.lastIndexOf('/') + 1);
            return new RmaDetailPage.Tile(key, fileName,
                    "/rma/" + rma.getRmaId() + "/media/" + UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8),
                    kind(fileName));
        }).toList();
        return new RmaDetailPage.Media(tiles, !closed);
    }

    /** The same extensions RMAController#getRmaMedia serves with their own content type. */
    static String kind(String fileName) {
        String extension = StringUtils.substringAfterLast(fileName, ".").toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg", "png", "gif" -> "image";
            case "mp4", "mov", "avi", "mkv" -> "video";
            case "pdf" -> "pdf";
            default -> "file";
        };
    }

    private static List<String> mediaKeys(RMA rma) {
        return rma.getMedia() == null ? List.of() : rma.getMedia().stream().filter(StringUtils::isNotBlank).toList();
    }

    private RmaDetailPage.Settings settings(RMA rma, boolean marketplace, Locale locale) {
        return new RmaDetailPage.Settings(StringUtils.trimToNull(rma.getEmail()), !marketplace,
                rma.isEmailNotificationsEnabled(), Money.inputRounded(rma.getShippingInsurance()),
                amount(locale, rma.getShippingInsurance()));
    }

    private static RmaDetailPage.Address address(RMA rma, boolean closed, ShippingDetails orderAddress, Locale locale) {
        ShippingDetails details = rma.getShippingDetails();
        boolean missing = !RmaAddressForm.hasAnyValue(details);
        List<String> lines = missing ? List.of() : Stream.of(joined(details.getName(), details.getSurname()),
                        details.getCompanyName(), details.getStreetAndNumber(),
                        Stream.of(joined(details.getPostalCode(), details.getCity()),
                                        CountryOptions.displayName(StringUtils.trimToNull(details.getCountry()), locale))
                                .filter(StringUtils::isNotBlank).collect(Collectors.joining(", ")))
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .toList();
        return new RmaDetailPage.Address(lines,
                missing ? null : StringUtils.trimToNull(details.getPhone()),
                missing ? null : StringUtils.trimToNull(details.getEmail()),
                missing, !missing && !details.isProperlyFilled(),
                "/dashboard/rma/" + rma.getRmaId() + "/shipping",
                closed ? null : RmaAddressPage.forDetails(rma, orderAddress));
    }

    private static String joined(String... parts) {
        return Stream.of(parts).filter(StringUtils::isNotBlank).map(String::trim).collect(Collectors.joining(" "));
    }

    private RmaDetailPage.History history(RMA rma, Locale locale) {
        List<Event> stored = rma.getEvents();
        List<RmaDetailPage.Event> events = new ArrayList<>();
        for (int i = stored.size() - 1; i >= 0; i--) {
            Event event = stored.get(i);
            events.add(new RmaDetailPage.Event(dateTime(event.getCreatedAt()), eventTitle(rma, event, locale)));
        }
        // the RMA's creation opens the timeline: without it the history seems to start in the middle
        if (rma.getCreatedAt() != null) {
            events.add(new RmaDetailPage.Event(dateTime(rma.getCreatedAt()), text(locale, "rma.history.created")));
        }
        return new RmaDetailPage.History(events, HISTORY_VISIBLE);
    }

    private String eventTitle(RMA rma, Event event, Locale locale) {
        String name = StringUtils.defaultString(event.getName());
        boolean email = event.getType() == EventType.email;
        if (!KNOWN_EVENTS.contains(name)) {
            return email ? text(locale, "rma.history.email", name) : text(locale, "rma.history.event.unknown", name);
        }
        String title = text(locale, "rma.history.event." + name,
                StringUtils.defaultIfBlank(rma.getMarketplace(), text(locale, "rma.detail.marketplace.unnamed")));
        return email ? text(locale, "rma.history.email", title) : title;
    }

    private static RmaDetailPage.CurrentFields current(RMA rma, RMAStatus status) {
        return new RmaDetailPage.CurrentFields(status.name(), rma.getEmail(), rma.getRejectionReason(),
                rma.isEmailNotificationsEnabled(), Money.input(rma.getShippingInsurance()), mediaKeys(rma));
    }

    private RmaDetailPage.AddOption addOption(OrderItem orderItem, Locale locale) {
        return new RmaDetailPage.AddOption(orderItem.getItemId(),
                text(locale, "rma.detail.add.option", orderItem.getName(), orderItem.getQty()),
                orderItem.getQty(), orderItem.getPrice());
    }

    private String resolution(RMAResolutionType type, Locale locale) {
        return type == null ? null : text(locale, "RMAResolutionType." + type.name());
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private String amount(Locale locale, double value) {
        return text(locale, "general.currency.amount", Money.format(value));
    }

    private String text(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
