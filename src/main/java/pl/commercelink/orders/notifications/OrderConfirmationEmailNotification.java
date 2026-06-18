package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.products.ProductCategoryLocalization;
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
            ProductCategoryLocalization productCategoryLocalization) {
        super(recipientEmail, recipientName);
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.shippingDetails = shippingDetails;
        this.documentType = documentType;
        this.personalCollection = personalCollection;

        this.products = orderItems.stream()
                .filter(o -> !o.hasCategory(ProductCategory.Services))
                .map(o -> LocalizedOrderItem.fromOrderItem(o, productCategoryLocalization))
                .collect(Collectors.toList());
        this.services = orderItems.stream()
                .filter(o -> o.hasCategory(ProductCategory.Services))
                .map(o -> LocalizedOrderItem.fromOrderItem(o, productCategoryLocalization))
                .collect(Collectors.toList());
    }

}