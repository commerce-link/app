package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
    @Autowired
    private OptimisticLockingExecutor optimisticLockingExecutor;

    /**
     * Records a delivery the operator ordered outside the system. The supplier order number and date are required:
     * the date goes onto the order items on commit. Callers validate first (the create page shows the errors); a form
     * without them is refused before anything is released or saved.
     */
    public String run(String storeId, DeliveryCreationForm form) {
        if (!form.hasDeliveryDetails()) {
            throw new IllegalArgumentException("A delivery recorded outside the system needs the supplier order number and date");
        }
        prepareForm(storeId, form);
        var delivery = createDelivery(storeId, form);
        finalizeDelivery(storeId, delivery, form);
        return delivery.getDeliveryId();
    }

    public void claimAllocations(String storeId, Delivery delivery, DeliveryCreationForm form) {
        prepareForm(storeId, form);
        clampDropshipQuantities(delivery, form);
        delivery.increaseTotalCost(allocationsCost(form));
        // The delivery has to exist before the items start pointing at it: the ordering step asks the
        // deliveries behind the order's items how the goods travel, and an unsaved delivery reads as none.
        deliveriesRepository.save(delivery);
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
        clampDropshipQuantities(delivery, form);
        delivery.increaseTotalCost(allocationsCost(form));
        // The delivery has to exist before the items start pointing at it. A claim is written onto the items
        // themselves and hides them from the allocation screen: saved the other way round, a failed save
        // would leave them reserved for a delivery that does not exist, with no screen left to release them
        // from. The caller must not save again - whatever it sets on the delivery has to be set before this.
        deliveriesRepository.save(delivery);
        orderAllocationsManager.claim(storeId, delivery.getDeliveryId(), form.getItems());
        if (!delivery.isDropship()) {
            // dropship goods never reach the warehouse, so they must not leave a reserved row behind
            warehouseAllocationsManager.claim(storeId, delivery.getDeliveryId(), form.getProvider(), form.getItems());
        }
    }

    private void clampDropshipQuantities(Delivery delivery, DeliveryCreationForm form) {
        if (delivery.isDropship()) {
            // dropship goods never reach the warehouse: claim and price exactly the selected order allocations,
            // whatever quantity a stale or tampered form asked for
            form.getItems().forEach(item -> item.setRequestedQty(item.getMinQty()));
        }
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
        completePending(storeId, delivery, form, unchanged -> {
        });
    }

    /**
     * Completes a pending purchase. {@code callerChanges} repeats what the caller already set on {@code delivery}
     * (flags, events): when the save loses an optimistic-locking race to a concurrent edit, the completion is
     * applied again to the delivery as stored now, and the caller's changes must travel with it.
     *
     * <p>The confirmed unit costs are written to the items before the delivery is saved, so a second cost sync
     * would find nothing left to change and return zero - the delta computed here is the only record of it. That
     * is why a lost race is resolved here, with the same delta, instead of being left to the caller's retry (an SQS
     * redelivery would complete the delivery at list prices). Unless the retries are exhausted: that is logged at
     * ERROR with the lost delta and rethrown, and the total then has to be repaired by hand.
     *
     * <p>After a lost race the {@code delivery} passed in is NOT the stored state: callers may only read its
     * identifiers afterwards.
     */
    public void completePending(String storeId, Delivery delivery, DeliveryCreationForm form,
                                Consumer<Delivery> callerChanges) {
        double costChange = deliveryCostSync.apply(storeId, delivery.getDeliveryId(), confirmedUnitCosts(form));
        applyCompletion(delivery, form, costChange);
        try {
            deliveriesRepository.save(delivery);
        } catch (ConditionalCheckFailedException conflict) {
            log.warn("Pending purchase save lost an optimistic-locking race, re-applying the completion: " +
                            "store={} delivery={} costChange={}", storeId, delivery.getDeliveryId(), costChange);
            reapplyCompletion(storeId, delivery.getDeliveryId(), form, costChange, callerChanges);
        }

        markClaimedAsOrdered(storeId, delivery, form.getEstimatedDeliveryAt());
    }

    private void reapplyCompletion(String storeId, String deliveryId, DeliveryCreationForm form, double costChange,
                                   Consumer<Delivery> callerChanges) {
        // A closure that throws anything but ConditionalCheckFailedException comes out of the retrying executor
        // wrapped, so a vanished delivery is flagged here and raised after the executor returns.
        boolean[] gone = {false};
        try {
            optimisticLockingExecutor.modifyAndSave(
                    () -> deliveriesRepository.findByIdConsistently(storeId, deliveryId),
                    current -> {
                        if (current == null) {
                            gone[0] = true;
                            return;
                        }
                        if (current.getOrderStatus() == null) {
                            // completed by someone else in the meantime: adding the delta again would count it twice
                            log.warn("Pending purchase already completed by a concurrent writer: store={} delivery={}",
                                    storeId, deliveryId);
                            return;
                        }
                        // Any other state (e.g. FAILED set meanwhile) is completed too: the supplier did place the
                        // order, and the first attempt would have overwritten the status the same way.
                        callerChanges.accept(current);
                        applyCompletion(current, form, costChange);
                    },
                    saved -> {
                        if (saved != null) {
                            deliveriesRepository.save(saved);
                        }
                    });
        } catch (OptimisticLockingExhaustedException e) {
            log.error("Pending purchase completion lost the save race on every retry, the cost delta is NOT applied " +
                            "and needs repairing by hand: store={} delivery={} supplierOrderNumber={} costChange={}",
                    storeId, deliveryId, form.getExternalDeliveryId(), costChange, e);
            throw e;
        }
        if (gone[0]) {
            String message = "Supplier holds the order, the delivery is gone: store=" + storeId + " delivery=" + deliveryId
                    + " supplierOrderNumber=" + form.getExternalDeliveryId();
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    private static void applyCompletion(Delivery delivery, DeliveryCreationForm form, double costChange) {
        delivery.setExternalDeliveryId(form.getExternalDeliveryId());
        delivery.setEstimatedDeliveryAt(form.getEstimatedDeliveryAt());
        if (!delivery.isDropship()) {
            // A dropship delivery carries no freight of ours: the supplier ships to the customer, so the
            // shipping, payment and tax terms of a warehouse delivery do not apply and must not be
            // overwritten by the purchase form.
            delivery.updateShippingCost(form.getShippingCost());
            delivery.updatePaymentCost(form.getPaymentCost());
            delivery.setPaymentTerms(form.getPaymentTerms());
            delivery.setTax(form.getTax());
        }
        delivery.setOrderStatus(null);
        delivery.increaseTotalCost(costChange);
    }

    /**
     * The delivery is already saved with the supplier's order number, so a failure here must not undo the
     * completion: an SQS redelivery would stop at the "no longer pending" guard and the order number would
     * be lost. Log loudly instead. The delivery itself is complete, but the affected items are stuck in
     * allocation, still claimed to it - neither ordered nor visible on the allocation screen. Release does
     * not reach them (the delivery is no longer AWAITING_APPROVAL, the only state it goes through); the one
     * in-application way out is deleting the allocations on the delivery's details screen, which gives the
     * items back to their orders but also drops them from the delivery. Restoring the intended state -
     * items ordered against this delivery - needs an engineer to re-run the marking. Each side gets its own
     * try/catch so a failure on one does not also skip the other.
     */
    public void markClaimedAsOrdered(String storeId, Delivery delivery, LocalDate estimatedDeliveryAt) {
        try {
            orderAllocationsManager.markClaimedAsOrdered(storeId, delivery.getDeliveryId(), estimatedDeliveryAt);
        } catch (RuntimeException e) {
            log.error("Claimed order allocations not marked as ordered - items remain claimed and stuck in " +
                            "allocation, needs an engineer to re-run the marking: " +
                            "store={} delivery={} provider={} estimatedDeliveryAt={}",
                    storeId, delivery.getDeliveryId(), delivery.getProvider(), estimatedDeliveryAt, e);
        }
        try {
            warehouseAllocationsManager.markClaimedAsOrdered(storeId, delivery.getDeliveryId());
        } catch (RuntimeException e) {
            log.error("Claimed warehouse allocations not marked as ordered - items remain claimed and stuck in " +
                            "allocation, needs an engineer to re-run the marking: " +
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
