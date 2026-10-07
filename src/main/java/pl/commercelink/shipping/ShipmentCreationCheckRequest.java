package pl.commercelink.shipping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.commercelink.orders.ShippingDetails;

import java.util.List;

/** Everything the creation checker needs to settle a command, including what its owner does once it is created. */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShipmentCreationCheckRequest {

    private String storeId;
    private ShipmentOwnerType ownerType;
    /** orderId or rmaId; null for the warehouse. */
    private String ownerId;
    private String commandId;
    /** Null while the provider has not said which package it created. */
    private String externalId;
    private String provider;
    private String pickUpAddressId;
    /** RMA: the items go back to the customer (else to a repair center). */
    private boolean toClient;
    /** RMA items or warehouse items sent with it. */
    private List<String> itemIds;
    /** Warehouse: who issued the goods out. */
    private String issuedBy;
    /** Warehouse: where the goods go, for the goods-out document. */
    private ShippingDetails receiver;
    private int attempt;
    /**
     * Customer's return: the operator books a failed return again ("Spróbuj ponownie") in place of its failed row. A
     * customer's own submission never replaces a return already there (RmaReturnShipmentOwner#markCreating). Read only
     * before the provider is called, so it is not sent with the check.
     */
    @JsonIgnore
    private boolean replacesFailedReturn;

    public ShipmentCreationCheckRequest withExternalId(String id) {
        return toBuilder().externalId(id).build();
    }

    public ShipmentCreationCheckRequest nextAttempt() {
        return toBuilder().attempt(attempt + 1).build();
    }
}
