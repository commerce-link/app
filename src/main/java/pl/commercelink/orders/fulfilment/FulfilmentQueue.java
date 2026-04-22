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

@Component
public class FulfilmentQueue {

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;

    public FulfilmentQueue(StoresRepository storesRepository, OrdersRepository ordersRepository) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
    }

    public List<OrderIndexEntry> pickFulfilmentGroup(List<String> excludedOrderIds, Predicate<Order> extraFilter) {
        List<Store> stores = storesRepository.findAll();
        List<OrderIndexEntry> oldestOrderPerStore = stores.stream()
                .filter(store -> store.getFulfilmentConfiguration() != null && store.getFulfilmentConfiguration().isAutomatedFulfilment())
                .map(Store::getStoreId)
                .map(storeId -> findOldestOrder(storeId, excludedOrderIds, 10, null, extraFilter))
                .filter(Objects::nonNull)
                .toList();

        return oldestOrderPerStore.stream()
                .min(Comparator.comparing(OrderIndexEntry::getOrderedAt))
                .map(oldestOrder -> {
                    if (oldestOrder.getFulfilmentType() == FulfilmentType.WarehouseFulfilment) {
                        return findWarehouseFulfilmentOrders(oldestOrder.getStoreId(), extraFilter);
                    } else {
                        return Collections.singletonList(oldestOrder);
                    }
                })
                .orElse(Collections.emptyList());
    }

    public List<OrderIndexEntry> pickFulfilmentGroup(String storeId, List<String> excludedOrderIds, Predicate<Order> extraFilter) {
        OrderIndexEntry oldestOrder = findOldestOrder(storeId, excludedOrderIds, 10, null, extraFilter);
        if (oldestOrder == null) {
            return Collections.emptyList();
        }
        if (oldestOrder.getFulfilmentType() == FulfilmentType.WarehouseFulfilment) {
            return findWarehouseFulfilmentOrders(oldestOrder.getStoreId(), extraFilter);
        } else {
            return Collections.singletonList(oldestOrder);
        }
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

    private List<OrderIndexEntry> findWarehouseFulfilmentOrders(String storeId, Predicate<Order> extraFilter) {
        List<OrderIndexEntry> storeWarehouseFulfilmentOrders = ordersRepository.findAllWarehouseFulfilmentOrder(storeId);
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
