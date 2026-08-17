package pl.commercelink.inventory.deliveries;

import java.util.List;

public record DropshipCandidate(String orderId, String customer, String provider,
                                List<DeliveryItem> items) {
}
