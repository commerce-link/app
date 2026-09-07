package pl.commercelink.shipping;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShipmentTrackingCheckRequest {

    private String storeId;
    private String orderId;
    private String trackingNo;
}
