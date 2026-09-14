package pl.commercelink.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.ClientShippingAddressChangeException.Reason;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.orders.notifications.OrderShippingAddressChangedEmailNotification;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class ClientShippingAddressChangeService {

    public static final String CHANGED_EVENT = EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED.name();

    private static final int MAX_FIELD_LENGTH = 100;

    private final OrdersRepository ordersRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final EmailClient emailClient;
    private final String appDomain;

    public ClientShippingAddressChangeService(OrdersRepository ordersRepository,
                                              OrderEventsRepository orderEventsRepository,
                                              EmailClient emailClient,
                                              @Value("${app.domain}") String appDomain) {
        this.ordersRepository = ordersRepository;
        this.orderEventsRepository = orderEventsRepository;
        this.emailClient = emailClient;
        this.appDomain = appDomain;
    }

    public boolean isEditable(Order order, Store store) {
        return store.isClientShippingAddressChangeEnabled()
                && store.supportsNotification(EmailNotificationType.CLIENT_VERIFICATION_CODE)
                && store.supportsNotification(EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED)
                && order.canChangeShippingAddress()
                && !orderEventsRepository.hasEvent(order.getOrderId(), EventType.email, CHANGED_EVENT);
    }

    public ShippingDetails validate(Order order, ShippingDetails requested) {
        ShippingDetails previous = order.getShippingDetails();
        if (previous == null) {
            throw new ClientShippingAddressChangeException(Reason.NOT_EDITABLE);
        }
        if (previous.isCountryChange(requested.getCountry())) {
            throw new ClientShippingAddressChangeException(Reason.COUNTRY_CHANGED);
        }
        ShippingDetails updated = previous.withEditableFieldsFrom(requested);
        if (!updated.isPlainTextWithin(MAX_FIELD_LENGTH) || !updated.isProperlyFilled()) {
            throw new ClientShippingAddressChangeException(Reason.INVALID_ADDRESS);
        }
        return updated;
    }

    public void change(Order order, ShippingDetails requested, Store store) {
        if (!isEditable(order, store)) {
            throw new ClientShippingAddressChangeException(Reason.NOT_EDITABLE);
        }
        ShippingDetails previous = order.getShippingDetails().copy();
        ShippingDetails updated = validate(order, requested);
        LocalDateTime now = LocalDateTime.now();

        order.setShippingDetails(updated);
        ordersRepository.save(order);
        if (sendConfirmation(order, store, previous, updated, now)) {
            orderEventsRepository.save(new OrderEvent(order.getOrderId(), EventType.email, CHANGED_EVENT, now));
        }
    }

    private boolean sendConfirmation(Order order, Store store, ShippingDetails previous, ShippingDetails updated, LocalDateTime changedAt) {
        Map<String, String> recipients = new LinkedHashMap<>();
        recipients.put(order.getBillingDetails().getEmail(), order.getBillingDetails().getName());
        if (isNotBlank(previous.getEmail())
                && !equalsIgnoreCase(previous.getEmail(), updated.getEmail())
                && !equalsIgnoreCase(previous.getEmail(), order.getBillingDetails().getEmail())) {
            recipients.put(previous.getEmail(), previous.getDisplayName());
        }

        String orderStatusLink = store.isClientOrderPageEnabled() ? order.createClientOrderUrl(appDomain) : null;
        boolean anySent = false;
        for (Map.Entry<String, String> recipient : recipients.entrySet()) {
            OrderShippingAddressChangedEmailNotification msg = new OrderShippingAddressChangedEmailNotification(
                    recipient.getKey(), recipient.getValue(), order.getOrderId(), orderStatusLink,
                    previous, updated, changedAt, store.getClientContactEmail());
            anySent |= emailClient.send(order.getStoreId(), EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED, msg);
        }
        return anySent;
    }
}
