package pl.commercelink.shipping;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShipmentCancellationCheckRequest {

    private String storeId;
    private String orderId;
    private String externalId;
    private String commandId;
    private int attempt;
    /** The integration the cancelled shipment belongs to; null in a message sent before the field existed. */
    private String provider;

    public static ShipmentCancellationCheckRequest first(String storeId, String orderId, String externalId, String commandId) {
        return first(storeId, orderId, externalId, commandId, null);
    }

    public static ShipmentCancellationCheckRequest first(String storeId, String orderId, String externalId, String commandId,
                                                         String provider) {
        return new ShipmentCancellationCheckRequest(storeId, orderId, externalId, commandId, 1, provider);
    }

    public ShipmentCancellationCheckRequest nextAttempt() {
        return new ShipmentCancellationCheckRequest(storeId, orderId, externalId, commandId, attempt + 1, provider);
    }
}
