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
 * first attempt. Runs after the save so a failed order write never leaves an attempt for an undelivered order, and
 * never throws — a receipt problem must not break the order update. A delivered point-of-sale order without the
 * operator's receipt decision gets a bell alert instead of an attempt: the shop may have printed it on its own cash
 * register, and a missing receipt can be fixed while a second fiscal record cannot.
 */
@Slf4j
@Component
public class ReceiptTrigger {

    private final StoresRepository storesRepository;
    private final ReceiptEligibility eligibility;
    private final ReceiptAttemptService attemptService;
    private final ReceiptAlerts alerts;

    public ReceiptTrigger(StoresRepository storesRepository, ReceiptEligibility eligibility,
                          @Lazy ReceiptAttemptService attemptService, ReceiptAlerts alerts) {
        this.storesRepository = storesRepository;
        this.eligibility = eligibility;
        this.attemptService = attemptService;
        this.alerts = alerts;
    }

    public void onOrderSaved(Order order) {
        boolean pos = order.isPointOfSale();
        if (order.getStatus() != OrderStatus.Delivered && !(pos && order.getStatus() == OrderStatus.Cancelled)) {
            return;
        }
        try {
            Store store = storesRepository.findById(order.getStoreId());
            if (store == null) {
                return;
            }
            if (pos) {
                if (eligibility.posDecisionMissing(store, order)
                        && attemptService.attemptsOf(store.getStoreId(), order.getOrderId()).isEmpty()) {
                    alerts.raisePosDecision(store.getStoreId(), order.getOrderId());
                    return;
                }
                alerts.resolvePosDecision(store.getStoreId(), order.getOrderId());
            }
            if (eligibility.automaticCandidate(store, order)) {
                attemptService.startAutomatic(store, order);
            }
        } catch (RuntimeException e) {
            log.error("Automatic receipt for order {} of store {} could not be started",
                    order.getOrderId(), order.getStoreId(), e);
        }
    }
}
