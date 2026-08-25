package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ShipmentCarrierOptions {

    private final CarrierDictionary carrierDictionary;

    public List<String> forOrder(Order order, Store store) {
        Set<String> options = new LinkedHashSet<>(
                carrierDictionary.namesUsedBy(store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER)));
        if (options.isEmpty()) {
            return List.of();
        }
        order.getShipments().stream()
                .map(Shipment::getCarrier)
                .filter(StringUtils::isNotBlank)
                .forEach(options::add);
        return List.copyOf(options);
    }
}
