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

/** Which stores issue automatic e-receipts and which orders get one. */
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
                && deliveredSinceEnabled(order, configuration.getEnabledAt());
    }

    private static boolean deliveredSinceEnabled(Order order, LocalDateTime enabledAt) {
        Optional<LocalDateTime> delivered = order.getShipments().stream()
                .map(Shipment::getDeliveredAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
        return enabledAt != null && delivered.isPresent() && !delivered.get().isBefore(enabledAt);
    }
}
