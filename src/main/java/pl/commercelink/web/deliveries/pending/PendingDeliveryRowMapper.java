package pl.commercelink.web.deliveries.pending;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.web.util.UriUtils;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.deliveries.SupplierOrderingModes.OrderingMode;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Builds the rows of the pending deliveries page; created once per request, like DeliveryRowMapper. */
public class PendingDeliveryRowMapper {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSource messages;
    private final Locale locale;
    private final SupplierLabelMap labels;
    private final Map<String, Order> orders;
    private final Map<String, OrderingMode> modes;
    private final String storeId;
    private final boolean superAdmin;
    private final DecimalFormat amount;

    public PendingDeliveryRowMapper(MessageSource messages, Locale locale, SupplierLabelMap labels, Map<String, Order> orders,
                                    Map<String, OrderingMode> modes, String storeId, boolean superAdmin) {
        this.messages = messages;
        this.locale = locale;
        this.labels = labels;
        this.orders = orders;
        this.modes = modes;
        this.storeId = storeId;
        this.superAdmin = superAdmin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        this.amount = new DecimalFormat("#,##0.00", symbols);
    }

    public PendingDeliveryRow warehouse(Delivery delivery, LocalDate today) {
        String provider = delivery.getProvider();
        List<Allocation> allocations = delivery.getAllocations() == null ? List.of() : delivery.getAllocations();
        List<String> orderIds = allocations.stream()
                .filter(a -> a.getType() == AllocationType.Order)
                .map(a -> a.getKey().getOrderId())
                .filter(Objects::nonNull)
                .distinct().sorted().toList();
        boolean restock = allocations.stream().anyMatch(a -> a.getType() == AllocationType.Warehouse);
        String source = !orderIds.isEmpty() && restock ? text("deliveries.pending.source.both", orderIds.size())
                : !orderIds.isEmpty() ? text("deliveries.pending.source.orders", orderIds.size())
                : text("deliveries.pending.source.restock");
        return row(Kind.WAREHOUSE, labels.of(storeId, provider), null, null, provider, source,
                delivery.hasDirectToConsumerAllocations(), orderIds,
                delivery.getItems() == null ? List.of() : delivery.getItems(), true,
                base() + "/deliveries/create/" + UriUtils.encodePathSegment(provider, UTF_8),
                "pending-w-" + slug(provider), today);
    }

    public PendingDeliveryRow dropship(DropshipCandidate candidate, LocalDate today) {
        String orderId = candidate.orderId();
        String provider = candidate.provider();
        return row(Kind.DROPSHIP, StringUtils.substringBefore(orderId, "-"), base() + "/orders/" + orderId,
                customer(orders.get(orderId)), provider, null, false, List.of(orderId), candidate.items(), false,
                base() + "/orders/" + orderId + "/dropship?provider=" + URLEncoder.encode(provider, UTF_8),
                "pending-d-" + slug(orderId + "-" + provider), today);
    }

    public String amount(double net) {
        return text("general.currency.amount", amount.format(net));
    }

    private PendingDeliveryRow row(Kind kind, String key, String keyHref, String customer, String provider, String source,
                                   boolean forward, List<String> orderIds, List<DeliveryItem> items, boolean withSources,
                                   String createHref, String detailId, LocalDate today) {
        LocalDate due = orderIds.stream().map(orders::get).filter(Objects::nonNull)
                .map(Order::getShippingDueAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        List<DeliveryItem> sorted = items.stream()
                .sorted(Comparator.comparing(DeliveryItem::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        int pieces = sorted.stream().mapToInt(DeliveryItem::getOrderedQty).sum();
        double cost = sorted.stream().mapToDouble(i -> i.getOrderedQty() * i.getUnitCost()).sum();
        OrderingMode mode = modes.getOrDefault(provider, OrderingMode.MANUAL);
        String modeText = text(mode.api() ? "deliveries.pending.mode.api" : "deliveries.pending.mode.manual")
                + (mode.approval() ? " · " + text("deliveries.pending.mode.approval") : "");
        String providerLabel = labels.of(storeId, provider);
        return new PendingDeliveryRow(kind, key, keyHref, customer, provider, providerLabel, source, forward, pieces,
                text("deliveries.pending.pieces", pieces), due, dueNote(due, today), dueTone(due, today), mode.api(),
                mode.approval(), modeText, cost, amount(cost), createHref, detailId,
                sorted.stream().map(i -> item(i, withSources)).toList(), orderIds,
                searchText(orderIds, providerLabel, sorted));
    }

    private PendingDeliveryRow.Item item(DeliveryItem item, boolean withSources) {
        return new PendingDeliveryRow.Item(item.getName(),
                text("deliveries.pending.detail.codes", dash(item.getMfn()), dash(item.getEan())),
                text("deliveries.pending.pieces", item.getOrderedQty()), amount(item.getUnitCost()),
                amount(item.getOrderedQty() * item.getUnitCost()),
                withSources ? sources(item.getAllocations() == null ? List.of() : item.getAllocations()) : List.of());
    }

    private List<PendingDeliveryRow.Source> sources(List<Allocation> allocations) {
        Map<String, Integer> byOrder = new LinkedHashMap<>();
        int warehouseQty = 0;
        String firstWarehouseItem = null;
        for (Allocation allocation : allocations) {
            if (allocation.getType() == AllocationType.Warehouse) {
                warehouseQty += allocation.getQty();
                if (firstWarehouseItem == null) {
                    firstWarehouseItem = allocation.getKey().getItemId();
                }
            } else if (allocation.getKey() != null && allocation.getKey().getOrderId() != null) {
                byOrder.merge(allocation.getKey().getOrderId(), allocation.getQty(), Integer::sum);
            }
        }
        List<PendingDeliveryRow.Source> sources = new ArrayList<>();
        byOrder.forEach((orderId, qty) -> sources.add(new PendingDeliveryRow.Source(
                StringUtils.substringBefore(orderId, "-"), base() + "/orders/" + orderId, qty, false)));
        if (warehouseQty > 0) {
            // the super admin has no store-scoped route to a warehouse item
            String href = superAdmin || firstWarehouseItem == null ? null : "/dashboard/warehouse/items/" + firstWarehouseItem;
            sources.add(new PendingDeliveryRow.Source(text("deliveries.pending.source.warehouse"), href, warehouseQty, true));
        }
        return sources;
    }

    private String dueNote(LocalDate due, LocalDate today) {
        if (due == null) {
            return text("deliveries.pending.due.none");
        }
        long days = ChronoUnit.DAYS.between(due, today);
        if (days > 1) return text("deliveries.list.due.overdue", days);
        if (days == 1) return text("deliveries.list.due.overdue.one");
        if (days == 0) return text("deliveries.list.due.today");
        if (days == -1) return text("deliveries.pending.due.tomorrow");
        return text("deliveries.pending.due.date", (due.getYear() == today.getYear() ? SAME_YEAR : OTHER_YEAR).format(due));
    }

    private static String dueTone(LocalDate due, LocalDate today) {
        if (due == null) return "is-none";
        if (due.isBefore(today)) return "is-bad";
        return due.isEqual(today) ? "is-warn" : "";
    }

    private String customer(Order order) {
        if (order == null || order.getBillingDetails() == null) {
            return null;
        }
        BillingDetails billing = order.getBillingDetails();
        return StringUtils.isNotBlank(billing.getName()) ? billing.getName().trim() : StringUtils.trimToNull(billing.getEmail());
    }

    private String searchText(List<String> orderIds, String providerLabel, List<DeliveryItem> items) {
        Stream<String> customers = orderIds.stream().map(orders::get).filter(Objects::nonNull)
                .map(Order::getBillingDetails).filter(Objects::nonNull)
                .flatMap(b -> Stream.of(b.getName(), b.getEmail()));
        Stream<String> products = items.stream().flatMap(i -> Stream.of(i.getName(), i.getMfn(), i.getEan()));
        return Stream.concat(Stream.concat(customers, products), Stream.of(providerLabel))
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("\n"));
    }

    private String base() {
        return superAdmin ? "/dashboard/store/" + storeId : "/dashboard";
    }

    /** A valid, stable HTML id part: unsafe characters replaced, plus a hash so two similar identities never collide. */
    private static String slug(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_") + "-" + Integer.toHexString(value.hashCode());
    }

    private static String dash(String value) {
        return StringUtils.isBlank(value) ? "—" : value;
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
