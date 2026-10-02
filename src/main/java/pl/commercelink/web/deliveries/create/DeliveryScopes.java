package pl.commercelink.web.deliveries.create;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockSuggestionService;

import java.util.List;

/**
 * Picks the scope of a create request: no order = the supplier's warehouse batch; an order = its dropship lines at
 * that supplier, re-assessed by DropshipEligibility on every request (an order can stop qualifying between the steps:
 * a line moved to another delivery, the supplier changed).
 */
@Component
@RequiredArgsConstructor
public class DeliveryScopes {

    private final DeliveriesPlanningService planning;
    private final RestockSuggestionService restockSuggestions;
    private final DeliveryTaxResolver taxResolver;
    private final SupplierPurchaseService supplierPurchase;
    private final DeliveryCreationService deliveryCreation;
    private final StoresRepository stores;
    private final OrdersRepository orders;
    private final OrderItemsRepository orderItems;
    private final DropshipEligibility eligibility;
    private final DropshipPurchaseService dropshipPurchase;

    public Resolution resolve(String storeId, String provider, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return new Resolution.Found(new WarehouseDeliveryScope(storeId, provider, planning, restockSuggestions,
                    taxResolver, supplierPurchase, deliveryCreation, stores));
        }
        Order order = orders.findById(storeId, orderId);
        if (order == null) {
            return new Resolution.Refused(null);
        }
        List<OrderItem> items = orderItems.findByOrderId(orderId);
        DropshipAssessment assessment = eligibility.assess(order, items);
        if (!assessment.hasProviders()) {
            return new Resolution.Refused(DropshipRejectionMessages.keyFor(assessment.rejection()));
        }
        if (!assessment.supports(provider)) {
            return new Resolution.Refused("orders.dropship.rejected.providerMismatch");
        }
        return new Resolution.Found(new DropshipDeliveryScope(storeId, provider, order, items, taxResolver,
                supplierPurchase, dropshipPurchase));
    }

    public sealed interface Resolution {

        record Found(DeliveryScope scope) implements Resolution {
        }

        /** The order cannot be dropshipped at this supplier; messageKey (null: none) is shown on the order page. */
        record Refused(String messageKey) implements Resolution {
        }
    }
}
