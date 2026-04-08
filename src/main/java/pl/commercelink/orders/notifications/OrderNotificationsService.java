package pl.commercelink.orders.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.orders.*;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
class OrderNotificationsService {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    @Autowired
    private EmailClient emailClient;

    void send(Order order) {

        if (!order.isEmailNotificationsEnabled()) {
            return;
        }

        List<EmailNotificationType> emailNotificationsSent = new LinkedList<>();

        if (qualifiesForNotification(order, OrderStatus.New, EmailNotificationType.ORDER_CONFIRMATION)) {
            if (sendOrderConfirmationNotification(order)) {
                emailNotificationsSent.add(EmailNotificationType.ORDER_CONFIRMATION);
            }
        }

        if (qualifiesForNotification(order, OrderStatus.Assembly, EmailNotificationType.ORDER_ASSEMBLY)) {
            if (sendOrderAssemblyEmailNotification(order)) {
                emailNotificationsSent.add(EmailNotificationType.ORDER_ASSEMBLY);
            }
        }

        if (qualifiesForNotification(order, OrderStatus.Assembled, EmailNotificationType.ORDER_ASSEMBLED)) {
            if (sendOrderAssembledEmailNotification(order)) {
                emailNotificationsSent.add(EmailNotificationType.ORDER_ASSEMBLED);
            }
        }

        if (qualifiesForNotification(order, OrderStatus.Realization, EmailNotificationType.ORDER_REALIZATION)) {
            if (sendOrderRealizationEmailNotification(order)) {
                emailNotificationsSent.add(EmailNotificationType.ORDER_REALIZATION);
            }
        }

        if (qualifiesForNotification(order, OrderStatus.Shipping, EmailNotificationType.ORDER_SHIPPING)) {
            if (sendOrderShippingEmailNotification(order)) {
                emailNotificationsSent.add(EmailNotificationType.ORDER_SHIPPING);
            }
        }

        if (qualifiesForNotification(order, OrderStatus.Shipping, EmailNotificationType.ORDER_PICKUP)) {
            if (sendOrderPickupEmailNotification(order)) {
                emailNotificationsSent.add(EmailNotificationType.ORDER_PICKUP);
            }
        }

        if (qualifiesForNotification(order, OrderStatus.Delivered, EmailNotificationType.ORDER_REVIEW)) {

            if (order.getShipments().stream().allMatch(shipment ->
                    shipment.getDeliveredAt() != null &&
                            shipment.getDeliveredAt().isBefore(LocalDateTime.now().minusDays(2))
            )) {
                if (sendOrderReviewEmailNotification(order)) {
                    OrderReview review = order.getReview();
                    review.setStatus(OrderReviewStatus.InProgress);
                    review.setReferenceNo(order.getOrderId().split("-")[0]);
                    review.setRequestedAt(LocalDate.now());

                    emailNotificationsSent.add(EmailNotificationType.ORDER_REVIEW);
                }
            }
        }

        if (!emailNotificationsSent.isEmpty()) {
            for (EmailNotificationType notificationSent : emailNotificationsSent) {
                OrderEvent event = new OrderEvent(order.getOrderId(), EventType.email, notificationSent.name(), LocalDateTime.now());
                orderEventsRepository.save(event);
            }

            if (emailNotificationsSent.contains(EmailNotificationType.ORDER_REVIEW)) {
                ordersRepository.save(order);
            }
        }

    }

    private boolean sendOrderAssemblyEmailNotification(Order order) {
        // check if we have all the data needed to send the email
        if (order.getEstimatedAssemblyAt() == null || order.getEstimatedShippingAt() == null) {
            return false;
        }

        EmailNotification msg = new OrderAssemblyEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId(),
                order.getEstimatedAssemblyAt(),
                order.getEstimatedShippingAt(),
                order.isPersonalCollection()
        );

        return send(order.getStoreId(), msg, EmailNotificationType.ORDER_ASSEMBLY);
    }

    private boolean sendOrderAssembledEmailNotification(Order order) {
        // check if we have all the data needed to send the email
        if (order.getEstimatedShippingAt() == null) {
            return false;
        }

        EmailNotification msg = new OrderAssembledEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId(),
                order.getEstimatedShippingAt()
        );

        return send(order.getStoreId(), msg, EmailNotificationType.ORDER_ASSEMBLED);
    }

    private boolean sendOrderRealizationEmailNotification(Order order) {
        // check if we have all the data needed to send the email
        if (order.getEstimatedShippingAt() == null) {
            return false;
        }

        EmailNotification msg = new OrderRealizationEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId(),
                order.getEstimatedShippingAt()
        );

        return send(order.getStoreId(), msg, EmailNotificationType.ORDER_REALIZATION);
    }

    private boolean sendOrderPickupEmailNotification(Order order) {

        List<Shipment> collections = order.getShipments().stream()
                .filter(Shipment::hasCollectionData)
                .collect(Collectors.toList());

        if (collections.isEmpty()) {
            return false;
        }

        Shipment firstCollection = collections.iterator().next();

        EmailNotification msg = new OrderPickupEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId(),
                firstCollection.getShippedAt().toLocalDate()
        );

        return send(order.getStoreId(), msg, EmailNotificationType.ORDER_PICKUP);
    }

    private boolean sendOrderShippingEmailNotification(Order order) {

        List<Shipment> shipments = order.getShipments().stream()
                .filter(Shipment::hasShippingData)
                .collect(Collectors.toList());

        if (shipments.isEmpty()) {
            return false;
        }

        boolean isB2C = order.getBillingDetails() == null || !order.getBillingDetails().hasTaxId();

        OrderShippingEmailNotification msg = new OrderShippingEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId(),
                isB2C,
                !isB2C
        );

        for (Shipment shipment : shipments) {
            msg.addTrackingUrl(shipment.getTrackingUrl());
        }

        return send(order.getStoreId(), msg, EmailNotificationType.ORDER_SHIPPING);
    }

    private boolean sendOrderReviewEmailNotification(Order order) {
        if (order.getReview().getStatus() != OrderReviewStatus.ToBeCollected) {
            return false;
        }

        EmailNotification msg = new OrderReviewEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId()
        );

        return send(order.getStoreId(), msg, EmailNotificationType.ORDER_REVIEW);
    }

    void sendOrderAssemblyDateChangedEmailNotification(Order order, LocalDate oldAssemblyDate) {
        if (order.getEstimatedAssemblyAt() == null) {
            return;
        }

        EmailNotification msg = new OrderAssemblyDateChangedEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId(),
                oldAssemblyDate,
                order.getEstimatedAssemblyAt()
        );

        if (send(order.getStoreId(), msg, EmailNotificationType.ORDER_ASSEMBLY_DATE_CHANGED)) {
            OrderEvent event = new OrderEvent(order.getOrderId(), EventType.email, EmailNotificationType.ORDER_ASSEMBLY_DATE_CHANGED.name(), LocalDateTime.now());
            orderEventsRepository.save(event);
        }
    }

    private boolean sendOrderConfirmationNotification(Order order) {
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

        if (orderItems.isEmpty()) {
            return false;
        }

        if (!order.isPersonalCollection() && !order.hasShippingDetails()) {
            return false;
        }

        ShippingDetails shippingDetails = order.getShippingDetails();
        DocumentType documentType = order.getReceiptType();
        
        String paymentMethod = "";
        if (!order.getPayments().isEmpty()) {
            PaymentSource paymentSource = order.getPayments().get(0).getSource();
            paymentMethod = paymentSource != null ? paymentSource.name() : "";
        }

        EmailNotification msg = new OrderConfirmationEmailNotification(
                getRecipientEmail(order),
                getRecipientName(order),
                order.getOrderId(),
                order.getTotalPrice(),
                paymentMethod,
                orderItems,
                shippingDetails,
                documentType,
                order.isPersonalCollection()
        );

        return send(order.getStoreId(), msg, EmailNotificationType.ORDER_CONFIRMATION);
    }

    private boolean qualifiesForNotification(Order order, OrderStatus orderStatus, EmailNotificationType emailNotificationType) {
        return order.getStatus() == orderStatus && !orderEventsRepository.hasEvent(order.getOrderId(), EventType.email, emailNotificationType.name());
    }

    private String getRecipientEmail(Order order) {
        return order.getBillingDetails().getEmail();
    }

    private String getRecipientName(Order order) {
        return order.getBillingDetails().getName();
    }

    private boolean send(String storeId, EmailNotification msg, EmailNotificationType notificationType) {
        return emailClient.send(storeId, notificationType, msg);
    }

}
