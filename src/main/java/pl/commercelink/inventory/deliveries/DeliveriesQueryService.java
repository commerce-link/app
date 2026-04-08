package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static pl.commercelink.inventory.deliveries.DeliveryItem.groupAndUnify;

@Service
public class DeliveriesQueryService {

    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private Warehouse warehouse;
    @Autowired
    private OrderAllocationsManager orderAllocationsManager;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    public Delivery fetchDeliveryWithAllocations(String storeId, String deliveryId) {
        var delivery = deliveriesRepository.findById(storeId, deliveryId);
        List<Allocation> allocations = fetchAllocations(storeId, delivery.getDeliveryId());

        delivery.setAllocations(allocations);
        delivery.setItems(groupAndUnify(allocations));
        if (delivery.hasExternalDocument(DocumentType.GoodsReceipt)) {
            enrichWithReceivedAllocations(storeId, delivery);
        }
        return delivery;
    }

    public List<Delivery> fetchActiveDeliveriesWithAllocations(String storeId) {
        var deliveries = deliveriesRepository.findAllActiveDeliveries(storeId);
        for (var delivery : deliveries) {
            delivery.setAllocations(fetchAllocations(storeId, delivery.getDeliveryId()));
            if (delivery.hasExternalDocument(DocumentType.GoodsReceipt)) {
                enrichWithReceivedAllocations(storeId, delivery);
            }
        }
        return deliveries;
    }

    private List<Allocation> fetchAllocations(String storeId, String deliveryId) {
        List<Allocation> entries = new LinkedList<>();
        entries.addAll(orderAllocationsManager.fetchAll(storeId, deliveryId));
        entries.addAll(warehouseAllocationsManager.fetchAll(storeId, deliveryId));
        return entries;
    }

    private void enrichWithReceivedAllocations(String storeId, Delivery delivery) {
        List<Allocation> receivedAllocations = warehouse.documentQueryService(storeId).fetchAllocations(delivery);
        if (receivedAllocations.isEmpty()) {
            return;
        }

        // reduce qty on warehouse allocations based on received allocations in the system
        for (Allocation receivedAllocation : receivedAllocations) {
            int qty = delivery.getReceivedAllocationsFor(receivedAllocation.getMfn(), AllocationType.Order)
                    .stream()
                    .mapToInt(Allocation::getQty)
                    .sum();

            receivedAllocation.decreaseQty(qty);
        }

        receivedAllocations.stream()
                .filter(a -> a.getQty() > 0).collect(Collectors.toList())
                .forEach(delivery::add);
    }
}
