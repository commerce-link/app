package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

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
    @Autowired
    private ExchangeRates exchangeRates;

    public String run(String storeId, DeliveryCreationForm form, boolean isSuperAdmin) {
        prepareForm(storeId, form);

        if (form.hasDeliveryDetails()) {
            var delivery = createDelivery(storeId, form, isSuperAdmin);
            finalizeDelivery(storeId, delivery, form);
            return delivery.getDeliveryId();
        }

        return null;
    }

    public void completePending(String storeId, Delivery delivery, DeliveryCreationForm form) {
        prepareForm(storeId, form);

        delivery.setExternalDeliveryId(form.getExternalDeliveryId());
        delivery.setEstimatedDeliveryAt(form.getEstimatedDeliveryAt());
        delivery.setShippingCost(form.getShippingCost());
        delivery.setPaymentCost(form.getPaymentCost());
        delivery.setPaymentTerms(form.getPaymentTerms());
        delivery.setTax(form.getTax());
        delivery.setOrderStatus(null);
        delivery.setPendingOrderForm(null);

        finalizeDelivery(storeId, delivery, form);
    }

    private void prepareForm(String storeId, DeliveryCreationForm form) {
        if (form.getSuggestedItems() != null) {
            form.getSuggestedItems().stream()
                    .filter(suggested -> suggested.getRequestedQty() > 0)
                    .map(SuggestedDeliveryItem::toDeliveryItem)
                    .forEach(form.getItems()::add);
        }

        if (form.isRemoveUnselected()) {
            removeUnselectedAllocations(storeId, form.getItems());
        }

        if (form.hasPricesInForeignCurrency()) {
            form.applyExchangeRate(exchangeRates.getCurrentSellRates().get(form.getSourceCurrency()));
        }
    }

    private void finalizeDelivery(String storeId, Delivery delivery, DeliveryCreationForm form) {
        double allocationsCost = form.getItems().stream()
                .mapToDouble(item -> item.getRequestedQty() * item.getUnitCost())
                .sum();
        delivery.increaseTotalCost(allocationsCost);

        deliveriesRepository.save(delivery);

        orderAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getEstimatedDeliveryAt(), form.getItems());
        warehouseAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getProvider(), form.getItems());
    }

    private Delivery createDelivery(String storeId, DeliveryCreationForm form, boolean isSuperAdmin) {
        var delivery = new Delivery(
                storeId,
                form.getExternalDeliveryId(),
                form.getProvider(),
                form.getEstimatedDeliveryAt(),
                form.getShippingCost(),
                form.getPaymentCost(),
                form.getPaymentTerms(),
                form.getTax()
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
