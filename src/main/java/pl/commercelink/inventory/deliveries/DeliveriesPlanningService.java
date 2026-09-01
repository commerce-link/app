package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private DropshipEligibility dropshipEligibility;

    public record Planning(List<Delivery> deliveries, List<DropshipCandidate> dropshipCandidates) {
    }

    public Planning plan(String storeId) {
        Partition partition = partition(storeId);
        List<Allocation> allocations = new LinkedList<>(partition.batch());
        allocations.addAll(warehouseAllocationsManager.fetchAll(storeId));
        return new Planning(groupIntoDeliveries(storeId, allocations), partition.candidates());
    }

    public List<Delivery> run(String storeId) {
        return plan(storeId).deliveries();
    }

    public Delivery run(String storeId, String provider) {
        return run(storeId).stream()
                .filter(d -> d.getProvider().equals(provider))
                .findFirst()
                .orElse(null);
    }

    private record Partition(List<Allocation> batch, List<DropshipCandidate> candidates) {
    }

    private Partition partition(String storeId) {
        List<Allocation> orderAllocations = orderAllocationsManager.fetchAll(storeId);

        Map<String, List<Allocation>> directToConsumerByOrderId = orderAllocations.stream()
                .filter(Allocation::isDirectToConsumer)
                .collect(Collectors.groupingBy(allocation -> allocation.getKey().getOrderId()));

        List<DropshipCandidate> candidates = new LinkedList<>();
        directToConsumerByOrderId.forEach((orderId, allocations) ->
                eligibleProvider(storeId, orderId).ifPresent(provider ->
                        candidates.add(new DropshipCandidate(orderId, provider, groupAndUnify(allocations),
                                List.copyOf(allocations)))));

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

    private Optional<String> eligibleProvider(String storeId, String orderId) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            return Optional.empty();
        }
        DropshipAssessment assessment = dropshipEligibility.assess(order, orderItemsRepository.findByOrderId(orderId));
        return assessment.hasProviders() ? Optional.of(assessment.providers().getFirst()) : Optional.empty();
    }

    private List<Delivery> groupIntoDeliveries(String storeId, List<Allocation> allocations) {
        Map<String, List<Allocation>> allocationsByProvider = allocations.stream()
                .collect(Collectors.groupingBy(Allocation::getDeliveryId));

        return allocationsByProvider.entrySet().stream()
                .map(entry -> {
                    var delivery = new Delivery(storeId, null, entry.getKey());
                    delivery.setType(DeliveryType.WAREHOUSE);
                    delivery.setAllocations(entry.getValue());
                    delivery.setItems(groupAndUnify(entry.getValue()));
                    return delivery;
                })
                .toList();
    }
}
