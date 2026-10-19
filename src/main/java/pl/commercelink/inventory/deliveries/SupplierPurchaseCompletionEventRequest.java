package pl.commercelink.inventory.deliveries;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPurchaseCompletionEventRequest {

    private String storeId;
    private String deliveryId;
    private String purchaseRef;
    private String orderId;
}
