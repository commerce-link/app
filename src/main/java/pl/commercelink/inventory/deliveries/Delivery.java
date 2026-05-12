package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.starter.util.ConversionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@DynamoDBTable(tableName = "Deliveries")
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

    @DynamoDBAttribute(attributeName = "shippingCost")
    private double shippingCost;
    @DynamoDBAttribute(attributeName = "paymentCost")
    private double paymentCost;
    @DynamoDBAttribute(attributeName = "paymentTerms")
    private int paymentTerms;

    @DynamoDBAttribute(attributeName = "totalCost")
    private double totalCost;
    @DynamoDBAttribute(attributeName = "tax")
    private double tax = DEFAULT_VAT_RATE;

    @DynamoDBAttribute(attributeName = "payments")
    private List<Payment> payments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "shipments")
    private List<Shipment> shipments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "events")
    private List<Event> events = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "documents")
    private List<Document> documents = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "managed")
    private boolean managed;
    @DynamoDBAttribute(attributeName = "invoiced")
    private boolean invoiced;
    @DynamoDBAttribute(attributeName = "synced")
    private boolean synced;
    @DynamoDBAttribute(attributeName = "paid")
    private boolean paid;

    // calculated on the fly based on current items distribution
    @DynamoDBIgnore
    private List<Allocation> allocations = new LinkedList<>();
    @DynamoDBIgnore
    private List<DeliveryItem> items = new LinkedList<>();

    // required by DynamoDb
    public Delivery() {

    }

    public Delivery(String storeId, String externalDeliveryId, String provider) {
        this.storeId = storeId;
        this.deliveryId = UUID.randomUUID().toString();
        this.externalDeliveryId = externalDeliveryId;
        this.provider = provider;
        this.orderedAt = LocalDateTime.now();
    }

    public Delivery(String storeId, String externalDeliveryId, String provider, LocalDate estimatedDeliveryAt, double shippingCost, double paymentCost, int paymentTerms, double tax) {
        this(storeId, externalDeliveryId, provider);

        this.estimatedDeliveryAt = estimatedDeliveryAt;
        this.shippingCost = shippingCost;
        this.paymentCost = paymentCost;
        this.paymentTerms = paymentTerms;
        this.tax = tax;
        this.totalCost = paymentCost + shippingCost;
    }

    public void add(Allocation allocation) {
        this.allocations.add(allocation);
    }

    public void add(List<Allocation> allocations) {
        this.allocations.addAll(allocations);
    }

    @DynamoDBIgnore
    public void markAsReceived() {
        this.receivedAt = LocalDateTime.now();
        this.addEvent(new Event(EventType.action, "DELIVERY_RECEIVED", LocalDateTime.now()));
    }

    @DynamoDBIgnore
    public boolean isWaitingForPayment() {
        return !isFullyPaid();
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
    public boolean hasMultiplePayments() {
        return payments.size() > 1;
    }

    @DynamoDBIgnore
    public double getUnpaidAmount() {
        return scale(getTotalCostGross() - getPaidAmount());
    }

    @DynamoDBIgnore
    public double getUnpaidAmountGross() {
        return getUnpaidAmount();
    }

    @DynamoDBIgnore
    public double getUnpaidAmountNet() {
        return scale(Price.fromGross(getUnpaidAmount(), tax).netValue());
    }

    @DynamoDBIgnore
    public double getPaidAmount() {
        return payments.stream().mapToDouble(Payment::getAppliedAmount).sum();
    }

    @DynamoDBIgnore
    public void transferPaymentFrom(Delivery source, double movedCost) {
        if (source.payments.isEmpty()) {
            return;
        }

        if (source.hasMultiplePayments()) {
            throw new IllegalArgumentException("Delivery with multiple payments can't be merged. First, consolidate or remove payments.");
        }

        Payment sourcePayment = source.payments.getFirst();
        double movedCostGross = Price.fromNet(movedCost, source.tax).grossValue();
        double originalCostGross = source.getTotalCostGross() + movedCostGross;

        if (Math.abs(sourcePayment.getAppliedAmount() - originalCostGross) > 0.005) {
            throw new IllegalArgumentException("Delivery with partial payment can't be split. First, settle the payment in full or remove it.");
        }

        Payment splitOff = sourcePayment.split(movedCostGross);

        if (sourcePayment.getAmount() == 0 && sourcePayment.getFee() == 0) {
            source.payments.clear();
        }

        Payment match = payments.stream()
                .filter(p -> p.matches(splitOff))
                .findFirst()
                .orElse(null);
        if (match != null) {
            match.absorb(splitOff);
        } else {
            payments.add(splitOff);
        }

        source.recomputePaid();
        recomputePaid();
    }

    @DynamoDBIgnore
    public boolean isFullyPaid() {
        return getPaidAmount() > 0 && getUnpaidAmount() == 0;
    }

    @DynamoDBIgnore
    public boolean isUnderpaid() {
        return getPaidAmount() > 0 && getUnpaidAmount() > 0;
    }

    @DynamoDBIgnore
    public boolean isOverpaid() {
        return getUnpaidAmount() < 0;
    }

    @DynamoDBIgnore
    public Payment getPendingPayment() {
        return payments.stream().filter(Payment::isUnsettled).findFirst().orElse(null);
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }

    public void addPayment(Payment payment) {
        this.payments.add(payment);
        recomputePaid();
    }

    public void clearPayments() {
        this.payments.clear();
        recomputePaid();
    }

    @DynamoDBIgnore
    public void recomputePaid() {
        this.paid = isFullyPaid();
    }

    @DynamoDBIgnore
    public double getTotalCostGross() {
        return Price.fromNet(totalCost, tax).grossValue();
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

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
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

    @DynamoDBIgnore
    public void updateShippingCost(double newShippingCost) {
        this.totalCost = scale(this.totalCost + (newShippingCost - this.shippingCost));
        this.shippingCost = newShippingCost;
        recomputePaid();
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

    @DynamoDBIgnore
    public void updatePaymentCost(double newPaymentCost) {
        this.totalCost = scale(this.totalCost + (newPaymentCost - this.paymentCost));
        this.paymentCost = newPaymentCost;
        recomputePaid();
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    @DynamoDBIgnore
    public void increaseTotalCost(double amount) {
        this.totalCost = scale(this.totalCost + amount);
        recomputePaid();
    }

    @DynamoDBIgnore
    public void decreaseTotalCost(double amount) {
        this.totalCost = scale(this.totalCost - amount);
        recomputePaid();
    }

    @DynamoDBIgnore
    public void recomputeTotalCost(List<Allocation> allocations) {
        double allocationsCost = allocations.stream().mapToDouble(Allocation::getTotalCost).sum();
        this.totalCost = scale(allocationsCost + paymentCost + shippingCost);
        recomputePaid();
    }

    private static double scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public double getTax() {
        return tax;
    }

    public void setTax(double tax) {
        this.tax = tax;
    }

    public void addEvent(Event deliveryEvent) {
        this.events.add(deliveryEvent);
    }

    public List<Event> getEvents() {
        return events.stream()
                .sorted(Comparator.comparing(Event::getCreatedAt))
                .collect(Collectors.toList());
    }

    public void setEvents(List<Event> events) {
        this.events = events;
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
