package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.util.List;

class FulfilmentAllocationGroup {

    private String allocationGroupId;
    private double allocationGroupValue;
    private List<FulfilmentGroup> fulfilmentGroups;

    public FulfilmentAllocationGroup(String allocationGroupId, List<FulfilmentGroup> fulfilmentGroups) {
        this.allocationGroupId = allocationGroupId;
        this.allocationGroupValue = fulfilmentGroups.iterator().next().getSourceValue();
        this.fulfilmentGroups = fulfilmentGroups;
    }

    public String getAllocationGroupId() {
        return allocationGroupId;
    }

    public double getAllocationGroupValue() {
        return allocationGroupValue;
    }

    public List<FulfilmentGroup> getFulfilmentGroups() {
        return fulfilmentGroups;
    }

    public FulfilmentGroup getWarehouseFulfilmentGroup() {
        return fulfilmentGroups.stream().filter(fg -> SupplierRegistry.WAREHOUSE.equalsIgnoreCase(fg.getSource().getProvider())).findFirst().orElse(null);
    }

    public boolean canBeFulfilledByWarehouse() {
        return fulfilmentGroups.stream().anyMatch(fg -> SupplierRegistry.WAREHOUSE.equalsIgnoreCase(fg.getSource().getProvider()));
    }
}
