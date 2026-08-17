package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static pl.commercelink.inventory.deliveries.DeliveryItem.groupAndUnify;

@Component
public class DeliveriesPlanningService {

    @Autowired
    private OrderAllocationsManager orderAllocationsManager;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Autowired
    private SupplierPurchaseService supplierPurchaseService;

    public List<Delivery> run(String storeId) {
        Partition partition = partition(storeId);
        List<Allocation> allocations = new LinkedList<>(partition.batch());
        allocations.addAll(warehouseAllocationsManager.fetchAll(storeId));
        return groupIntoDeliveries(storeId, allocations);
    }

    public Delivery run(String storeId, String provider) {
        return run(storeId).stream()
                .filter(d -> d.getProvider().equals(provider))
                .findFirst()
                .orElse(null);
    }

    public List<DropshipCandidate> dropshipCandidates(String storeId) {
        return partition(storeId).candidates();
    }

    private record Partition(List<Allocation> batch, List<DropshipCandidate> candidates) {
    }

    private Partition partition(String storeId) {
        List<Allocation> orderAllocations = orderAllocationsManager.fetchAll(storeId);

        Map<String, List<Allocation>> directToConsumerByOrderId = orderAllocations.stream()
                .filter(Allocation::isDirectToConsumer)
                .collect(Collectors.groupingBy(allocation -> allocation.getKey().getOrderId()));

        Map<String, Boolean> dropshipByProvider = new HashMap<>();
        List<DropshipCandidate> candidates = new LinkedList<>();
        for (Map.Entry<String, List<Allocation>> entry : directToConsumerByOrderId.entrySet()) {
            Set<String> providers = entry.getValue().stream()
                    .map(Allocation::getDeliveryId)
                    .collect(Collectors.toSet());
            if (providers.size() != 1) {
                continue;
            }
            String provider = providers.iterator().next();
            boolean dropshipSupported = dropshipByProvider.computeIfAbsent(provider,
                    p -> supplierPurchaseService.isDropshipAvailable(storeId, p));
            if (!dropshipSupported) {
                continue;
            }
            candidates.add(new DropshipCandidate(entry.getKey(),
                    entry.getValue().getFirst().getKey().getName(),
                    provider,
                    groupAndUnify(entry.getValue())));
        }

        Set<String> candidateOrderIds = candidates.stream()
                .map(DropshipCandidate::orderId)
                .collect(Collectors.toSet());
        List<Allocation> batch = orderAllocations.stream()
                .filter(allocation -> !allocation.isDirectToConsumer()
                        || !candidateOrderIds.contains(allocation.getKey().getOrderId()))
                .toList();

        return new Partition(batch, candidates.stream()
                .sorted(Comparator.comparing(DropshipCandidate::orderId))
                .toList());
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
