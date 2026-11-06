package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeliveryCostSync {

    private final OrderAllocationsManager orderAllocationsManager;
    private final WarehouseAllocationsManager warehouseAllocationsManager;

    public double apply(String storeId, String deliveryId, Map<String, Double> unitCostsByEan) {
        if (unitCostsByEan.isEmpty()) {
            return 0;
        }
        return warehouseAllocationsManager.updateUnitCosts(storeId, deliveryId, unitCostsByEan)
                + orderAllocationsManager.updateUnitCosts(deliveryId, unitCostsByEan);
    }
}
