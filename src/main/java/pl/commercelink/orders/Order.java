package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.baskets.Basket;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.Store;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@DynamoDBTable(tableName = "Orders")
public class Order {

    @DynamoDBHashKey(attributeName = "storeId")
    @DynamoDBIndexHashKey(globalSecondaryIndexNames = {"StoreIdOrderedAtIndex", "ExternalOrderIdIndex"}, attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "orderId")
    private String orderId;
    @DynamoDBAttribute(attributeName = "externalOrderId")
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "ExternalOrderIdIndex", attributeName = "externalOrderId")
    private String externalOrderId;

    @DynamoDBAttribute(attributeName = "affiliateId")
    private String affiliateId;
    @DynamoDBAttribute(attributeName = "gclid")
    private String gclid;
    // projected from BillingDetails.email, any change in place will be overwritten
    @DynamoDBAttribute(attributeName = "email")
    private String email;
    // has to be stored so that we do not need to fetch order items all the time
    @DynamoDBAttribute(attributeName = "totalPrice")
    private double totalPrice;

    @DynamoDBAttribute(attributeName = "orderedAt")
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "StoreIdOrderedAtIndex", attributeName = "orderedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime orderedAt;
    @DynamoDBAttribute(attributeName = "orderRealizationDays")
    private int orderRealizationDays;
    @DynamoDBAttribute(attributeName = "estimatedAssemblyAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate estimatedAssemblyAt;
    @DynamoDBAttribute(attributeName = "estimatedShippingAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate estimatedShippingAt;
    @DynamoDBAttribute(attributeName = "emailNotificationsEnabled")
    private boolean emailNotificationsEnabled;

    @DynamoDBAttribute(attributeName = "billingDetails")
    private BillingDetails billingDetails;
    @DynamoDBAttribute(attributeName = "shippingDetails")
    private ShippingDetails shippingDetails;

    @DynamoDBAttribute(attributeName = "comment")
    private String comment;
    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private OrderStatus status;
    @DynamoDBAttribute(attributeName = "source")
    private OrderSource source;
    @DynamoDBAttribute(attributeName = "review")
    private OrderReview review;
    @DynamoDBAttribute(attributeName = "payments")
    private List<Payment> payments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "shipments")
    private List<Shipment> shipments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "documents")
    private List<Document> documents = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "fulfilmentType")
    @DynamoDBTypeConvertedEnum
    private FulfilmentType fulfilmentType;

    // required by dynamodb
    public Order() {
    }

    public Order(String storeId) {
        this.storeId = storeId;
        this.orderId = UUID.randomUUID().toString();
        this.orderedAt = LocalDateTime.now();
        this.status = OrderStatus.New;
    }

    @DynamoDBIgnore
    public boolean hasStatus(OrderStatus status) {
        return this.status == status;
    }

    @DynamoDBIgnore
    public boolean hasOneOfStatuses(OrderStatus... statuses) {
        for (OrderStatus status : statuses) {
            if (this.status == status) {
                return true;
            }
        }
        return false;
    }

    @DynamoDBIgnore
    public boolean isFullyPaid() {
        return getUnpaidAmount() == 0;
    }

    @DynamoDBIgnore
    public boolean isInvoiced() {
        return isRMAReplacementOrder() || getClosingDocument().isPresent();
    }

    @DynamoDBIgnore
    public boolean isDelivered() {
        return shipments.stream().allMatch(shipment -> shipment.getDeliveredAt() != null);
    }

    @DynamoDBIgnore
    public boolean isSettled() {
        return isDelivered() && isFullyPaid() && isInvoiced();
    }

    @DynamoDBIgnore
    public void reopen() {
        if (hasOneOfStatuses(OrderStatus.Completed)) {
            this.status = OrderStatus.Delivered;
        }
    }

    @DynamoDBIgnore
    public boolean isInReviewForMoreThan(int days) {
        return review != null &&
                review.getStatus() == OrderReviewStatus.InProgress &&
                review.getRequestedAt() != null &&
                review.getRequestedAt().isBefore(LocalDate.now().minusDays(days));
    }

    @DynamoDBIgnore
    public boolean isAwaitingDocumentsGeneration() {
        return !getDocumentByType(DocumentType.GoodsIssue).isPresent();
    }

    @DynamoDBIgnore
    public boolean isAwaitingInvoiceGeneration() {
        return getNextInvoiceToIssue().isPresent();
    }

    @DynamoDBIgnore
    public boolean canTransitionToDelivered(OrderStatus newStatus) {
        if (newStatus != OrderStatus.Delivered || hasStatus(OrderStatus.Delivered)) {
            return true;
        }
        return hasBeenShippedOrIsReadyForCollection();
    }

    @DynamoDBIgnore
    public boolean hasBeenShippedOrIsReadyForCollection() {
        return getShipments().stream().allMatch(s -> s.hasCollectionData() || s.hasShippingData());
    }

    @DynamoDBIgnore
    public Optional<DocumentType> getNextInvoiceToIssue() {
        return getNextDocumentToIssue().filter(DocumentType::isB2BInvoice);
    }

    @DynamoDBIgnore
    public Optional<DocumentType> getNextDocumentToIssue() {
        if (isInvoiced()) {
            return Optional.empty();
        }

        boolean hasAdvanceInvoice = documents.stream()
                .anyMatch(r -> r.hasOneOfTypes(DocumentType.InvoiceAdvance));
        if (hasAdvanceInvoice) {
            return Optional.of(DocumentType.InvoiceFinal);
        }

        DocumentType type = billingDetails.hasTaxId()
                ? DocumentType.InvoiceVat
                : DocumentType.Receipt;

        return Optional.of(type);
    }

    @DynamoDBIgnore
    public DocumentType getReceiptType() {
        Optional<Document> op = getClosingDocument();
        if (op.isPresent()) {
            return op.get().getType();
        }

        return getNextDocumentToIssue().orElseGet(() -> billingDetails.hasTaxId() ? DocumentType.InvoiceVat : DocumentType.Receipt);
    }

    @DynamoDBIgnore
    public Optional<Document> getDocumentByType(DocumentType documentType) {
        return documents.stream()
                .filter(r -> r.getType() == documentType)
                .findFirst();
    }

    @DynamoDBIgnore
    public Optional<Document> getClosingDocument() {
        return documents.stream()
                .filter(r -> r.getType().isClosingInvoice())
                .findFirst();
    }

    public void addDocument(Document document) {
        documents.add(document);
    }

    public void addDocumentIfMissing(Document document) {
        if (document != null && !hasDocument(document)) {
            documents.add(document);
        }
    }

    private boolean hasDocument(Document document) {
        return documents.stream()
                .filter(s -> s.getType() == document.getType())
                .anyMatch(s -> Objects.equals(s.getId(), document.getId()));
    }

    @DynamoDBIgnore
    public double getPaidAmount() {
        return payments.stream()
                .mapToDouble(Payment::getAmount)
                .sum();
    }

    @DynamoDBIgnore
    public double getUnpaidAmount() {
        return totalPrice - getPaidAmount();
    }

    @DynamoDBIgnore
    public double getUnpaidAmountGross() {
        return getUnpaidAmount();
    }

    @DynamoDBIgnore
    public double getUnpaidAmountNet() {
        return Price.fromGross(getUnpaidAmount()).netValue();
    }

    @DynamoDBIgnore
    public void increaseRealizationDays(OrderItem orderItem, int estimatedDeliveryDays) {
        if (orderItem.hasCategory(ProductCategory.Services)) {
            this.orderRealizationDays = Math.max(orderRealizationDays, estimatedDeliveryDays);
        }
    }

    @DynamoDBIgnore
    public boolean isEligibleForRMACreation() {
        return hasOneOfStatuses(OrderStatus.Delivered, OrderStatus.Completed);
    }

    @DynamoDBIgnore
    public boolean isRMAReplacementOrder() {
        return source != null && "RMA".equals(source.getName());
    }

    @DynamoDBIgnore
    public boolean isMarketplaceOrder() {
        boolean isExternal = externalOrderId != null && !externalOrderId.isEmpty();
        boolean isMarketplaceSource = source != null && source.getType() == OrderSourceType.Marketplace;
        return isExternal && isMarketplaceSource;
    }

    @DynamoDBIgnore
    public Payment getLatestPayment() {
        return payments.get(payments.size() - 1);
    }

    @DynamoDBIgnore
    public void increaseTotalPrice(double amount) {
        this.totalPrice += amount;
    }

    @DynamoDBIgnore
    public void decreaseTotalPrice(double amount) {
        this.totalPrice -= amount;
    }

    @DynamoDBIgnore
    public LocalDateTime getLastEventDate() {
        Optional<Shipment> lastShipment = getShipments().stream()
                .filter(s -> s.getShippedAt() != null)
                .max(Comparator.comparing(Shipment::getShippedAt));

        if (lastShipment.isPresent()) {
            return lastShipment.get().getShippedAt();
        }

        if (estimatedShippingAt != null) {
            return estimatedShippingAt.atTime(11, 59, 59);
        }

        if (estimatedAssemblyAt != null) {
            return estimatedAssemblyAt.atTime(11, 59, 59);
        }

        return orderedAt;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getOrderId() {
        return orderId;
    }

    @DynamoDBIgnore
    public String getShortenedOrderId() {
        return ConversionUtil.getShortenedId(orderId);
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getEmail() {
        return billingDetails != null ? billingDetails.getEmail() : null;
    }

    public void setEmail(String email) {
        this.email = billingDetails != null ? billingDetails.getEmail() : null;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public void setOrderedAt(LocalDateTime orderedAt) {
        this.orderedAt = orderedAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public BillingDetails getBillingDetails() {
        return billingDetails;
    }

    public void setBillingDetails(BillingDetails billingDetails) {
        this.email = billingDetails.getEmail(); // projection
        this.billingDetails = billingDetails;
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(ShippingDetails shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public OrderSource getSource() {
        return source;
    }

    public void setSource(OrderSource source) {
        this.source = source;
    }

    public OrderReview getReview() {
        return review;
    }

    public void setReview(OrderReview review) {
        this.review = review;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }

    public List<Shipment> getShipments() {
        return shipments;
    }

    public void setShipments(List<Shipment> shipments) {
        this.shipments = shipments;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }

    public void addShipment(Shipment shipment) {
        this.shipments.add(shipment);
    }

    public void addPayment(Payment payment) {
        this.payments.add(payment);
    }

    public LocalDate getEstimatedAssemblyAt() {
        return estimatedAssemblyAt;
    }

    public void setEstimatedAssemblyAt(LocalDate estimatedAssemblyAt) {
        this.estimatedAssemblyAt = estimatedAssemblyAt;
    }

    public LocalDate getEstimatedShippingAt() {
        return estimatedShippingAt;
    }

    public void setEstimatedShippingAt(LocalDate estimatedShippingAt) {
        this.estimatedShippingAt = estimatedShippingAt;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public void setExternalOrderId(String externalOrderId) {
        this.externalOrderId = externalOrderId;
    }

    public String getGclid() {
        return gclid;
    }

    public void setGclid(String gclid) {
        this.gclid = gclid;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    @DynamoDBIgnore
    public void markAsPaid() {
        double paidAmount = payments.stream().mapToDouble(Payment::getAmount).sum();
        double unpaidAmount = totalPrice - paidAmount;

        Optional<Payment> op = payments
                .stream()
                .filter(Payment::isUnsettled)
                .findFirst();

        if (op.isPresent()) {
            op.get().setAmount(unpaidAmount);
        } else {
            payments.add(new Payment("", "", PaymentSource.DirectDebit, unpaidAmount, 0));
        }
    }

    public FulfilmentType getFulfilmentType() {
        return fulfilmentType;
    }

    public void setFulfilmentType(FulfilmentType fulfilmentType) {
        this.fulfilmentType = fulfilmentType;
    }

    public int getOrderRealizationDays() {
        return orderRealizationDays;
    }

    public void setOrderRealizationDays(int orderRealizationDays) {
        this.orderRealizationDays = orderRealizationDays;
    }

    @DynamoDBIgnore
    public LocalDate updateEstimatedAssemblyAt(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            return estimatedAssemblyAt;
        }

        if (estimatedAssemblyAt == null || deliveryDate.isAfter(estimatedAssemblyAt)) {
            estimatedAssemblyAt = deliveryDate;
            estimatedShippingAt = addWeekdayDays(deliveryDate, orderRealizationDays);
        }

        return estimatedAssemblyAt;
    }

    private LocalDate addWeekdayDays(LocalDate start, int realizationDays) {
        LocalDate date = start;
        int addedDays = 0;
        while (addedDays < realizationDays) {
            date = date.plusDays(1);
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                addedDays++;
            }
        }
        return date;
    }

    @DynamoDBIgnore
    public boolean hasShippingDetails() {
        return shippingDetails != null && shippingDetails.isProperlyFilled();
    }

    @DynamoDBIgnore
    public boolean isPersonalCollection() {
        return shipments.stream().anyMatch(shipment -> shipment.getType() == ShipmentType.PersonalCollection);
    }

    public static class Builder {

        private final Order order;

        private Builder(String storeId, String affiliateId, int orderRealizationDays, boolean emailNotificationsEnabled, String comment, OrderReviewStatus reviewStatus, FulfilmentType fulfilmentType, double totalPrice, BillingDetails billingDetails, ShippingDetails shippingDetails, OrderSource source) {
            order = new Order(storeId);

            order.setAffiliateId(affiliateId);
            order.setSource(source != null ? source : new OrderSource("", OrderSourceType.Other));
            order.setOrderRealizationDays(orderRealizationDays);
            order.setEmailNotificationsEnabled(emailNotificationsEnabled);
            order.setComment(comment);
            order.setReview(new OrderReview(reviewStatus));
            order.setFulfilmentType(fulfilmentType);
            order.setTotalPrice(totalPrice);

            // billing and shipping details
            order.setBillingDetails(billingDetails.copy());
            order.setShippingDetails(shippingDetails.copy());

            // shipment
            Shipment shipment = new Shipment();
            shipment.setType(ShipmentType.Courier);
            order.addShipment(shipment);

            // payment
            order.addPayment(new Payment(PaymentSource.DirectDebit));
        }

        public Builder(Store store, Basket basket) {
            this(
                    store.getStoreId(), basket.getAffiliateId(), DeliveryDays.calculate(store, basket).getMaxRealizationDays(), true, basket.getComment(),
                    OrderReviewStatus.ToBeCollected, basket.getFulfilmentType(), basket.getTotalPrice(),
                    basket.getBillingDetails(), basket.getShippingDetails(), basket.getSource()
            );
        }

        public Builder(Order original) {
            this(
                    original.getStoreId(), null, 1, false, null,
                    OrderReviewStatus.NotApplicable, FulfilmentType.WarehouseFulfilment, 0,
                    original.getBillingDetails(), original.getShippingDetails(), new OrderSource("RMA", OrderSourceType.Other)
            );
        }

        public Builder withBillingDetails(BillingDetails billingDetails) {
            order.setBillingDetails(billingDetails);
            return this;
        }

        public Builder withShippingDetails(ShippingDetails shippingDetails) {
            order.setShippingDetails(shippingDetails);
            return this;
        }

        public Builder withShipmentType(ShipmentType shipmentType) {
            order.getShipments().get(0).setType(shipmentType);
            return this;
        }

        public Builder withPaymentSource(PaymentSource source) {
            order.getPayments().get(0).setSource(source);
            return this;
        }

        public Builder withPayment(Payment payment) {
            order.getPayments().set(0, payment);
            return this;
        }

        public Builder withOrderId(String orderId) {
            order.setOrderId(orderId);
            return this;
        }

        public Builder withExternalOrderId(String externalOrderId) {
            order.setExternalOrderId(externalOrderId);
            return this;
        }

        public Order build() {
            return order;
        }
    }

}