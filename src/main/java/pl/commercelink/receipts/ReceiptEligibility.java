package pl.commercelink.receipts;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.ReceiptConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Which stores issue automatic e-receipts and which orders get one. A point-of-sale order gets one only when the
 * operator chose an e-receipt for the customer's own e-mail; otherwise the trigger raises an alert instead.
 */
@Component
public class ReceiptEligibility {

    private final ReceiptProviderFactory providerFactory;

    public ReceiptEligibility(ReceiptProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    public boolean storeReady(Store store) {
        String provider = store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER);
        return store.getReceiptConfiguration().isEnabled() && provider != null
                && providerFactory.getDescriptor(provider) != null;
    }

    /**
     * A consumer order without a closing document, with something to sell; the store's settings aside. Payment is not
     * a condition: the receipt declares the order's payment method, whether the money has arrived yet or not.
     */
    public boolean orderQualifies(Order order) {
        return order.getStatus() != OrderStatus.Cancelled
                && !order.isB2B()
                && !order.isInvoiced()
                && !order.isRMAReplacementOrder()
                && order.getTotalPrice() > 0;
    }

    public boolean automaticCandidate(Store store, Order order) {
        ReceiptConfiguration configuration = store.getReceiptConfiguration();
        return order.getStatus() == OrderStatus.Delivered
                && storeReady(store)
                && orderQualifies(order)
                && deliveredSinceEnabled(order, configuration.getEnabledAt())
                && (!order.isPointOfSale() || posEReceiptChosen(store, order));
    }

    /**
     * Whether a point-of-sale order has an e-receipt chosen for a real customer e-mail. The shop may have printed the
     * sale on its own cash register, so without this choice an automatic e-receipt could register the sale twice.
     */
    public boolean posEReceiptChosen(Store store, Order order) {
        if (BuyerEmail.of(order, store) == null) {
            return false;
        }
        return switch (store.getReceiptConfiguration().getPosReceiptMode()) {
            case E_RECEIPT -> true;
            case ASK -> order.isPosEReceiptRequested();
            case CASH_REGISTER -> false;
        };
    }

    /**
     * A delivered point-of-sale order that would otherwise get an automatic receipt but has neither a recorded receipt
     * nor a chosen e-receipt: the operator decides, the automation does not guess.
     */
    public boolean posDecisionMissing(Store store, Order order) {
        return order.isPointOfSale()
                && order.getStatus() == OrderStatus.Delivered
                && storeReady(store)
                && orderQualifies(order)
                && deliveredSinceEnabled(order, store.getReceiptConfiguration().getEnabledAt())
                && !posEReceiptChosen(store, order);
    }

    private static boolean deliveredSinceEnabled(Order order, LocalDateTime enabledAt) {
        Optional<LocalDateTime> delivered = order.getShipments().stream()
                .map(Shipment::getDeliveredAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
        return enabledAt != null && delivered.isPresent() && !delivered.get().isBefore(enabledAt);
    }
}
