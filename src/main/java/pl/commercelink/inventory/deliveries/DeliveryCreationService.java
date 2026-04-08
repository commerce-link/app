package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component
public class DeliveryCreationService {

    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private OrderAllocationsManager orderAllocationsManager;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    public void run(String storeId, DeliveryCreationForm form, boolean isSuperAdmin) {
        if (form.hasPricesInForeignCurrency()) {
            form.applyExchangeRate(new ExchangeRates().getCurrentSellRates().get(form.getSourceCurrency()));
        }

        if (form.hasDeliveryDetails()) {
            var delivery = createDelivery(storeId, form, isSuperAdmin);
            deliveriesRepository.save(delivery);

            orderAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getEstimatedDeliveryAt(), form.getItems());
            warehouseAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getProvider(), form.getItems());
        }

        if (form.isRemoveUnselected()) {
            removeUnselectedAllocations(storeId, form.getItems());
        }
    }

    private Delivery createDelivery(String storeId, DeliveryCreationForm form, boolean isSuperAdmin) {
        var delivery = new Delivery(
                storeId,
                form.getExternalDeliveryId(),
                form.getProvider(),
                form.getPaymentStatus(),
                form.getEstimatedDeliveryAt(),
                form.getShippingCost(),
                form.getPaymentCost(),
                form.getPaymentTerms()
        );
        delivery.setManaged(isSuperAdmin);
        delivery.addEvent(new Event(EventType.action, "DELIVERY_CREATED", LocalDateTime.now()));
        return delivery;
    }

    private void removeUnselectedAllocations(String storeId, List<DeliveryItem> items) {
        Map<String, List<String>> allocationsByOrderId = new HashMap<>();

        for (DeliveryItem item : items) {
            for (Allocation allocation : item.getUnselectedAllocations(AllocationType.Order)) {
                allocationsByOrderId.computeIfAbsent(allocation.getKey().getOrderId(), k -> new LinkedList<>()).add(allocation.getKey().getItemId());
            }

            for (Allocation allocation : item.getUnselectedAllocations(AllocationType.Warehouse)) {
                warehouseAllocationsManager.remove(storeId, allocation.getKey().getItemId());
            }
        }

        for (String orderId : allocationsByOrderId.keySet()) {
            orderAllocationsManager.remove(
                    storeId, orderId, allocationsByOrderId.get(orderId)
            );
        }
    }
}
