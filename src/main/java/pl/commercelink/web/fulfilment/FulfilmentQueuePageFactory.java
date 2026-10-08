package pl.commercelink.web.fulfilment;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderRow;
import pl.commercelink.web.orders.OrderRowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FulfilmentQueuePageFactory {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final MessageSource messages;
    private final StoresRepository storesRepository;

    public FulfilmentQueuePage build(boolean superAdmin, List<String> skipped, List<OrderIndexEntry> group,
                                     Map<String, Order> orders, Map<String, Integer> itemsToOrder,
                                     LocalDate today, Locale locale) {
        Set<String> skippedIds = new LinkedHashSet<>(skipped);
        if (group.isEmpty()) {
            FulfilmentQueuePage.EmptyState empty = skippedIds.isEmpty()
                    ? FulfilmentQueuePage.EmptyState.NONE_WAITING : FulfilmentQueuePage.EmptyState.ALL_SKIPPED;
            return new FulfilmentQueuePage(null, null, List.of(), skippedIds.size(), List.copyOf(skippedIds), null, 0,
                    empty, superAdmin);
        }

        String storeId = group.get(0).getStoreId();
        // the documents flag only drives the WZ/FV marks of the orders list, which this page does not show
        OrderRowMapper mapper = new OrderRowMapper(messages, locale, false);
        List<FulfilmentQueueRow> rows = new ArrayList<>();
        Set<String> skipOrderIds = new LinkedHashSet<>(skippedIds);
        int itemsTotal = 0;
        for (OrderIndexEntry entry : group) {
            int items = itemsToOrder.getOrDefault(entry.getOrderId(), 0);
            itemsTotal += items;
            skipOrderIds.add(entry.getOrderId());
            rows.add(row(entry, orders.get(entry.getOrderId()), items, superAdmin, mapper, today));
        }

        FulfilmentQueuePage.GroupKind kind = group.get(0).getFulfilmentType() == FulfilmentType.WarehouseFulfilment
                ? FulfilmentQueuePage.GroupKind.WAREHOUSE : FulfilmentQueuePage.GroupKind.DROPSHIP;
        String postAction = superAdmin ? "/dashboard/store/" + storeId + "/orders/fulfilment" : "/dashboard/orders/fulfilment";
        return new FulfilmentQueuePage(kind, superAdmin ? storeName(storeId) : null, rows, skippedIds.size(),
                List.copyOf(skipOrderIds), postAction, itemsTotal, null, superAdmin);
    }

    private FulfilmentQueueRow row(OrderIndexEntry entry, Order order, int items, boolean superAdmin,
                                   OrderRowMapper mapper, LocalDate today) {
        String href = superAdmin
                ? "/dashboard/store/" + entry.getStoreId() + "/orders/" + entry.getOrderId()
                : "/dashboard/orders/" + entry.getOrderId();
        String orderedAt = orderedAt(entry.getOrderedAt(), today);
        if (order == null) {
            Order bare = new Order(entry.getStoreId());
            bare.setOrderId(entry.getOrderId());
            OrderRow fallback = mapper.map(bare, today);
            return new FulfilmentQueueRow(entry.getOrderId(), href, entry.getShortenedOrderId(), fallback.sourceText(), null,
                    entry.getEmail() != null ? entry.getEmail() : fallback.clientName(), null, null, orderedAt,
                    fallback.dueText(), null, "", items);
        }
        OrderRow mapped = mapper.map(order, today);
        return new FulfilmentQueueRow(entry.getOrderId(), href, mapped.number(), mapped.sourceText(), mapped.externalId(),
                mapped.clientName(), mapped.clientCity(), mapped.email(), orderedAt,
                mapped.dueText(), mapped.dueNote(), mapped.dueTone(), items);
    }

    private static String orderedAt(LocalDateTime orderedAt, LocalDate today) {
        if (orderedAt == null) {
            return "";
        }
        return (orderedAt.getYear() == today.getYear() ? SAME_YEAR : OTHER_YEAR).format(orderedAt);
    }

    private String storeName(String storeId) {
        Store store = storesRepository.findById(storeId);
        return store == null || store.getName() == null || store.getName().isBlank() ? storeId : store.getName();
    }
}
