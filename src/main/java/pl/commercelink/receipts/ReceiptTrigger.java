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
 * first attempt, and the alerts of its blocked or failed attempts follow the order page's rule. Runs
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
        reconcileDeadAttemptAlerts(order);
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
     * Keeps the bell alerts of the order's blocked or failed attempts in line with the order page
     * ({@link ReceiptAttemptService#reconcileDeadAttemptAlerts(Order)}), whatever the order's status: a manual
     * e-receipt can be blocked before delivery. Also called by saves that bypass the order lifecycle (invoicing).
     * Never throws.
     */
    public void reconcileDeadAttemptAlerts(Order order) {
        try {
            attemptService.reconcileDeadAttemptAlerts(order);
        } catch (RuntimeException e) {
            log.error("Receipt alerts of order {} of store {} could not be reconciled",
                    order.getOrderId(), order.getStoreId(), e);
        }
    }

    /**
     * Whether the order's blocked or failed attempts no longer ask for anything: the order has its closing document
     * (a receipt from the shop's cash register, an invoice), or it is cancelled, so there is no sale left to receipt
     * and "Wystaw ponownie" is not offered (product owner's decision). Only dead attempts read it: they fiscalised
     * nothing, so neither reason hides a registered sale; a live or fiscalised attempt keeps its own problem. The bell
     * ({@link ReceiptAttemptService#reconcileDeadAttemptAlerts(Order)}) and the order page ({@link ReceiptOrderView})
     * read the same rule.
     */
    static boolean settlesDeadAttempts(Order order) {
        return order.getStatus() == OrderStatus.Cancelled || order.isInvoiced();
    }
}
