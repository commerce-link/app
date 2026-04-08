package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DeliveredPredicate {

    @Autowired
    private DeliveriesRepository deliveriesRepository;

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
