package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.stores.Store;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ObservedDeliveryForms {

    @Autowired
    private OrdersRepository ordersRepository;

    public List<DeliveryForm> of(Store store) {
        return ordersRepository.findAll(store.getStoreId()).stream()
                .filter(order -> order.getSource() != null
                        && order.getSource().getType() == OrderSourceType.Marketplace)
                .flatMap(order -> order.getShipments().stream()
                        .map(Shipment::getDeliveryForm)
                        .filter(form -> form != null && !form.isBlank())
                        .map(form -> new DeliveryForm(order.getSource().getName(), form)))
                .distinct()
                .sorted(Comparator.comparing(DeliveryForm::source).thenComparing(DeliveryForm::name))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    public Set<String> carrierNamesFor(Store store) {
        return store.getShippingConfiguration() == null
                ? Set.of()
                : store.getShippingConfiguration().getAuthorizedCarriers().stream()
                        .map(carrier -> carrier.getDisplayName() != null && !carrier.getDisplayName().isBlank()
                                ? carrier.getDisplayName()
                                : carrier.getName())
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public record DeliveryForm(String source, String name) {
    }
}
