package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.localization.EnumLocalizer;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;
import java.util.stream.Collectors;

@Getter
class OrderConfirmationEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("totalAmount")
    private double totalAmount;
    @JsonProperty("paymentMethod")
    private String paymentMethod;
    @JsonProperty("products")
    private List<LocalizedOrderItem> products;
    @JsonProperty("services")
    private List<LocalizedOrderItem> services;
    @JsonProperty("shippingDetails")
    private ShippingDetails shippingDetails; // New field for shipping details
    @JsonProperty("documentType")
    private DocumentType documentType; // New field for receipt type
    @JsonProperty("personalCollection")
    private boolean personalCollection;

    OrderConfirmationEmailNotification(
            String recipientEmail, String recipientName, String orderId, double totalAmount, String paymentMethod,
            List<OrderItem> orderItems, ShippingDetails shippingDetails, DocumentType documentType, boolean personalCollection,
            EnumLocalizer enumLocalizer) {
        super(recipientEmail, recipientName);
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.shippingDetails = shippingDetails;
        this.documentType = documentType;
        this.personalCollection = personalCollection;

        this.products = orderItems.stream()
                .filter(OrderItem::isProduct)
                .map(o -> LocalizedOrderItem.fromOrderItem(o, enumLocalizer))
                .collect(Collectors.toList());
        this.services = orderItems.stream()
                .filter(OrderItem::isService)
                .map(o -> LocalizedOrderItem.fromOrderItem(o, enumLocalizer))
                .collect(Collectors.toList());
    }

}