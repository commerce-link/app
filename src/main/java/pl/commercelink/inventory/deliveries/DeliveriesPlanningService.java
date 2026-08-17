package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static pl.commercelink.inventory.deliveries.DeliveryItem.groupAndUnify;

@Component
public class DeliveriesPlanningService {

    @Autowired
    private OrderAllocationsManager orderAllocationsManager;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    public List<Delivery> run(String storeId) {
        List<Allocation> allocations = new LinkedList<>();
        allocations.addAll(orderAllocationsManager.fetchAll(storeId));
        allocations.addAll(warehouseAllocationsManager.fetchAll(storeId));
        allocations.removeIf(Allocation::isDirectToConsumer);
        return groupIntoDeliveries(storeId, allocations);
    }

    public List<DropshipCandidate> dropshipCandidates(String storeId) {
        Map<String, List<Allocation>> allocationsByOrderId = orderAllocationsManager.fetchAll(storeId).stream()
                .filter(Allocation::isDirectToConsumer)
                .collect(Collectors.groupingBy(allocation -> allocation.getKey().getOrderId()));

        return allocationsByOrderId.entrySet().stream()
                .flatMap(entry -> {
                    Map<String, List<Allocation>> byProvider = entry.getValue().stream()
                            .collect(Collectors.groupingBy(Allocation::getDeliveryId));
                    boolean multiSupplier = byProvider.size() > 1;
                    return byProvider.entrySet().stream()
                            .map(providerEntry -> new DropshipCandidate(
                                    entry.getKey(),
                                    providerEntry.getValue().getFirst().getKey().getName(),
                                    providerEntry.getKey(),
                                    groupAndUnify(providerEntry.getValue()),
                                    multiSupplier));
                })
                .sorted(Comparator.comparing(DropshipCandidate::orderId)
                        .thenComparing(DropshipCandidate::provider))
                .toList();
    }

    public Delivery run(String storeId, String provider) {
        return run(storeId).stream()
                .filter(d -> d.getProvider().equals(provider))
                .findFirst()
                .orElse(null);
    }

    private List<Delivery> groupIntoDeliveries(String storeId, List<Allocation> allocations) {
        Map<String, List<Allocation>> allocationsByProvider = allocations.stream()
                .collect(Collectors.groupingBy(Allocation::getDeliveryId));

        return allocationsByProvider.entrySet().stream()
                .map(entry -> {
                    var delivery = new Delivery(storeId, null, entry.getKey());
                    delivery.setAllocations(entry.getValue());
                    delivery.setItems(groupAndUnify(entry.getValue()));
                    return delivery;
                })
                .toList();
    }
}
