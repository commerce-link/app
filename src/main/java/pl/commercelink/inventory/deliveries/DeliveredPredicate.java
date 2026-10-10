package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class DeliveredPredicate {

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    /**
     * The first item whose delivery is no record of the store (added by hand as "Unknown", no delivery id, a removed
     * delivery): goods out and shipping read the counterparty from that record, so such an item cannot take part.
     */
    public <T extends Delivered> Optional<T> firstWithoutDelivery(String storeId, List<T> items) {
        return items.stream()
                .filter(item -> item.getDeliveryId() == null || deliveriesRepository.findById(storeId, item.getDeliveryId()) == null)
                .findFirst();
    }

    public boolean isFromSameSource(String storeId, List<? extends Delivered> items) {
        if (items.size() == 1) {
            return true;
        }

        if (items.stream().map(Delivered::getDeliveryId).distinct().count() == 1) {
            return true;
        }

        String provider = null;
        for (Delivered item : items) {
            var delivery = deliveriesRepository.findById(storeId, item.getDeliveryId());
            if (provider == null) {
                provider = delivery.getProvider();
            } else if (!Objects.equals(provider, delivery.getProvider())) {
                return false;
            }
        }

        return true;
    }

}
