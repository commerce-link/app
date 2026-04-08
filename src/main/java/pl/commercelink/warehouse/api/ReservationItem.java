package pl.commercelink.warehouse.api;

import java.util.LinkedList;
import java.util.List;

public class ReservationItem {

    private String itemId;
    private String mfn;
    private int qty;

    private final List<ReservationConfirmation> confirmations = new LinkedList<>();

    public ReservationItem() {

    }

    public ReservationItem(String itemId, int qty) {
        this.qty = qty;
        this.itemId = itemId;
    }

    public ReservationItem(String itemId, String mfn, int qty) {
        this.itemId = itemId;
        this.mfn = mfn;
        this.qty = qty;
    }

    public void add(ReservationConfirmation item) {
        confirmations.add(item);
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

    public String getMfn() {
        return mfn;
    }

    public void setMfn(String mfn) {
        this.mfn = mfn;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }
}
