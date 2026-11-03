package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.carriers.CarriersRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ShipmentCarrierOptions {

    private final CarriersRepository carriersRepository;

    /** Our carriers, plus what the order's shipments already hold, so an older or typed-in text stays selectable. */
    public List<String> forOrder(Order order) {
        Set<String> options = new LinkedHashSet<>(carriersRepository.names());
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
