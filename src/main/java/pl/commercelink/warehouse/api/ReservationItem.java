package pl.commercelink.warehouse.api;

import java.util.LinkedList;
import java.util.List;

public class ReservationItem {

    private String itemId;
    private String ean;
    private int qty;
    private String warehouseItemId;

    private final List<ReservationConfirmation> confirmations = new LinkedList<>();

    public ReservationItem() {

    }

    public ReservationItem(String itemId, int qty) {
        this.qty = qty;
        this.itemId = itemId;
    }

    public ReservationItem(String itemId, String ean, int qty) {
        this.itemId = itemId;
        this.ean = ean;
        this.qty = qty;
    }

    public ReservationItem(String itemId, String ean, int qty, String warehouseItemId) {
        this(itemId, ean, qty);
        this.warehouseItemId = warehouseItemId;
    }

    public void add(ReservationConfirmation item) {
        confirmations.add(item);
    }

    public boolean hasWarehouseItemId() {
        return warehouseItemId != null && !warehouseItemId.isBlank();
    }

    public int getRemainingQty() {
        return qty - confirmations.stream().mapToInt(ReservationConfirmation::qty).sum();
    }

    public boolean hasConfirmations() {
        return !confirmations.isEmpty();
    }

    public List<ReservationConfirmation> getConfirmations() {
        return confirmations;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public String getWarehouseItemId() {
        return warehouseItemId;
    }

    public void setWarehouseItemId(String warehouseItemId) {
        this.warehouseItemId = warehouseItemId;
    }
}
