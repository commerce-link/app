package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.notifications.EmailNotificationType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Component
public class RMALifecycle {

    private final RMARepository rmaRepository;
    private final EmailClient emailClient;
    private final String appDomain;

    private final Map<RMAStatus, Consumer<RMA>> notificationHandlers;
    private final Map<RMAItemStatus, BiConsumer<RMA, List<RMAItem>>> itemNotificationHandlers;

    public RMALifecycle(RMARepository rmaRepository, EmailClient emailClient, @Value("${app.domain}") String appDomain) {
        this.rmaRepository = rmaRepository;
        this.emailClient = emailClient;
        this.appDomain = appDomain;

        this.notificationHandlers = new HashMap<>();
        this.notificationHandlers.put(RMAStatus.Approved, this::sendCarrierArrangement);
        this.notificationHandlers.put(RMAStatus.Rejected, this::sendRejected);
        this.notificationHandlers.put(RMAStatus.ItemsReceived, this::sendItemsReceived);
        this.notificationHandlers.put(RMAStatus.Processing, this::sendRMAProcessingStarted);

        this.itemNotificationHandlers = new HashMap<>();
        this.itemNotificationHandlers.put(RMAItemStatus.SentForRepair, this::sendItemsAccepted);
        this.itemNotificationHandlers.put(RMAItemStatus.MovedToWarehouse, this::sendItemsAccepted);
        this.itemNotificationHandlers.put(RMAItemStatus.ReturnedToClient, this::sendItemsReturnToClient);
    }

    public void update(RMA rma) {
        update(rma, Collections.emptyList(), false);
    }

    public void update(RMA rma, List<RMAItem> updatedItems) {
        update(rma, updatedItems, false);
    }

    public void update(RMA rma, List<RMAItem> updatedItems, boolean emailSilent) {
        if (!emailSilent && rma.isEmailNotificationsEnabled()) {
            notificationHandlers.getOrDefault(rma.getStatus(), r -> {}).accept(rma);

            if (!updatedItems.isEmpty()) {
                RMAItemStatus status = updatedItems.get(0).getStatus();
                itemNotificationHandlers.getOrDefault(status, (r, items) -> {}).accept(rma, updatedItems);
            }
        }

        rmaRepository.save(rma);
    }

    private void sendCarrierArrangement(RMA rma) {
        if (qualifiesForNotification(rma, RMAStatus.Approved, EmailNotificationType.RMA_CARRIER_ARRANGEMENT)) {
            String rmaClientLink = appDomain + "/store/" + rma.getStoreId() + "/client/rma/" + rma.getRmaId();
            RMACarrierArrangeEmailNotification msg = new RMACarrierArrangeEmailNotification(
                    rma.getEmail(),
                    rma.getEmail(),
                    rma.getRmaId(),
                    rma.getOrderId(),
                    rma.getStatus(),
                    rmaClientLink
            );
            sendAndRecordEvent(rma, EmailNotificationType.RMA_CARRIER_ARRANGEMENT, msg);
        }
    }

    private void sendRejected(RMA rma) {
        if (qualifiesForNotification(rma, RMAStatus.Rejected, EmailNotificationType.RMA_REJECTED)) {
            String rmaClientLink = appDomain + "/store/" + rma.getStoreId() + "/client/rma/" + rma.getRmaId();
            RMARejectedEmailNotification msg = new RMARejectedEmailNotification(
                    rma.getEmail(),
                    rma.getEmail(),
                    rma.getRmaId(),
                    rma.getOrderId(),
                    rma.getRejectionReason(),
                    rmaClientLink
            );
            sendAndRecordEvent(rma, EmailNotificationType.RMA_REJECTED, msg);
        }
    }

    private void sendItemsReceived(RMA rma) {
        if (qualifiesForNotification(rma, RMAStatus.ItemsReceived, EmailNotificationType.RMA_ITEMS_RECEIVED)) {
            RMAWarehouseReceivedEmailNotification msg = new RMAWarehouseReceivedEmailNotification(
                    rma.getEmail(),
                    rma.getEmail(),
                    rma.getRmaId(),
                    rma.getOrderId()
            );
            sendAndRecordEvent(rma, EmailNotificationType.RMA_ITEMS_RECEIVED, msg);
        }
    }

    private void sendRMAProcessingStarted(RMA rma) {
        if (qualifiesForNotification(rma, RMAStatus.Processing, EmailNotificationType.RMA_PROCESSING_STARTED)) {
            RMAProcessingStartedNotification msg = new RMAProcessingStartedNotification(
                    rma.getEmail(),
                    rma.getEmail(),
                    rma.getRmaId(),
                    rma.getOrderId()
            );
            sendAndRecordEvent(rma, EmailNotificationType.RMA_PROCESSING_STARTED, msg);
        }
    }

    private void sendItemsAccepted(RMA rma, List<RMAItem> updatedItems) {
        if (updatedItems.isEmpty()) return;
        RMAItemAcceptNotification msg = new RMAItemAcceptNotification(rma.getEmail(), rma.getEmail(), rma.getRmaId(), rma.getOrderId(), updatedItems);
        sendAndRecordEvent(rma, EmailNotificationType.RMA_ITEMS_ACCEPTED, msg);
    }

    private void sendItemsReturnToClient(RMA rma, List<RMAItem> updatedItems) {
        if (updatedItems.isEmpty()) return;
        RMAItemSendToClientNotification msg = new RMAItemSendToClientNotification(rma.getEmail(), rma.getEmail(), rma.getRmaId(), rma.getOrderId(), updatedItems, rma.getShipments());
        sendAndRecordEvent(rma, EmailNotificationType.RMA_ITEMS_SEND_TO_CLIENT, msg);
    }

    private void sendAndRecordEvent(RMA rma, EmailNotificationType type, EmailNotification msg) {
        boolean emailSentSuccess = emailClient.send(rma.getStoreId(), type, msg);
        if (emailSentSuccess) {
            rma.addEvent(createEventFor(type));
        }
    }

    private boolean qualifiesForNotification(RMA rma, RMAStatus rmaStatus, EmailNotificationType emailNotificationType) {
        return rma.getStatus() == rmaStatus && !rma.hasEvent(createEventFor(emailNotificationType));
    }

    private Event createEventFor(EmailNotificationType emailNotificationType) {
        return new Event(EventType.email, emailNotificationType.name(), LocalDateTime.now());
    }

}
