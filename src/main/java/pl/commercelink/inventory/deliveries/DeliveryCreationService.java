package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DeliveryCreationService {

    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private OrderAllocationsManager orderAllocationsManager;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Autowired
    private ExchangeRates exchangeRates;
    @Autowired
    private SupplierConnectionModeResolver supplierConnectionModeResolver;
    @Autowired
    private DeliveryCostSync deliveryCostSync;

    public String run(String storeId, DeliveryCreationForm form) {
        prepareForm(storeId, form);

        if (form.hasDeliveryDetails()) {
            var delivery = createDelivery(storeId, form);
            finalizeDelivery(storeId, delivery, form);
            return delivery.getDeliveryId();
        }

        return null;
    }

    public void claimAllocations(String storeId, Delivery delivery, DeliveryCreationForm form) {
        prepareForm(storeId, form);
        if (delivery.isDropship()) {
            // dropship goods never reach the warehouse: claim and price exactly the selected order allocations,
            // whatever quantity a stale or tampered form asked for
            form.getItems().forEach(item -> item.setRequestedQty(item.getMinQty()));
        }
        delivery.increaseTotalCost(allocationsCost(form));
        orderAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getEstimatedDeliveryAt(), form.getItems());
        if (!delivery.isDropship()) {
            warehouseAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getProvider(), form.getItems());
        }
    }

    /**
     * The automatic purchase path: the allocations are reserved for this delivery but stay in allocation
     * until the supplier confirms. The manual "Save" path keeps using claimAllocations/commit, where the
     * order at the supplier already exists.
     */
    public void claimAllocationsForPurchase(String storeId, Delivery delivery, DeliveryCreationForm form) {
        prepareForm(storeId, form);
        delivery.increaseTotalCost(allocationsCost(form));
        orderAllocationsManager.claim(storeId, delivery.getDeliveryId(), form.getItems());
        warehouseAllocationsManager.claim(storeId, delivery.getDeliveryId(), form.getProvider(), form.getItems());
    }

    /** Frees the allocations the operator unchecked on the creation form without creating a delivery. */
    public void releaseUnselectedAllocations(String storeId, DeliveryCreationForm form) {
        removeUnselectedAllocations(storeId, form.getItems());
    }

    public void releaseAllocations(String storeId, Delivery delivery) {
        orderAllocationsManager.release(storeId, delivery.getDeliveryId(), delivery.getProvider());
        warehouseAllocationsManager.release(storeId, delivery.getDeliveryId(), delivery.getProvider());
    }

    public void completePending(String storeId, Delivery delivery, DeliveryCreationForm form) {
        delivery.setExternalDeliveryId(form.getExternalDeliveryId());
        delivery.setEstimatedDeliveryAt(form.getEstimatedDeliveryAt());
        delivery.updateShippingCost(form.getShippingCost());
        delivery.updatePaymentCost(form.getPaymentCost());
        delivery.setPaymentTerms(form.getPaymentTerms());
        delivery.setTax(form.getTax());
        delivery.setOrderStatus(null);

        delivery.increaseTotalCost(deliveryCostSync.apply(storeId, delivery.getDeliveryId(), confirmedUnitCosts(form)));
        deliveriesRepository.save(delivery);

        markClaimedAsOrdered(storeId, delivery, form.getEstimatedDeliveryAt());
    }

    public void completeDropshipPending(String storeId, Delivery delivery, DeliveryCreationForm form) {
        delivery.setExternalDeliveryId(form.getExternalDeliveryId());
        delivery.setOrderStatus(null);
        delivery.increaseTotalCost(deliveryCostSync.apply(storeId, delivery.getDeliveryId(), confirmedUnitCosts(form)));
        deliveriesRepository.save(delivery);
    }

    /**
     * The delivery is already saved with the supplier's order number, so a failure here must not undo the
     * completion: an SQS redelivery would stop at the "no longer pending" guard and the order number would
     * be lost. Log loudly instead. The delivery itself is complete, but the affected items are stuck in
     * allocation, still claimed to it - neither ordered nor visible on the allocation screen - and there is
     * no in-application remedy for that state by this point (the delivery is no longer AWAITING_APPROVAL,
     * which is the only state release goes through). Recovery needs an engineer to intervene directly:
     * clear the claim on the affected items or re-run the marking. Each side gets its own try/catch so a
     * failure on one does not also skip the other.
     */
    public void markClaimedAsOrdered(String storeId, Delivery delivery, LocalDate estimatedDeliveryAt) {
        try {
            orderAllocationsManager.markClaimedAsOrdered(storeId, delivery.getDeliveryId(), estimatedDeliveryAt);
        } catch (RuntimeException e) {
            log.error("Claimed order allocations not marked as ordered - items remain claimed and stuck in " +
                            "allocation with no in-application remedy, needs an engineer to intervene directly: " +
                            "store={} delivery={} provider={} estimatedDeliveryAt={}",
                    storeId, delivery.getDeliveryId(), delivery.getProvider(), estimatedDeliveryAt, e);
        }
        try {
            warehouseAllocationsManager.markClaimedAsOrdered(storeId, delivery.getDeliveryId());
        } catch (RuntimeException e) {
            log.error("Claimed warehouse allocations not marked as ordered - items remain claimed and stuck in " +
                            "allocation with no in-application remedy, needs an engineer to intervene directly: " +
                            "store={} delivery={} provider={} estimatedDeliveryAt={}",
                    storeId, delivery.getDeliveryId(), delivery.getProvider(), estimatedDeliveryAt, e);
        }
    }

    private Map<String, Double> confirmedUnitCosts(DeliveryCreationForm form) {
        return form.getItems().stream()
                .filter(item -> item.getRequestedQty() > 0)
                .collect(Collectors.toMap(DeliveryItem::getMfn, DeliveryItem::getUnitCost, (a, b) -> a));
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

    private double allocationsCost(DeliveryCreationForm form) {
        return form.getItems().stream()
                .mapToDouble(item -> item.getRequestedQty() * item.getUnitCost())
                .sum();
    }

    private void finalizeDelivery(String storeId, Delivery delivery, DeliveryCreationForm form) {
        delivery.increaseTotalCost(allocationsCost(form));

        deliveriesRepository.save(delivery);

        orderAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getEstimatedDeliveryAt(), form.getItems());
        warehouseAllocationsManager.commit(storeId, delivery.getDeliveryId(), form.getProvider(), form.getItems());
    }

    private Delivery createDelivery(String storeId, DeliveryCreationForm form) {
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
        delivery.setConnectionMode(supplierConnectionModeResolver.resolve(storeId, form.getProvider()));
        delivery.setType(DeliveryType.WAREHOUSE);
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
