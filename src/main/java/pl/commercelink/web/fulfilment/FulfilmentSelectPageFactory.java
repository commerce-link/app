package pl.commercelink.web.fulfilment;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.fulfilment.FulfilmentAllocation;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.FulfilmentGroup;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.fulfilment.FulfilmentVariant;
import pl.commercelink.orders.fulfilment.UnmatchedItem;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.fulfilment.FulfilmentSelectPage.*;
import pl.commercelink.web.orders.OrderRow;
import pl.commercelink.web.orders.OrderRowMapper;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class FulfilmentSelectPageFactory {

    /** Supplier colours cycle through --cl-supplier-1..6; 0 is the store's warehouse. */
    private static final int PALETTE = 6;
    private static final String RESTOCK_COMMIT = "/dashboard/warehouse/fulfilment/commit";

    private final MessageSource messages;
    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;

    public FulfilmentSelectPage forOrders(FulfilmentForm form, String storeId, boolean superAdmin, SupplierLabelMap labels,
                                          LocalDate today, Locale locale) {
        SupplierLabelMap names = labels.withWarehouse(text("fulfilment.select.warehouse", locale));
        List<String> selected = form.getSelectedOrders() == null ? List.of() : form.getSelectedOrders();
        List<String> stepOrders = form.isOrderByOrder() && !selected.isEmpty() ? List.of(selected.get(0)) : selected;
        OrderRowMapper mapper = new OrderRowMapper(messages, locale, false);

        Map<String, Order> orders = new LinkedHashMap<>();
        stepOrders.stream().distinct().forEach(id -> {
            Order order = ordersRepository.findById(storeId, id);
            if (order != null) {
                orders.put(id, order);
            }
        });
        Map<String, Integer> itemsPerOrder = itemsPerOrder(form);
        int itemsTotal = itemsPerOrder.values().stream().mapToInt(Integer::intValue).sum();
        List<OrderRef> refs = stepOrders.stream().distinct()
                .map(id -> new OrderRef(id, number(id, orders.get(id), mapper, today), orderHref(storeId, id, superAdmin),
                        itemsPerOrder.getOrDefault(id, 0)))
                .sorted(Comparator.comparing(OrderRef::number))
                .toList();

        Kind kind = kind(stepOrders, orders);
        Map<String, Integer> colours = colours(form, names);
        String base = superAdmin ? "/dashboard/store/" + storeId + "/orders/fulfilment/" : "/dashboard/orders/fulfilment/";
        Actions actions = form.isOrderByOrder()
                ? new Actions(base + "commit", null, base + "skip")
                : new Actions(base + "commit", base + "commitAndContinue", null);
        Step step = step(form, selected);
        boolean narrowed = form.isOnlyWithProfit() || form.isOnlyMultiOrder() || form.isOnlyLocalSuppliers();
        EmptyState empty = form.getEntries().isEmpty() && form.getUnmatched().isEmpty() ? EmptyState.NOTHING_LEFT : EmptyState.NONE;
        String commitHelp = step != null && !step.last() ? "fulfilment.select.commit.help.next" : "fulfilment.select.commit.help";

        return new FulfilmentSelectPage(Mode.ORDERS, kind, superAdmin ? storeName(storeId) : null,
                context(kind, refs, orders, mapper, today, itemsTotal, locale), strategyKey(form.getPathSelector()),
                narrowingKeys(form), step, SkippedGroups.from(nonNull(form.getSkippedOrderIds()), form.getSkippedGroups()).queueHref(),
                actions, refs, categories(form, names, colours), suppliers(form, names, colours), committed(form, names, colours),
                variants(form, names), missing(form, refs, storeId, superAdmin), true, itemsTotal, empty,
                narrowed ? "fulfilment.select.noMatch.narrowed" : "fulfilment.select.noMatch",
                form.isOnlyMultiOrder() && stepOrders.size() == 1, commitHelp);
    }

    public FulfilmentSelectPage forRestock(FulfilmentForm form, SupplierLabelMap labels, boolean profitKnown, Locale locale) {
        Map<String, Integer> colours = colours(form, labels);
        int products = (int) form.getEntries().stream()
                .flatMap(group -> group.getAllocations().stream())
                .map(allocation -> allocation.getKey().getId())
                .distinct().count();
        EmptyState empty = form.getEntries().isEmpty() ? EmptyState.RESTOCK_EMPTY : EmptyState.NONE;
        return new FulfilmentSelectPage(Mode.RESTOCK, Kind.RESTOCK, null, text("fulfilment.select.context.restock", locale, products),
                null, List.of(), null, "/dashboard/warehouse", new Actions(RESTOCK_COMMIT, null, null), List.of(),
                categories(form, labels, colours), suppliers(form, labels, colours), List.of(), List.of(), List.of(),
                profitKnown, products, empty, null, false, "fulfilment.select.commit.help.restock");
    }

    private static List<String> nonNull(List<String> list) {
        return list == null ? List.of() : list;
    }

    /** Waiting items per order: those an offer covers plus those none does, each counted once. */
    private static Map<String, Integer> itemsPerOrder(FulfilmentForm form) {
        Map<String, Set<String>> items = new LinkedHashMap<>();
        for (FulfilmentGroup group : form.getEntries()) {
            for (FulfilmentAllocation allocation : group.getAllocations()) {
                items.computeIfAbsent(allocation.getOrderId(), k -> new HashSet<>()).add(allocation.getOrderItemId());
            }
        }
        for (UnmatchedItem item : form.getUnmatched()) {
            items.computeIfAbsent(item.orderId(), k -> new HashSet<>()).add(item.itemId());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        items.forEach((order, set) -> counts.put(order, set.size()));
        return counts;
    }

    private static Kind kind(List<String> stepOrders, Map<String, Order> orders) {
        boolean allWarehouse = !orders.isEmpty() && orders.size() == stepOrders.stream().distinct().count()
                && orders.values().stream().allMatch(o -> o.getFulfilmentType() == FulfilmentType.WarehouseFulfilment);
        if (allWarehouse) {
            return Kind.WAREHOUSE;
        }
        // A single order we cannot read is not known to be dropship either.
        return stepOrders.stream().distinct().count() == 1 && !orders.isEmpty() ? Kind.DROPSHIP : Kind.MIXED;
    }

    private String context(Kind kind, List<OrderRef> refs, Map<String, Order> orders, OrderRowMapper mapper, LocalDate today,
                           int itemsTotal, Locale locale) {
        if (refs.size() == 1) {
            OrderRef ref = refs.get(0);
            Order order = orders.get(ref.orderId());
            String client = order == null ? "" : client(mapper.map(order, today));
            return client.isEmpty()
                    ? text("fulfilment.select.context.single.noClient", locale, ref.number(), itemsTotal)
                    : text("fulfilment.select.context.single", locale, ref.number(), client, itemsTotal);
        }
        if (kind == Kind.WAREHOUSE) {
            return text("fulfilment.select.context.warehouse", locale, refs.size(), itemsTotal);
        }
        return text("fulfilment.select.context.orders", locale, refs.size(), itemsTotal);
    }

    private static String client(OrderRow row) {
        return Stream.of(row.clientName(), row.clientCity()).filter(StringUtils::isNotBlank).collect(Collectors.joining(", "));
    }

    private static String number(String orderId, Order order, OrderRowMapper mapper, LocalDate today) {
        if (order != null) {
            return mapper.map(order, today).number();
        }
        return orderId == null ? "" : ConversionUtil.getShortenedId(orderId);
    }

    private static String orderHref(String storeId, String orderId, boolean superAdmin) {
        return superAdmin ? "/dashboard/store/" + storeId + "/orders/" + orderId : "/dashboard/orders/" + orderId;
    }

    private static Step step(FulfilmentForm form, List<String> selected) {
        if (!form.isOrderByOrder() || selected.isEmpty()) {
            return null;
        }
        int total = Math.max(form.getOrderCountAtStart(), selected.size());
        return new Step(total - selected.size() + 1, total, !form.hasRemainingOrders());
    }

    private static String strategyKey(String pathSelector) {
        if ("suggest".equals(pathSelector) || "suggest-exact".equals(pathSelector)) {
            return "fulfilment.select.strategy.suggest";
        }
        return "fulfilment.select.strategy.default";
    }

    private static List<String> narrowingKeys(FulfilmentForm form) {
        List<String> keys = new ArrayList<>();
        if (form.isOnlyWithProfit()) {
            keys.add("fulfilment.queue.narrow.profit");
        }
        if (form.isOnlyLocalSuppliers()) {
            keys.add("fulfilment.queue.narrow.local");
        }
        if (form.isOnlyMultiOrder()) {
            keys.add("fulfilment.queue.narrow.multi");
        }
        if (form.isOrderByOrder()) {
            keys.add("fulfilment.queue.narrow.byOrder");
        }
        return keys;
    }

    /** Suppliers on the page and in earlier steps, sorted by label, take colours 1..6 in turn; the warehouse takes 0. */
    private static Map<String, Integer> colours(FulfilmentForm form, SupplierLabelMap names) {
        Set<String> providers = new LinkedHashSet<>();
        form.getEntries().forEach(group -> providers.add(group.getSource().getProvider()));
        if (form.getCommittedSuppliers() != null) {
            providers.addAll(form.getCommittedSuppliers().keySet());
        }
        List<String> sorted = providers.stream()
                .filter(p -> !SupplierRegistry.WAREHOUSE.equals(p))
                .sorted(Comparator.comparing((String p) -> String.valueOf(names.of(p)), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        Map<String, Integer> colours = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            colours.put(sorted.get(i), i % PALETTE + 1);
        }
        colours.put(SupplierRegistry.WAREHOUSE, 0);
        return colours;
    }

    private static List<Category> categories(FulfilmentForm form, SupplierLabelMap names, Map<String, Integer> colours) {
        Map<String, List<FulfilmentGroup>> byCategory = new LinkedHashMap<>();
        for (FulfilmentGroup group : form.getEntries()) {
            String category = group.getSource().getCategory();
            byCategory.computeIfAbsent(StringUtils.isBlank(category) ? "" : category, k -> new ArrayList<>()).add(group);
        }
        List<String> keys = byCategory.keySet().stream()
                .sorted(Comparator.comparing(String::isEmpty).thenComparing(String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Category> categories = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            List<Offer> offers = byCategory.get(key).stream()
                    // stable: equal target prices keep the generator's order, which the page uses to break price ties
                    .sorted(Comparator.comparingDouble(FulfilmentGroup::getTargetPrice).reversed())
                    .map(group -> offer(group, names, colours))
                    .toList();
            categories.add(new Category("offers-category-" + (i + 1), key.isEmpty() ? null : key, offers));
        }
        return categories;
    }

    private static Offer offer(FulfilmentGroup group, SupplierLabelMap names, Map<String, Integer> colours) {
        String provider = group.getSource().getProvider();
        return new Offer(group, names.of(provider), colours.getOrDefault(provider, 0), SupplierRegistry.WAREHOUSE.equals(provider));
    }

    private static List<SupplierRow> suppliers(FulfilmentForm form, SupplierLabelMap names, Map<String, Integer> colours) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        form.getEntries().forEach(group -> counts.merge(group.getSource().getProvider(), 1, Integer::sum));
        return counts.entrySet().stream()
                .map(e -> new SupplierRow(e.getKey(), names.of(e.getKey()), colours.getOrDefault(e.getKey(), 0),
                        SupplierRegistry.WAREHOUSE.equals(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(SupplierRow::warehouse).thenComparing(SupplierRow::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<SupplierTotal> committed(FulfilmentForm form, SupplierLabelMap names, Map<String, Integer> colours) {
        if (form.getCommittedSuppliers() == null) {
            return List.of();
        }
        return form.getCommittedSuppliers().entrySet().stream()
                .map(e -> new SupplierTotal(e.getKey(), names.of(e.getKey()), colours.getOrDefault(e.getKey(), 0),
                        SupplierRegistry.WAREHOUSE.equals(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingDouble(SupplierTotal::amount).reversed())
                .toList();
    }

    private static List<VariantOption> variants(FulfilmentForm form, SupplierLabelMap names) {
        Set<String> accepted = form.getEntries().stream().filter(FulfilmentGroup::isAccepted).map(FulfilmentGroup::getId)
                .collect(Collectors.toSet());
        List<VariantOption> options = new ArrayList<>();
        boolean appliedFound = false;
        for (FulfilmentVariant variant : form.getVariants()) {
            boolean applied = !appliedFound && new HashSet<>(variant.getGroupIds()).equals(accepted);
            appliedFound |= applied;
            options.add(new VariantOption(String.join(" ", variant.getGroupIds()), variant.getSupplierCount(),
                    variant.getEstimatedTotal(), variant.isCheapest(), variant.isFewestSuppliers(),
                    variant.getProviders().stream().map(names::of).collect(Collectors.joining(", ")), applied));
        }
        return options;
    }

    private static List<Missing> missing(FulfilmentForm form, List<OrderRef> refs, String storeId, boolean superAdmin) {
        Map<String, OrderRef> byId = refs.stream().collect(Collectors.toMap(OrderRef::orderId, r -> r, (a, b) -> a));
        Map<String, List<UnmatchedItem>> byOrder = form.getUnmatched().stream()
                .collect(Collectors.groupingBy(UnmatchedItem::orderId, LinkedHashMap::new, Collectors.toList()));
        return byOrder.entrySet().stream()
                .map(entry -> {
                    String orderId = entry.getKey();
                    OrderRef ref = byId.get(orderId);
                    String number = ref != null ? ref.number() : ConversionUtil.getShortenedId(orderId);
                    String href = ref != null ? ref.href() : orderHref(storeId, orderId, superAdmin);
                    boolean narrowed = entry.getValue().stream().anyMatch(item -> item.reason() == UnmatchedItem.Reason.NARROWED);
                    return new Missing(orderId, number, href, entry.getValue().size(), narrowed);
                })
                .toList();
    }

    private String storeName(String storeId) {
        Store store = storesRepository.findById(storeId);
        return store == null || StringUtils.isBlank(store.getName()) ? storeId : store.getName();
    }

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
