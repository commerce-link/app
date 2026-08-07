package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;

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

    @Getter
    @Setter
    public static class AllocationRef {
        private String orderId;
        private String itemId;
    }
}
