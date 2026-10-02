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

    public static ShipmentCancellationCheckRequest first(String storeId, String orderId, String externalId, String commandId) {
        return new ShipmentCancellationCheckRequest(storeId, orderId, externalId, commandId, 1);
    }

    public ShipmentCancellationCheckRequest nextAttempt() {
        return new ShipmentCancellationCheckRequest(storeId, orderId, externalId, commandId, attempt + 1);
    }
}
