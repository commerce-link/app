package pl.commercelink.web.rma;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.SerialNumbers;
import pl.commercelink.orders.rma.OpenRmaCoverage;
import pl.commercelink.orders.rma.RmaCreateService;
import pl.commercelink.orders.rma.RmaOrderSearch;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.web.dtos.RmaCreateForm;
import pl.commercelink.web.orders.AddressBlock;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderFormats;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderPageModelFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class RmaNewPageFactory {

    static final String FROM_ORDER = "order";
    private static final Pattern ITEM_FIELD = Pattern.compile("items(\\d+)\\.(\\w+)");

    private final OrderItemsRepository orderItems;
    private final OpenRmaCoverage coverage;
    private final MessageSource messages;

    public RmaNewPage build(String storeId, RmaOrderSearch.Result result, String from, Locale locale) {
        return build(storeId, result, from, null, locale);
    }

    /** serial: the serial number an order was chosen by from a list, so its item is ticked as when it is the only hit. */
    public RmaNewPage build(String storeId, RmaOrderSearch.Result result, String from, String serial, Locale locale) {
        return switch (result.kind()) {
            case EMPTY -> page(RmaNewPage.State.START, null, null, from, msg("rma.new.lead", locale), locale);
            case NOT_FOUND -> notFound(result, from, locale);
            case CHOICE -> choice(result, from, locale);
            case ORDER -> result.order().isEligibleForRMACreation()
                    ? orderPage(storeId, result.order(), result.query(), from, result.serialItemId(), serial, null, null, locale)
                    : ineligible(result, from, locale);
        };
    }

    public RmaNewPage rebuild(String storeId, Order order, RmaCreateForm form, RmaCreateService.Outcome outcome, Locale locale) {
        return orderPage(storeId, order, order.getOrderId(), form.getFrom(), null, null, form, outcome, locale);
    }

    private RmaNewPage page(RmaNewPage.State state, String query, Order order, String from, String lead, Locale locale) {
        // from=order travels with every search, also through a list or "not found", until an order is shown
        boolean fromOrder = FROM_ORDER.equals(from);
        boolean backToOrder = fromOrder && order != null;
        return new RmaNewPage(state, query,
                backToOrder ? "/dashboard/orders/" + order.getOrderId() : "/dashboard/rma",
                backToOrder ? msg("rma.new.back.order", locale, ConversionUtil.getShortenedId(order.getOrderId())) : msg("rma.new.back.list", locale),
                fromOrder ? FROM_ORDER : null, lead, null, null, null, null, List.of(), null, null, null, Map.of(), Map.of());
    }

    private RmaNewPage notFound(RmaOrderSearch.Result result, String from, Locale locale) {
        RmaNewPage base = page(RmaNewPage.State.NOT_FOUND, result.query(), null, from, null, locale);
        String desc = result.byEmail() ? msg("rma.new.notfound.desc.email", locale, result.query()) : msg("rma.new.notfound.desc", locale);
        return with(base, null, null, desc, null, List.of(), null, null, null, Map.of(), Map.of());
    }

    private RmaNewPage choice(RmaOrderSearch.Result result, String from, Locale locale) {
        // a list found by serial number (no order id starts with the query) keeps it, so the chosen order ticks that item
        String lowerQuery = result.query().toLowerCase(Locale.ROOT);
        String serial = !result.byEmail() && result.orders().stream().noneMatch(o -> o.getOrderId().startsWith(lowerQuery))
                ? result.query() : null;
        String keptFrom = FROM_ORDER.equals(from) ? FROM_ORDER : null;
        List<RmaNewPage.ChoiceRow> rows = result.orders().stream().map(o -> choiceRow(o, keptFrom, serial)).toList();
        String lead = result.byEmail()
                ? msg("rma.new.lead.choice.email", locale, result.query(), rows.size())
                : msg("rma.new.lead.choice.match", locale, rows.size());
        RmaNewPage.Choice choice = new RmaNewPage.Choice(
                msg(result.byEmail() ? "rma.new.choice.title.email" : "rma.new.choice.title.match", locale), rows,
                result.truncated() ? msg("rma.new.choice.limit", locale, RmaOrderSearch.CHOICE_LIMIT) : null);
        RmaNewPage base = page(RmaNewPage.State.CHOICE, result.query(), null, from, lead, locale);
        return with(base, null, choice, null, null, List.of(), null, null, null, Map.of(), Map.of());
    }

    private RmaNewPage.ChoiceRow choiceRow(Order order, String from, String serial) {
        boolean eligible = order.isEligibleForRMACreation();
        String unavailable = eligible ? null : order.hasStatus(OrderStatus.Cancelled)
                ? "rma.new.choice.unavailable.cancelled" : "rma.new.choice.unavailable.early";
        return new RmaNewPage.ChoiceRow(ConversionUtil.getShortenedId(order.getOrderId()), order.getOrderId(),
                eligible ? searchHref(order.getOrderId(), from, serial) : null, OrderFormats.date(order.getOrderedAt()),
                OrderLabels.status(order.getStatus()), OrderLabels.tone(order.getStatus()), Money.format(order.getTotalPrice()),
                unavailable);
    }

    private RmaNewPage ineligible(RmaOrderSearch.Result result, String from, Locale locale) {
        Order order = result.order();
        String shortId = ConversionUtil.getShortenedId(order.getOrderId());
        boolean cancelled = order.hasStatus(OrderStatus.Cancelled);
        String status = msg(OrderLabels.status(order.getStatus()), locale);
        RmaNewPage.Ineligible ineligible = new RmaNewPage.Ineligible(
                cancelled ? "rma.new.ineligible.title.cancelled" : "rma.new.ineligible.title.early",
                cancelled ? msg("rma.new.ineligible.desc.cancelled", locale, shortId) : msg("rma.new.ineligible.desc.early", locale, shortId, status),
                "/dashboard/orders/" + order.getOrderId(), msg("rma.new.ineligible.open", locale, shortId));
        RmaNewPage base = page(RmaNewPage.State.INELIGIBLE, result.query(), order, from, null, locale);
        return with(base, header(order, locale), null, null, ineligible, List.of(), null, null, null, Map.of(), Map.of());
    }

    private RmaNewPage orderPage(String storeId, Order order, String query, String from, String serialItemId, String serial,
                                 RmaCreateForm form, RmaCreateService.Outcome outcome, Locale locale) {
        List<OrderItem> reportable = orderItems.findByOrderIdAndStatuses(order.getOrderId(), RmaCreateService.REPORTABLE);
        if (serialItemId == null && serial != null) {
            serialItemId = reportable.stream().filter(i -> SerialNumbers.contains(i.getSerialNo(), serial))
                    .map(OrderItem::getItemId).findFirst().orElse(null);
        }
        Map<String, String> claims = coverage.claims(storeId, reportable.stream().map(OrderItem::getItemId).toList());
        List<RmaNewPage.ItemRow> rows = new ArrayList<>();
        for (int i = 0; i < reportable.size(); i++) {
            OrderItem item = reportable.get(i);
            RmaCreateForm.Item posted = form == null ? null : postedFor(form, item.getItemId());
            String claimedBy = claims.get(item.getItemId());
            boolean selected = claimedBy == null && (posted != null ? posted.isSelected() : item.getItemId().equals(serialItemId));
            rows.add(new RmaNewPage.ItemRow(i, item.getItemId(), item.getName(), sub(item, locale), item.isService(),
                    item.getQty(), postedQty(posted), selected,
                    posted != null && posted.getResolution() != null ? posted.getResolution().name() : null,
                    posted != null ? posted.getReason() : null, Money.format(item.getPrice()), item.getPrice(),
                    claimedBy == null ? null : ConversionUtil.getShortenedId(claimedBy),
                    claimedBy == null ? null : "/dashboard/rma/" + claimedBy));
        }
        boolean anyFree = rows.stream().anyMatch(r -> !r.claimed());
        String emptyText = reportable.isEmpty() ? msg("rma.new.items.none", locale)
                : anyFree ? null : msg("rma.new.items.allClaimed", locale);
        List<RmaNewPage.ItemRow> chosen = rows.stream().filter(RmaNewPage.ItemRow::selected).toList();
        int pieces = chosen.stream().mapToInt(r -> parse(r.qty())).sum();
        double value = chosen.stream().mapToDouble(r -> r.unitPrice() * parse(r.qty())).sum();
        RmaNewPage.Summary summary = new RmaNewPage.Summary(form != null ? form.getEmail() : order.getEmail(),
                chosen.size(), pieces, Money.format(value), anyFree);

        Map<String, String> summaryErrors = new LinkedHashMap<>();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        String pageError = null;
        if (outcome != null) {
            pageError = outcome.pageErrorKey() == null ? null : msg(outcome.pageErrorKey(), locale);
            for (RmaCreateForm.Error error : outcome.errors()) {
                String text = messages.getMessage(error.key(), error.args(), locale);
                String field = rowField(error.field(), form, rows);
                fieldErrors.put(field, text);
                summaryErrors.put(field, error.itemName() == null ? text : error.itemName() + " — " + text);
            }
        }
        String lead = msg("rma.new.lead.order", locale);
        RmaNewPage base = page(RmaNewPage.State.ORDER, query, order, from, lead, locale);
        return with(base, header(order, locale), null, null, null, rows, emptyText, summary, pageError, summaryErrors, fieldErrors);
    }

    /**
     * The posted form names an item field by its index in the request; the page prints the fields by the item's row.
     * They differ when the order's items changed after the page was opened (or the request was crafted), so the error
     * is moved to the row of the same item, which keeps the summary's links pointing at fields that exist.
     */
    private static String rowField(String field, RmaCreateForm form, List<RmaNewPage.ItemRow> rows) {
        Matcher m = field == null ? null : ITEM_FIELD.matcher(field);
        if (m == null || !m.matches() || form == null) {
            return field;
        }
        int posted = Integer.parseInt(m.group(1));
        if (posted >= form.getItems().size() || form.getItems().get(posted) == null) {
            return field;
        }
        String itemId = form.getItems().get(posted).getItemId();
        return rows.stream().filter(r -> r.itemId().equals(itemId)).findFirst()
                .map(r -> RmaCreateForm.field(r.index(), m.group(2))).orElse(field);
    }

    private RmaNewPage.Header header(Order order, Locale locale) {
        String source = order.isMarketplaceOrder() && order.getSource() != null && order.getSource().getName() != null
                ? order.getSource().getName() : msg("rma.new.order.source.shop", locale);
        return new RmaNewPage.Header(order.getOrderId(), ConversionUtil.getShortenedId(order.getOrderId()),
                "/dashboard/orders/" + order.getOrderId(), OrderLabels.status(order.getStatus()),
                OrderLabels.tone(order.getStatus()), OrderFormats.date(order.getOrderedAt()),
                client(order), source);
    }

    // "name or company · city"; without either, the e-mail the order page falls back to
    static String client(Order order) {
        String person = Stream.of(AddressBlock.of(order.getShippingDetails(), Locale.ROOT).companyOrPerson(),
                AddressBlock.of(order.getBillingDetails(), Locale.ROOT).companyOrPerson()).filter(Objects::nonNull).findFirst().orElse(null);
        if (person == null) {
            return OrderPageModelFactory.clientName(order);
        }
        String city = Stream.of(order.getShippingDetails() == null ? null : order.getShippingDetails().getCity(),
                        order.getBillingDetails() == null ? null : order.getBillingDetails().getCity())
                .filter(StringUtils::isNotBlank).map(String::trim).findFirst().orElse(null);
        return city == null ? person : person + " · " + city;
    }

    private String sub(OrderItem item, Locale locale) {
        if (item.isService()) {
            return msg("rma.new.items.service", locale);
        }
        return Stream.of(item.getEan() == null || item.getEan().isBlank() ? null : msg("rma.new.items.ean", locale, item.getEan()),
                        item.getSerialNo() == null || item.getSerialNo().isBlank() ? null : msg("rma.new.items.serial", locale, item.getSerialNo()))
                .filter(s -> s != null).collect(Collectors.joining(" · "));
    }

    private static RmaCreateForm.Item postedFor(RmaCreateForm form, String itemId) {
        return form.getItems().stream().filter(i -> itemId.equals(i.getItemId())).findFirst().orElse(null);
    }

    // the script disables the quantity of an unticked item, so it is not posted; ticking it again after a rejected
    // submit must not start from an empty field
    private static String postedQty(RmaCreateForm.Item posted) {
        return posted == null || posted.getQty() == null ? "1" : posted.getQty();
    }

    private static int parse(String qty) {
        try {
            return Math.max(0, Integer.parseInt(qty == null ? "" : qty.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String searchHref(String orderId) {
        return searchHref(orderId, null, null);
    }

    static String searchHref(String orderId, String from, String serial) {
        UriComponentsBuilder href = UriComponentsBuilder.fromPath("/dashboard/rma/new").queryParam("q", "{q}");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("q", orderId);
        if (from != null) {
            href.queryParam("from", "{from}");
            values.put("from", from);
        }
        if (serial != null) {
            href.queryParam("serial", "{serial}");
            values.put("serial", serial);
        }
        return href.encode().buildAndExpand(values).toUriString();
    }

    private static RmaNewPage with(RmaNewPage base, RmaNewPage.Header order, RmaNewPage.Choice choice, String notFoundDesc,
                                   RmaNewPage.Ineligible ineligible, List<RmaNewPage.ItemRow> items, String itemsEmptyText,
                                   RmaNewPage.Summary summary, String pageError, Map<String, String> errorSummary,
                                   Map<String, String> fieldErrors) {
        return new RmaNewPage(base.state(), base.query(), base.backHref(), base.backLabel(), base.from(), base.leadText(),
                order, choice, notFoundDesc, ineligible, items, itemsEmptyText, summary, pageError, errorSummary, fieldErrors);
    }

    private String msg(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
