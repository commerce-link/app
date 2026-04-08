package pl.commercelink.orders.notifications;

import java.util.Arrays;
import java.util.List;

public enum EmailNotificationType {
    ORDER_CONFIRMATION("OrderConfirmationTemplate", Arrays.asList("orderId", "totalAmount", "paymentMethod", "products (category, name, quantity, price)", "services (category, name, quantity, price)", "shippingDetails", "receiptType", "personalCollection")),
    ORDER_ASSEMBLY("OrderAssemblyTemplate", Arrays.asList("orderId", "estimatedAssemblyDate", "estimatedShippingDate", "personalCollection")),
    ORDER_ASSEMBLED("OrderAssembledTemplate", Arrays.asList("orderId", "estimatedShippingDate")),
    ORDER_REALIZATION("OrderRealizationTemplate", Arrays.asList("orderId", "estimatedShippingDate")),
    ORDER_SHIPPING("OrderShippingTemplate", Arrays.asList("orderId", "isReceipt", "isInvoice", "trackingUrls")),
    ORDER_PICKUP("OrderPickupTemplate", Arrays.asList("orderId", "estimatedCollectionDate")),
    ORDER_INVOICE("OrderInvoiceTemplate", Arrays.asList("orderId", "invoiceNumber")),
    ORDER_REVIEW("OrderReviewTemplate", Arrays.asList("orderId")),
    ORDER_ASSEMBLY_DATE_CHANGED("OrderAssemblyDateChangedTemplate", Arrays.asList("orderId", "oldAssemblyDate", "newAssemblyDate")),
    ORDER_INVOICE_PROFORMA("OrderInvoiceProformaTemplate", Arrays.asList("invoiceNumber")),
    RMA_CARRIER_ARRANGEMENT("RMACarrierArrangementTemplate", Arrays.asList("rmaId", "orderId", "status", "rmaClientLink")),
    RMA_CARRIER_CONFIRMATION("RMACarrierConfirmationTemplate", Arrays.asList("rmaId", "orderId", "shippingDetails", "trackingUrls")),
    RMA_REJECTED("RMARejectedTemplate", Arrays.asList("rmaId", "orderId", "rejectionReason", "rmaClientLink")),
    RMA_ITEMS_RECEIVED("RMAItemsReceivedTemplate", Arrays.asList("rmaId", "orderId")),
    RMA_PROCESSING_STARTED("RMAProcessingStartedTemplate", Arrays.asList("rmaId", "orderId")),
    RMA_ITEMS_ACCEPTED("RMAItemsAcceptedTemplate", Arrays.asList("rmaId", "orderId", "rmaItems")),
    RMA_ITEMS_SEND_TO_CLIENT("RMAItemsSendToClient", Arrays.asList("rmaId", "orderId", "rmaItems", "shipments"));

    private final String templateName;
    private final List<String> parameters;

    EmailNotificationType(String templateName, List<String> parameters) {
        this.templateName = templateName;
        this.parameters = parameters;
    }

    public String getTemplateName() {
        return templateName;
    }

    public List<String> getParameters() {
        return parameters;
    }
}
