package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.web.orders.Money;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DeliveryFulfilmentUpdateForm {

    private String ean;
    private String mfn;
    private double unitCost;
    private List<AllocationRef> allocations = new ArrayList<>();
    private List<String> warehouseItemIds = new ArrayList<>();

    // the field has no browser validation, so a cost typed with more decimals is rounded to whole grosze here
    public void setUnitCost(double unitCost) {
        this.unitCost = Money.round(unitCost);
    }

    @Getter
    @Setter
    public static class AllocationRef {
        private String orderId;
        private String itemId;
    }
}
