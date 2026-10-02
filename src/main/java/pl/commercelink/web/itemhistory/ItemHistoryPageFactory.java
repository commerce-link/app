package pl.commercelink.web.itemhistory;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.history.*;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.warehouse.api.WarehouseItemView;
import pl.commercelink.web.orders.OrderFormats;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.orders.PluralForm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@RequiredArgsConstructor
public class ItemHistoryPageFactory {

    private static final String SEP = " · ";
    private static final Set<FulfilmentStatus> ITEM_STATUS_WORTH_SAYING =
            EnumSet.of(FulfilmentStatus.Returned, FulfilmentStatus.Replaced, FulfilmentStatus.InRMA, FulfilmentStatus.Destroyed);

    private final MessageSource messages;

    public ItemHistoryPage empty() {
        return ItemHistoryPage.empty();
    }

    public ItemHistoryPage of(ItemHistory history, Locale locale) {
        Texts t = new Texts(messages, locale);
        if (!history.found()) {
            return new ItemHistoryPage(history.serialNo(), true, false, null, null, null, null, List.of(),
                    null, null, null, null, null);
        }
        ItemAmbiguity ambiguity = history.ambiguity();
        List<ItemHistoryPage.Event> events = history.events().stream().map(e -> event(e, t)).toList();
        int rest = events.size() - ItemHistoryPage.VISIBLE_EVENTS;
        boolean collapsed = rest > 0;
        String note = history.totalEvents() > events.size()
                ? t.text("item.history.events.truncated", events.size(), history.totalEvents())
                : t.text("item.history.events.order");
        return new ItemHistoryPage(history.serialNo(), true, true,
                ambiguity.ambiguous() ? t.text("item.history.warning.counts", ambiguity.orderCount(), ambiguity.productCount()) : null,
                product(history, t), now(history.now(), ambiguity.ambiguous(), t), note, events,
                collapsed ? ItemHistoryPage.VISIBLE_EVENTS : null,
                collapsed ? t.text("order.history.more." + PluralForm.of(rest), rest) : null,
                collapsed ? t.text("order.history.shown", rest) : null,
                collapsed ? t.text("order.history.hidden", rest) : null,
                events.isEmpty() ? t.text("item.history.events.empty") : null);
    }

    private ItemHistoryPage.Product product(ItemHistory history, Texts t) {
        ItemIdentity identity = history.identity();
        String name = isNotBlank(identity.name()) ? identity.name() : t.text("item.history.product.unnamed");
        String title = identity.productCount() > 1 ? t.text("item.history.product.many", identity.productCount(), name) : name;
        return new ItemHistoryPage.Product(title, history.serialNo(), blankToNull(identity.ean()), blankToNull(identity.mfn()));
    }

    private ItemHistoryPage.Now now(ItemNow now, boolean ambiguous, Texts t) {
        List<String> parts = new ArrayList<>();
        String label = t.text("item.history.now." + now.state().name());
        String tone = OrderLabels.INFO;
        String linkText = null;
        String href = null;
        switch (now.state()) {
            case IN_RMA -> {
                RMAItem item = now.rmaLine().item();
                parts.add(t.text("item.history.record.rma", shortId(item.getRmaId())));
                if (item.getStatus() != null) parts.add(t.text("RMAItemStatus." + item.getStatus().name()));
                if (item.getDesiredResolution() != null) parts.add(t.text("item.history.fact.expected", t.text("RMAResolutionType." + item.getDesiredResolution().name())));
                tone = OrderLabels.WARN;
                linkText = "RMA";
                href = rmaHref(item.getRmaId());
            }
            case IN_ORDER, AT_CUSTOMER -> {
                Order order = now.orderLine().order();
                String status = order.getStatus() == null ? "" : t.text(OrderLabels.status(order.getStatus()));
                if (now.state() == ItemNow.State.IN_ORDER) {
                    parts.add(t.text("item.history.record.order", shortId(order.getOrderId())));
                    if (!status.isEmpty()) parts.add(status);
                    FulfilmentStatus itemStatus = now.orderLine().item().getStatus();
                    if (itemStatus != null) parts.add(t.text("item.history.fact.itemStatus", t.text(OrderLabels.itemStatus(itemStatus))));
                } else {
                    parts.add(t.text("item.history.now.text.atCustomer", shortId(order.getOrderId()), status));
                    if (now.orderLine().placedAt() != null) parts.add(OrderFormats.date(now.orderLine().placedAt()));
                    tone = OrderLabels.NEUTRAL;
                }
                linkText = t.text("item.history.record.order", "").trim();
                href = orderHref(order.getOrderId());
            }
            case IN_STOCK, RESERVED, INBOUND, WAREHOUSE_OTHER -> {
                WarehouseItemView item = now.warehouseItem();
                switch (now.state()) {
                    case IN_STOCK -> { parts.add(t.text("item.history.now.text.inStock")); tone = OrderLabels.OK; }
                    case RESERVED -> parts.add(t.text("item.history.now.text.reserved"));
                    case INBOUND -> parts.add(t.text("item.history.now.text.inbound"));
                    default -> {
                        label = item.getStatus() == null ? t.text("item.history.now.UNKNOWN") : t.text(OrderLabels.itemStatus(item.getStatus()));
                        tone = item.getStatus() == null ? OrderLabels.NEUTRAL : OrderLabels.tone(item.getStatus());
                    }
                }
                if (item.getCondition() != null) parts.add(t.text("item.history.fact.condition", t.text("ItemCondition." + item.getCondition().name())));
                linkText = t.text("item.history.record.warehouse");
                href = "/dashboard/warehouse/items/" + item.getItemId();
            }
            case UNKNOWN -> {
                parts.add(t.text("item.history.now.text.unknown"));
                tone = OrderLabels.NEUTRAL;
            }
        }
        if (ambiguous) parts.add(t.text("item.history.now.text.ambiguous"));
        return new ItemHistoryPage.Now(label, tone, String.join(SEP, parts), linkText, href);
    }

    private ItemHistoryPage.Event event(ItemHistoryEvent e, Texts t) {
        String at = e.at() == null ? null : OrderFormats.dateTime(e.at());
        String title = t.text("item.history.event." + e.type().name());
        List<String> facts = new ArrayList<>();
        return switch (e.type()) {
            case ORDER_PLACED -> {
                Order order = e.orderLine().order();
                OrderItem item = e.orderLine().item();
                String client = OrderPageModelFactory.clientName(order);
                if (isNotBlank(client)) facts.add(t.text("item.history.fact.client", client));
                if (item.getWarehouseItemId() != null) facts.add(t.text("item.history.fact.fromWarehouse"));
                if (ITEM_STATUS_WORTH_SAYING.contains(item.getStatus())) facts.add(t.text("item.history.fact.itemStatus", t.text(OrderLabels.itemStatus(item.getStatus()))));
                yield new ItemHistoryPage.Event(at, "fa-shopping-cart", title,
                        t.text("item.history.record.order", shortId(order.getOrderId())), orderHref(order.getOrderId()), order.getOrderId(),
                        order.getStatus() == null ? null : t.text(OrderLabels.status(order.getStatus())),
                        OrderLabels.tone(order.getStatus()), joined(facts));
            }
            case RMA_CREATED -> {
                RMAItem item = e.rmaLine().item();
                if (item.getDesiredResolution() != null) facts.add(t.text("item.history.fact.expected", t.text("RMAResolutionType." + item.getDesiredResolution().name())));
                if (item.getActualResolution() != null) facts.add(t.text("item.history.fact.actual", t.text("RMAResolutionType." + item.getActualResolution().name())));
                String rmaId = e.rmaLine().rma().getRmaId();
                yield new ItemHistoryPage.Event(at, "fa-undo-alt", title, t.text("item.history.record.rma", shortId(rmaId)),
                        rmaHref(rmaId), rmaId,
                        item.getStatus() == null ? null : t.text("RMAItemStatus." + item.getStatus().name()),
                        rmaTone(item.getStatus()), joined(facts));
            }
            case DELIVERY_ORDERED, DELIVERY_RECEIVED -> {
                Delivery d = e.delivery();
                if (isNotBlank(d.getProvider())) facts.add(t.text("item.history.fact.supplier", d.getProvider()));
                if (e.type() == ItemHistoryEvent.Type.DELIVERY_ORDERED && isNotBlank(d.getExternalDeliveryId()) && !d.isExternalDeliveryIdProvisional()) {
                    facts.add(t.text("item.history.fact.supplierRef", d.getExternalDeliveryId()));
                }
                yield new ItemHistoryPage.Event(at, "fa-truck", title, t.text("item.history.record.delivery", shortId(d.getDeliveryId())),
                        "/dashboard/deliveries/details?deliveryId=" + d.getDeliveryId(), d.getDeliveryId(), null, null, joined(facts));
            }
        };
    }

    static String rmaTone(RMAItemStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case New, Received -> OrderLabels.INFO;
            case SentForRepair -> OrderLabels.WARN;
            case ReturnedToClient -> OrderLabels.OK;
            case MovedToWarehouse -> OrderLabels.NEUTRAL;
        };
    }

    private static String shortId(String id) {
        return id == null || id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static String orderHref(String orderId) {
        return "/dashboard/orders/" + orderId;
    }

    private static String rmaHref(String rmaId) {
        return "/dashboard/rma/" + rmaId;
    }

    private static String joined(List<String> facts) {
        return facts.isEmpty() ? null : String.join(SEP, facts);
    }

    private static String blankToNull(String value) {
        return isNotBlank(value) ? value : null;
    }

    private record Texts(MessageSource messages, Locale locale) {
        String text(String key, Object... args) {
            return messages.getMessage(key, args, locale);
        }
    }
}
