package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.orders.PaymentStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.invoicing.api.Price;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@DynamoDBTable(tableName = "DeliveriesV2")
public class Delivery {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "deliveryId")
    private String deliveryId;
    @DynamoDBAttribute(attributeName = "externalDeliveryId")
    private String externalDeliveryId;

    @DynamoDBAttribute(attributeName = "orderedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime orderedAt;
    @DynamoDBAttribute(attributeName = "receivedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime receivedAt;

    @DynamoDBAttribute(attributeName = "estimatedDeliveryAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate estimatedDeliveryAt;

    @DynamoDBAttribute(attributeName = "provider")
    private String provider;
    @DynamoDBAttribute(attributeName = "paymentStatus")
    @DynamoDBTypeConvertedEnum
    private PaymentStatus paymentStatus;

    @DynamoDBAttribute(attributeName = "shippingCost")
    private double shippingCost;
    @DynamoDBAttribute(attributeName = "paymentCost")
    private double paymentCost;
    @DynamoDBAttribute(attributeName = "paymentTerms")
    private int paymentTerms;

    @DynamoDBAttribute(attributeName = "shipments")
    private List<Shipment> shipments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "events")
    private List<Event> deliveryEvents = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "documents")
    private List<Document> documents = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "managed")
    private boolean managed;
    @DynamoDBAttribute(attributeName = "invoiced")
    private boolean invoiced;
    @DynamoDBAttribute(attributeName = "synced")
    private boolean synced;

    // calculated on the fly based on current items distribution
    @DynamoDBIgnore
    private List<Allocation> allocations = new LinkedList<>();
    @DynamoDBIgnore
    private List<DeliveryItem> items = new LinkedList<>();

    // required by DynamoDb
    public Delivery() {

    }

    public Delivery(String storeId, String externalDeliveryId, String provider, PaymentStatus paymentStatus) {
        this.storeId = storeId;
        this.deliveryId = UUID.randomUUID().toString();
        this.externalDeliveryId = externalDeliveryId;
        this.provider = provider;
        this.paymentStatus = paymentStatus;
        this.orderedAt = LocalDateTime.now();
    }

    public Delivery(String storeId, String externalDeliveryId, String provider, PaymentStatus paymentStatus, LocalDate estimatedDeliveryAt, double shippingCost, double paymentCost, int paymentTerms) {
        this(storeId, externalDeliveryId, provider, paymentStatus);

        this.estimatedDeliveryAt = estimatedDeliveryAt;
        this.shippingCost = shippingCost;
        this.paymentCost = paymentCost;
        this.paymentTerms = paymentTerms;
    }

    public void add(Allocation allocation) {
        this.allocations.add(allocation);
    }

    public void add(List<Allocation> allocations) {
        this.allocations.addAll(allocations);
    }

    @DynamoDBIgnore
    public void markAsPaid() {
        this.paymentStatus = PaymentStatus.Paid;
    }

    @DynamoDBIgnore
    public void markAsReceived() {
        this.receivedAt = LocalDateTime.now();
        this.addEvent(new Event(EventType.action, "DELIVERY_RECEIVED", LocalDateTime.now()));
    }

    @DynamoDBIgnore
    public boolean isWaitingForPayment() {
        return paymentStatus != PaymentStatus.Paid;
    }

    @DynamoDBIgnore
    public boolean isWaitingForCollection() {
        return receivedAt == null;
    }

    @DynamoDBIgnore
    public boolean hasBeenReceived() {
        return receivedAt != null;
    }

    @DynamoDBIgnore
    public boolean hasDocumentOfType(DocumentType documentType) {
        return documents.stream().anyMatch(document -> document.getType() == documentType);
    }

    @DynamoDBIgnore
    public boolean hasExternalDocument(DocumentType documentType) {
        return documents.stream().anyMatch(doc -> doc.getType() == documentType && doc.isExternal());
    }

    @DynamoDBIgnore
    public Document findDocumentById(String documentId) {
        return documents.stream()
                .filter(doc -> StringUtils.equals(doc.getId(), documentId))
                .findFirst()
                .orElse(null);
    }

    @DynamoDBIgnore
    public LocalDate getPaymentDueDate() {
        if (orderedAt == null) {
            return null;
        }
        return orderedAt.toLocalDate().plusDays(paymentTerms);
    }

    @DynamoDBIgnore
    public Price getUnpaidAmount() {
        return Price.fromNet(allocations.stream().mapToDouble(Allocation::getTotalCost).sum() + paymentCost + shippingCost);
    }

    @DynamoDBIgnore
    public List<Allocation> getAllocations() {
        return allocations;
    }

    @DynamoDBIgnore
    public List<Allocation> getReceivedAllocationsFor(String mfn, AllocationType type) {
        return allocations.stream()
                .filter(a -> StringUtils.equals(a.getMfn(), mfn))
                .filter(a -> a.getType() == type)
                .filter(a -> !a.isInAllocation())
                .collect(Collectors.toList());
    }

    @DynamoDBIgnore
    public void setAllocations(List<Allocation> allocations) {
        this.allocations = allocations;
    }

    @DynamoDBIgnore
    public List<DeliveryItem> getItems() {
        return items;
    }

    @DynamoDBIgnore
    public void setItems(List<DeliveryItem> items) {
        this.items = items;
    }

    @DynamoDBIgnore
    public String getShortenedDeliveryId() {
        return ConversionUtil.getShortenedId(deliveryId);
    }

    // required by DynamoDb
    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getExternalDeliveryId() {
        return externalDeliveryId;
    }

    public void setExternalDeliveryId(String externalDeliveryId) {
        this.externalDeliveryId = externalDeliveryId;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public void setOrderedAt(LocalDateTime orderedAt) {
        this.orderedAt = orderedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDate getEstimatedDeliveryAt() {
        return estimatedDeliveryAt;
    }

    public void setEstimatedDeliveryAt(LocalDate estimatedDeliveryAt) {
        this.estimatedDeliveryAt = estimatedDeliveryAt;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public List<Shipment> getShipments() {
        return shipments;
    }

    public void setShipments(List<Shipment> shipments) {
        this.shipments = shipments;
    }

    public double getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(double shippingCost) {
        this.shippingCost = shippingCost;
    }

    public int getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(int paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public double getPaymentCost() {
        return paymentCost;
    }

    public void setPaymentCost(double paymentCost) {
        this.paymentCost = paymentCost;
    }

    public void addEvent(Event deliveryEvent) {
        this.deliveryEvents.add(deliveryEvent);
    }

    public List<Event> getDeliveryEvents() {
        return deliveryEvents.stream()
                .sorted(Comparator.comparing(Event::getCreatedAt))
                .collect(Collectors.toList());
    }

    public void setDeliveryEvents(List<Event> deliveryEvents) {
        this.deliveryEvents = deliveryEvents;
    }

    public boolean isManaged() {
        return managed;
    }

    public void setManaged(boolean managed) {
        this.managed = managed;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }

    public void addDocument(Document document) {
        this.documents.add(document);

        this.invoiced = hasDocumentOfType(DocumentType.InvoiceVat);
        if (document.getType() == DocumentType.InvoiceVat) {
            this.synced = false;
        }
    }

    public void removeDocument(Document document) {
        this.documents.remove(document);

        this.invoiced = hasDocumentOfType(DocumentType.InvoiceVat);
        if (document.getType() == DocumentType.InvoiceVat) {
            this.synced = false;
        }
    }

    public boolean isInvoiced() {
        return invoiced;
    }

    public void setInvoiced(boolean invoiced) {
        this.invoiced = invoiced;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
