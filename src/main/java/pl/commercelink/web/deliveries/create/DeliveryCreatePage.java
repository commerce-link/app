package pl.commercelink.web.deliveries.create;

import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.DropshipPurchaseService;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.Objects;

/**
 * What the three create pages show around the form: addresses, supplier name, recipient (the store's warehouse, or
 * the customer with an optional pickup point), whether and how the integration can order, and the lead counts.
 */
public record DeliveryCreatePage(DeliveryCreateLinks links, String provider, String supplierName, Order order,
                                 ShippingDetails consignee, Shipment pickupShipment, boolean purchaseAvailable,
                                 String purchaseBlockedReason, boolean requiresApproval, boolean requiresOrderIdentity,
                                 int orderCount, boolean restock) {

    public static DeliveryCreatePage of(DeliveryScope scope, DeliveryCreateLinks links, String supplierName,
                                        DeliveryCreationForm form) {
        Order order = scope.order();
        int orderCount = (int) form.getItems().stream()
                .flatMap(item -> item.getAllocations().stream())
                .filter(allocation -> allocation.getType() == AllocationType.Order)
                .map(allocation -> allocation.getKey().getOrderId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        boolean restock = form.getItems().stream()
                .flatMap(item -> item.getAllocations().stream())
                .map(Allocation::getType)
                .anyMatch(type -> type == AllocationType.Warehouse);
        return new DeliveryCreatePage(links, scope.provider(), supplierName, order,
                order == null ? null : order.getShippingDetails(),
                order == null ? null : DropshipPurchaseService.pickupShipment(order).orElse(null),
                scope.purchaseAvailable(), scope.purchaseBlockedReason(), scope.requiresApproval(),
                scope.requiresOrderIdentity(), orderCount, restock);
    }

    public boolean dropship() {
        return order != null;
    }
}
