package pl.commercelink.inventory.deliveries;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Item;

@Component
public class DeliveryRedirectResolver {

    public String resolveFor(Item item) {
        if (SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId())) {
            return "/dashboard/warehouse";
        }
        if (item.hasOneOfTheStatuses(FulfilmentStatus.New, FulfilmentStatus.Allocation)) {
            return "/dashboard/deliveries/create/" + item.getDeliveryId();
        }
        return "/dashboard/deliveries/details?deliveryId=" + item.getDeliveryId();
    }
}
