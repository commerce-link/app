package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderIdRefreshService {

    static final int MAX_SQS_ATTEMPTS = 6;

    private static final String ORDER_ID_CONFIRMED_EVENT = "DELIVERY_ORDER_ID_CONFIRMED";
    private static final String ORDER_ID_UNCONFIRMED_EVENT = "DELIVERY_ORDER_ID_UNCONFIRMED";

    private final DeliveriesRepository deliveriesRepository;
    private final StoreSupplierProviderResolver providerResolver;

    public void refresh(OrderIdRefreshEventRequest request, int attempt) {
        if (attempt > MAX_SQS_ATTEMPTS) {
            return;
        }
        Delivery delivery = deliveriesRepository.findById(request.getStoreId(), request.getDeliveryId());
        if (delivery == null || !request.getPurchaseRef().equals(delivery.getPurchaseRef())) {
            return;
        }
        SupplierProvider provider = resolveProvider(request.getStoreId(), request.getProvider());
        if (provider == null) {
            recordUnconfirmed(delivery);
            return;
        }
        Optional<String> confirmed = lookup(provider, request.getPurchaseRef());
        if (confirmed.isPresent()) {
            applyConfirmedId(delivery, confirmed.get());
            return;
        }
        if (attempt >= MAX_SQS_ATTEMPTS) {
            recordUnconfirmed(delivery);
        }
        throw new ExternalOrderIdPendingException(request.getDeliveryId(), attempt);
    }

    public enum ManualRefreshOutcome { CONFIRMED, STILL_PENDING, UNAVAILABLE }

    public ManualRefreshOutcome refreshManually(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null || StringUtils.isBlank(delivery.getPurchaseRef())) {
            return ManualRefreshOutcome.UNAVAILABLE;
        }
        SupplierProvider provider = resolveProvider(storeId, delivery.getProvider());
        if (provider == null) {
            return ManualRefreshOutcome.UNAVAILABLE;
        }
        Optional<String> confirmed = lookup(provider, delivery.getPurchaseRef());
        if (confirmed.isEmpty()) {
            return ManualRefreshOutcome.STILL_PENDING;
        }
        applyConfirmedId(delivery, confirmed.get());
        return ManualRefreshOutcome.CONFIRMED;
    }

    private SupplierProvider resolveProvider(String storeId, String provider) {
        try {
            return providerResolver.resolve(storeId, provider);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Optional<String> lookup(SupplierProvider provider, String purchaseRef) {
        try {
            return provider.confirmedOrderId(purchaseRef);
        } catch (SupplierOrderException e) {
            return Optional.empty();
        }
    }

    private void applyConfirmedId(Delivery delivery, String confirmedId) {
        boolean idChanged = !confirmedId.equals(delivery.getExternalDeliveryId());
        if (!idChanged && !delivery.isExternalDeliveryIdProvisional()) {
            return;
        }
        delivery.setExternalDeliveryId(confirmedId);
        delivery.setExternalDeliveryIdProvisional(false);
        if (idChanged) {
            delivery.addEvent(new Event(EventType.action, ORDER_ID_CONFIRMED_EVENT, LocalDateTime.now()));
        }
        deliveriesRepository.save(delivery);
    }

    private void recordUnconfirmed(Delivery delivery) {
        delivery.addEvent(new Event(EventType.action, ORDER_ID_UNCONFIRMED_EVENT, LocalDateTime.now()));
        deliveriesRepository.save(delivery);
    }
}
