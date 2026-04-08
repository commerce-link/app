package pl.commercelink.inventory.deliveries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeliveryItem {

    private String name;
    private String ean;
    private String mfn;

    private int orderedQty;
    private int availableQty;
    private int reservedQty;
    private int receivedQty;
    private int requestedQty;

    private double unitCost;
    private List<Allocation> allocations = new ArrayList<>();

    // used on UI
    public DeliveryItem() {

    }

    public static List<DeliveryItem> groupAndUnify(List<Allocation> allocations) {
        Map<String, List<Allocation>> allocationByMfn = allocations.stream()
                .collect(Collectors.groupingBy(Allocation::getMfn));

        List<DeliveryItem> result = new ArrayList<>();
        for (String mfn : allocationByMfn.keySet()) {
            List<Allocation> allocs = allocationByMfn.get(mfn);
            String name = allocs.get(0).getName();
            String ean = allocs.get(0).getEan();
            double unitCost = allocs.get(0).getUnitCost();

            result.add(new DeliveryItem(name, ean, mfn, unitCost, allocs));
        }

        return result;
    }

    public DeliveryItem(String name, String ean, String mfn, double unitCost, List<Allocation> allocations) {
        this.name = name;
        this.ean = ean;
        this.mfn = mfn;
        this.unitCost = unitCost;

        this.allocations = new ArrayList<>(allocations);
        this.orderedQty = allocations.stream().mapToInt(Allocation::getQty).sum();
        this.availableQty = allocations.stream()
                .filter(a -> a.getType() == AllocationType.Warehouse)
                .mapToInt(Allocation::getQty)
                .sum();
        this.reservedQty = allocations.stream()
                .filter(a -> a.getType() == AllocationType.Order)
                .mapToInt(Allocation::getQty)
                .sum();
        this.receivedQty = allocations.stream()
                .filter(a -> !a.isInAllocation())
                .mapToInt(Allocation::getQty)
                .sum();
        this.requestedQty = this.orderedQty;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public String getMfn() {
        return mfn;
    }

    public void setMfn(String mfn) {
        this.mfn = mfn;
    }

    public int getOrderedQty() {
        return orderedQty;
    }

    public void setOrderedQty(int orderedQty) {
        this.orderedQty = orderedQty;
    }

    public int getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(int availableQty) {
        this.availableQty = availableQty;
    }

    public int getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(int reservedQty) {
        this.reservedQty = reservedQty;
    }

    public int getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(int receivedQty) {
        this.receivedQty = receivedQty;
    }

    public int getRequestedQty() {
        return requestedQty;
    }

    public void setRequestedQty(int requestedQty) {
        this.requestedQty = requestedQty;
    }

    public double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(double unitCost) {
        this.unitCost = unitCost;
    }

    public List<Allocation> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<Allocation> allocations) {
        this.allocations = allocations;
    }

    public int getMinQty() {
        return allocations.stream()
                .filter(Allocation::isSelected)
                .filter(a -> a.getType() == AllocationType.Order)
                .mapToInt(Allocation::getQty)
                .sum();
    }

    public int getWarehouseQtyAdjustment() {
        int selectedWarehouseQty = allocations.stream()
                .filter(Allocation::isSelected)
                .filter(a -> a.getType() == AllocationType.Warehouse)
                .mapToInt(Allocation::getQty)
                .sum();
        int availableForWarehouse = requestedQty - getMinQty();
        return availableForWarehouse - selectedWarehouseQty;
    }

    public List<Allocation> getSelectedAllocations(AllocationType type) {
        return allocations.stream()
                .filter(a -> a.isSelected() && a.getType() == type)
                .collect(Collectors.toList());
    }

    public List<Allocation> getUnselectedAllocations(AllocationType type) {
        return allocations.stream()
                .filter(a -> !a.isSelected() && a.getType() == type)
                .collect(Collectors.toList());
    }

    public void updateUnitCost(double unitCost) {
        this.unitCost = unitCost;
        for (Allocation allocation : allocations) {
            allocation.setUnitCost(unitCost);
        }
    }
}
