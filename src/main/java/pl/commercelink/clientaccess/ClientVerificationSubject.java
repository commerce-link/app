package pl.commercelink.clientaccess;

import lombok.Getter;

@Getter
public class ClientVerificationSubject {

    private final String key;
    private final String storeId;
    private final String orderId;
    private final String rmaId;

    private ClientVerificationSubject(String key, String storeId, String orderId, String rmaId) {
        this.key = key;
        this.storeId = storeId;
        this.orderId = orderId;
        this.rmaId = rmaId;
    }

    public static ClientVerificationSubject order(String storeId, String orderId) {
        return new ClientVerificationSubject("ORDER#" + storeId + "#" + orderId, storeId, orderId, null);
    }

    public static ClientVerificationSubject rma(String storeId, String rmaId) {
        return new ClientVerificationSubject("RMA#" + storeId + "#" + rmaId, storeId, null, rmaId);
    }
}
