package pl.commercelink.orders.fulfilment;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ManualWarehouseFulfilment {

    private final Inventory inventory;
    private final WarehouseAllocationsManager warehouseAllocationsManager;
    private final SupplierRegistry supplierRegistry;

    public ManualWarehouseFulfilment(Inventory inventory, WarehouseAllocationsManager warehouseAllocationsManager, SupplierRegistry supplierRegistry) {
        this.inventory = inventory;
        this.warehouseAllocationsManager = warehouseAllocationsManager;
        this.supplierRegistry = supplierRegistry;
    }

    public FulfilmentForm init(String storeId, List<OrderItem> orderItems) {
        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory.withEnabledSuppliersOnly(storeId))
                .withAcceptedSuppliers(supplierRegistry.getExternalSupplierNames())
                .withFulfilmentUnderCost()
                .build();
        return new FulfilmentForm("warehouse", "redirect:/dashboard/warehouse?statuses=New", generator.runWithGrouping(orderItems));
    }

    public void accept(String storeId, FulfilmentForm form) {
        List<FulfilmentItem> fulfilments = form.getAcceptedFulfilmentItems()
                .stream()
                .filter(FulfilmentItem::isAccepted)
                .collect(Collectors.toList());

        warehouseAllocationsManager.create(storeId, fulfilments);
    }

}
