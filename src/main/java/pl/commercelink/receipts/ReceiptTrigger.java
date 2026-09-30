package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

/**
 * Called after the order lifecycle saved an order: a delivered consumer order of a store with e-receipts gets its
 * first attempt, and an order with its closing document settles the alerts of its blocked or failed attempts. Runs
 * after the save so a failed order write never leaves an attempt for an undelivered order, and never throws — a
 * receipt problem must not break the order update.
 */
@Slf4j
@Component
public class ReceiptTrigger {

    private final StoresRepository storesRepository;
    private final ReceiptEligibility eligibility;
    private final ReceiptAttemptService attemptService;

    public ReceiptTrigger(StoresRepository storesRepository, ReceiptEligibility eligibility,
                          @Lazy ReceiptAttemptService attemptService) {
        this.storesRepository = storesRepository;
        this.eligibility = eligibility;
        this.attemptService = attemptService;
    }

    public void onOrderSaved(Order order) {
        settleDeadAttemptAlerts(order);
        if (order.getStatus() != OrderStatus.Delivered) {
            return;
        }
        try {
            Store store = storesRepository.findById(order.getStoreId());
            if (store != null && eligibility.automaticCandidate(store, order)) {
                attemptService.startAutomatic(store, order);
            }
        } catch (RuntimeException e) {
            log.error("Automatic receipt for order {} of store {} could not be started",
                    order.getOrderId(), order.getStoreId(), e);
        }
    }

    /**
     * A receipt or invoice recorded after a blocked or failed e-receipt settles what that alert asked for, whatever
     * the order's status: a manual e-receipt can be blocked before delivery. Also called by saves that bypass the
     * order lifecycle (invoicing). Never throws.
     */
    public void settleDeadAttemptAlerts(Order order) {
        if (!order.isInvoiced() || order.getStatus() == OrderStatus.Cancelled) {
            return;
        }
        try {
            attemptService.resolveDeadAttemptAlerts(order.getStoreId(), order.getOrderId());
        } catch (RuntimeException e) {
            log.error("Receipt alerts of order {} of store {} could not be resolved",
                    order.getOrderId(), order.getStoreId(), e);
        }
    }
}
