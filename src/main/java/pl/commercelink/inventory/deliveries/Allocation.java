package pl.commercelink.inventory.deliveries;

import pl.commercelink.orders.Item;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.warehouse.builtin.WarehouseItem;

public class Allocation {

    private AllocationKey key;
    private AllocationType type;

    private String name;
    private int qty;

    private String deliveryId;
    private String ean;
    private String mfn;
    private double unitCost;

    private boolean inAllocation;
    private boolean selected;

    public Allocation() {

    }

    private Allocation(AllocationKey key, AllocationType type, Item item) {
        this.key = key;
        this.type = type;

        this.name = item.getName();
        this.qty = item.getQty();

        this.deliveryId = item.getDeliveryId();
        this.ean = item.getEan();
        this.mfn = item.getManufacturerCode();
        this.unitCost = item.getCost();
        this.inAllocation = item.isInAllocation() || item.isWaitingForCollection();
    }

    public static Allocation fromWarehouseItem(WarehouseItem i) {
        return new Allocation(new AllocationKey(null, i.getItemId(), "Warehouse"), AllocationType.Warehouse, i);
    }

    public static Allocation fromOrderItem(Order order, OrderItem i) {
        return new Allocation(new AllocationKey(order.getOrderId(), i.getItemId(), order.getBillingDetails().getEmail()), AllocationType.Order, i);
    }

    public void decreaseQty(int amount) {
        this.qty -= amount;
    }

    // required by UI
    public AllocationKey getKey() {
        return key;
    }

    public void setKey(AllocationKey key) {
        this.key = key;
    }

    public AllocationType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setType(AllocationType type) {
        this.type = type;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
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

    public double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(double unitCost) {
        this.unitCost = unitCost;
    }

    public boolean isInAllocation() {
        return inAllocation;
    }

    public void setInAllocation(boolean inAllocation) {
        this.inAllocation = inAllocation;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public double getTotalCost() {
        return unitCost * qty;
    }

}
