package pl.commercelink.orders.fulfilment;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.QueryPageResult;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Picks the next group of the fulfilment queue. The store's warehouse orders always come first, as one group bought
 * together; dropship orders follow one at a time, oldest first, once no warehouse order is left or the warehouse group
 * was skipped.
 */
@Component
public class FulfilmentQueue {

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;

    public FulfilmentQueue(StoresRepository storesRepository, OrdersRepository ordersRepository) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
    }

    public List<OrderIndexEntry> pickFulfilmentGroup(List<String> excludedOrderIds, Predicate<Order> extraFilter) {
        List<String> storeIds = storesRepository.findAll().stream()
                .filter(store -> store.getFulfilmentConfiguration() != null && store.getFulfilmentConfiguration().isAutomatedFulfilment())
                .map(Store::getStoreId)
                .toList();

        // the store whose oldest warehouse order waits longest goes first; only its group is loaded in full
        Optional<OrderIndexEntry> oldestWarehouseOrder = storeIds.stream()
                .map(storeId -> findOldestWarehouseOrder(storeId, excludedOrderIds, extraFilter))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(OrderIndexEntry::getOrderedAt));
        if (oldestWarehouseOrder.isPresent()) {
            return findWarehouseFulfilmentOrders(oldestWarehouseOrder.get().getStoreId(), excludedOrderIds, extraFilter);
        }

        return storeIds.stream()
                .map(storeId -> findOldestOrder(storeId, excludedOrderIds, 10, null, extraFilter))
                .filter(Objects::nonNull)
                .min(Comparator.comparing(OrderIndexEntry::getOrderedAt))
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    public List<OrderIndexEntry> pickFulfilmentGroup(String storeId, List<String> excludedOrderIds, Predicate<Order> extraFilter) {
        List<OrderIndexEntry> warehouseGroup = findWarehouseFulfilmentOrders(storeId, excludedOrderIds, extraFilter);
        if (!warehouseGroup.isEmpty()) {
            return warehouseGroup;
        }
        OrderIndexEntry oldestOrder = findOldestOrder(storeId, excludedOrderIds, 10, null, extraFilter);
        return oldestOrder == null ? Collections.emptyList() : Collections.singletonList(oldestOrder);
    }

    // the index returns the store's warehouse orders oldest first, so the first one the filter lets through is the oldest
    private Optional<OrderIndexEntry> findOldestWarehouseOrder(String storeId, List<String> excludedOrderIds, Predicate<Order> extraFilter) {
        return notExcluded(ordersRepository.findAllWarehouseFulfilmentOrder(storeId), excludedOrderIds)
                .filter(o -> extraFilter == null || extraFilter.test(ordersRepository.findById(o.getStoreId(), o.getOrderId())))
                .findFirst();
    }

    private static Stream<OrderIndexEntry> notExcluded(List<OrderIndexEntry> orders, List<String> excludedOrderIds) {
        Set<String> excluded = excludedOrderIds == null ? Set.of() : new HashSet<>(excludedOrderIds);
        return orders.stream().filter(o -> !excluded.contains(o.getOrderId()));
    }

    private OrderIndexEntry findOldestOrder(String storeId, List<String> excludedOrderIds, int limit,
                                  Map<String, AttributeValue> startKey, Predicate<Order> extraFilter) {
        QueryPageResult<OrderIndexEntry> orders = ordersRepository.findOldestOrdersWaitingForFulfilment(storeId, excludedOrderIds, limit, startKey);

        Stream<OrderIndexEntry> orderStream = orders.items().stream();
        if (extraFilter != null) {
            orderStream = orderStream
                    .map(o -> ordersRepository.findById(o.getStoreId(), o.getOrderId()))
                    .filter(extraFilter)
                    .map(OrderIndexEntry::fromOrder);
        }

        Optional<OrderIndexEntry> oldestMatch = orderStream.min(Comparator.comparing(OrderIndexEntry::getOrderedAt));
        if (oldestMatch.isPresent()) return oldestMatch.get();
        if (orders.lastEvaluatedKey() != null) {
            return findOldestOrder(storeId, excludedOrderIds, limit, orders.lastEvaluatedKey(), extraFilter);
        }
        return null;
    }

    // skipped orders stay out, so a skipped warehouse group hands the queue over to the dropship orders
    private List<OrderIndexEntry> findWarehouseFulfilmentOrders(String storeId, List<String> excludedOrderIds, Predicate<Order> extraFilter) {
        List<OrderIndexEntry> storeWarehouseFulfilmentOrders = notExcluded(ordersRepository.findAllWarehouseFulfilmentOrder(storeId), excludedOrderIds)
                .collect(Collectors.toList());
        if (extraFilter != null) {
            storeWarehouseFulfilmentOrders = storeWarehouseFulfilmentOrders.stream()
                    .map(o -> ordersRepository.findById(o.getStoreId(), o.getOrderId()))
                    .filter(extraFilter)
                    .map(OrderIndexEntry::fromOrder)
                    .collect(Collectors.toList());
        }
        return storeWarehouseFulfilmentOrders;
    }

}
