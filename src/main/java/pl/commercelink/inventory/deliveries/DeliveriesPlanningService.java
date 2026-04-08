package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.PaymentStatus;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

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
        return groupIntoDeliveries(storeId, allocations);
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
                    var delivery = new Delivery(storeId, null, entry.getKey(), PaymentStatus.New);
                    delivery.setAllocations(entry.getValue());
                    delivery.setItems(groupAndUnify(entry.getValue()));
                    return delivery;
                })
                .toList();
    }
}
