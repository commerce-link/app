package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.baskets.Basket;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.Store;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBTable(tableName = "Orders")
public class Order {

    public static final int PREFERRED_SHIPPING_WINDOW_DAYS = 14;

    @DynamoDBHashKey(attributeName = "storeId")
    @DynamoDBIndexHashKey(globalSecondaryIndexNames = {"StoreIdOrderedAtIndex", "ExternalOrderIdIndex"}, attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "orderId")
    private String orderId;
    @DynamoDBAttribute(attributeName = "externalOrderId")
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "ExternalOrderIdIndex", attributeName = "externalOrderId")
    private String externalOrderId;
    // set on the child order created by createSplit(); externalOrderId stays unique to the parent
    @DynamoDBAttribute(attributeName = "splitFromOrderId")
    @Getter
    @Setter
    private String splitFromOrderId;
    @DynamoDBAttribute(attributeName = "externalSupplierId")
    private String externalSupplierId;

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
    @DynamoDBAttribute(attributeName = "preferredShippingAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Getter
    @Setter
    private LocalDate preferredShippingAt;
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
    @DynamoDBVersionAttribute
    private Long version;

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
    public boolean isClosed() {
        return hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled);
    }

    /** A courier can be ordered once the goods are assembled; earlier the parcel contents are not known. */
    @DynamoDBIgnore
    public boolean canOrderShipment() {
        return !status.isOneOf(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly);
    }

    @DynamoDBIgnore
    public boolean isFullyPaid() {
        return getUnpaidAmount() == 0;
    }

    /** A sale at the shop's counter, created from the dashboard's point of sale. */
    @DynamoDBIgnore
    public boolean isPointOfSale() {
        return source != null && source.getType() == OrderSourceType.PointOfSale;
    }

    @DynamoDBIgnore
    public boolean isInvoiced() {
        return isRMAReplacementOrder() || getClosingDocument().isPresent();
    }

    @DynamoDBIgnore
    public boolean isDelivered() {
        // allMatch is true for no shipments: an order without any (legacy data) has delivered nothing
        return !shipments.isEmpty() && shipments.stream().allMatch(shipment -> shipment.getDeliveredAt() != null);
    }

    /**
     * Nothing is left to deliver before the order can settle: every shipment is delivered. An order without shipments
     * waits in every status before Delivered: every order is created with a shipment waiting to go out, and an empty
     * list before delivery means the only shipment was removed (or lost), never that the goods reached the customer; settling it on payment and invoice alone would complete an order that never
     * shipped. Only a legacy order already Delivered (or Completed) without shipments settles.
     */
    @DynamoDBIgnore
    public boolean hasNothingLeftToDeliver() {
        return shipments.isEmpty() ? hasOneOfStatuses(OrderStatus.Delivered, OrderStatus.Completed) : isDelivered();
    }

    @DynamoDBIgnore
    public boolean isSettled(boolean warehouseDocumentsRequired) {
        return hasNothingLeftToDeliver() && isFullyPaid() && isInvoiced() && !isAwaitingDocumentsGeneration(warehouseDocumentsRequired) && !isAwaitingReview();
    }

    @DynamoDBIgnore
    public void reopen() {
        if (hasOneOfStatuses(OrderStatus.Completed)) {
            this.status = OrderStatus.Delivered;
        }
    }

    @DynamoDBIgnore
    public boolean isAwaitingReview() {
        return review != null && review.getStatus() == OrderReviewStatus.ToBeCollected;
    }

    @DynamoDBIgnore
    public boolean isAwaitingDocumentsGeneration(boolean warehouseDocumentsRequired) {
        return warehouseDocumentsRequired && !getDocumentByType(DocumentType.GoodsIssue).isPresent();
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
        return !getShipments().isEmpty() && getShipments().stream().allMatch(s -> s.hasCollectionData() || s.hasShippingData());
    }

    /** At least one shipment has gone out (a shipped or delivery date), or waits for collection. */
    @DynamoDBIgnore
    public boolean hasShippedShipment() {
        return getShipments().stream().anyMatch(Shipment::hasGoneOut);
    }

    /**
     * An operator's correction left a Shipping order with nothing shipped (the only shipment removed, a shipped date
     * cleared): the order goes back to Realization and waits for the next shipment, which moves it to Shipping again
     * (OrderLifecycle). Returns whether it went back. Kept out of OrderLifecycle on purpose: the lifecycle never moves
     * an order back by itself, only this operator action does.
     */
    public boolean returnToRealizationWhenNothingShipped() {
        if (status != OrderStatus.Shipping || hasShippedShipment()) {
            return false;
        }
        status = OrderStatus.Realization;
        return true;
    }

    /**
     * For a personal collection "Shipping" means ready for collection: every collection shipment without its moment of
     * readiness gets now. Called when the operator moves the order to Shipping by hand; the lifecycle's own move to
     * Shipping needs every shipment shipped or ready already.
     */
    public void markCollectionsReady(LocalDateTime now) {
        getShipments().stream()
                .filter(shipment -> shipment.getType() == ShipmentType.PersonalCollection)
                .filter(shipment -> shipment.getShippedAt() == null)
                .forEach(shipment -> shipment.setShippedAt(now));
    }

    @DynamoDBIgnore
    public boolean isB2B() {
        return billingDetails != null && billingDetails.hasTaxId();
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

        DocumentType type = isB2B()
                ? DocumentType.InvoiceVat
                : DocumentType.Receipt;

        return Optional.of(type);
    }

    @DynamoDBIgnore
    public List<DocumentType> getIssuableDocumentTypes() {
        if (!isB2B() || isInvoiced()) {
            return List.of();
        }

        if (getDocumentByType(DocumentType.InvoiceAdvance).isPresent()) {
            return List.of(DocumentType.InvoiceFinal);
        }

        boolean hasOrder = getDocumentByType(DocumentType.Order).isPresent();

        List<DocumentType> types = new ArrayList<>();
        if (!hasOrder) {
            types.add(DocumentType.Order);
        }
        types.add(DocumentType.InvoiceVat);
        if (hasOrder && getPaidAmount() > 0) {
            types.add(DocumentType.InvoiceAdvance);
        }
        return types;
    }

    @DynamoDBIgnore
    public DocumentType getReceiptType() {
        Optional<Document> op = getClosingDocument();
        if (op.isPresent()) {
            return op.get().getType();
        }

        return getNextDocumentToIssue().orElseGet(() -> isB2B() ? DocumentType.InvoiceVat : DocumentType.Receipt);
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

    public boolean removeDocument(DocumentType type, String number) {
        if (type == null || !type.isInvoiceOrReceipt()) {
            return false;
        }
        return documents.removeIf(d -> d.getType() == type && Objects.equals(d.getNumber(), number));
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
                .mapToDouble(Payment::getAppliedAmount)
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
        if (orderItem.isService()) {
            this.orderRealizationDays = Math.max(orderRealizationDays, estimatedDeliveryDays);
        }
    }

    @DynamoDBIgnore
    public boolean isEligibleForRMACreation() {
        return hasOneOfStatuses(OrderStatus.Delivered, OrderStatus.Completed);
    }

    /** What stands between the order and cancelling it; empty when it can be cancelled. */
    public enum CancelBlocker { NOT_DELIVERED, PRODUCTS_NOT_RETURNED, PAYMENTS_NOT_REFUNDED }

    @DynamoDBIgnore
    public boolean canBeCancelled(List<OrderItem> orderItems) {
        return cancelBlockers(orderItems).isEmpty();
    }

    /** The conditions of {@link #canBeCancelled} this order does not meet yet, so a reason can name exactly those. */
    @DynamoDBIgnore
    public Set<CancelBlocker> cancelBlockers(List<OrderItem> orderItems) {
        Set<CancelBlocker> blockers = EnumSet.noneOf(CancelBlocker.class);
        if (!hasOneOfStatuses(OrderStatus.Delivered, OrderStatus.Completed)) {
            blockers.add(CancelBlocker.NOT_DELIVERED);
        }
        if (getPaidAmount() != 0) {
            blockers.add(CancelBlocker.PAYMENTS_NOT_REFUNDED);
        }
        if (!orderItems.stream().filter(OrderItem::isProduct).allMatch(OrderItem::isReturned)) {
            blockers.add(CancelBlocker.PRODUCTS_NOT_RETURNED);
        }
        return blockers;
    }

    @DynamoDBIgnore
    public void cancel(List<OrderItem> orderItems) {
        orderItems.stream()
                .filter(i -> i.isService())
                .forEach(OrderItem::markAsReturned);

        this.totalPrice = orderItems.stream()
                .mapToDouble(OrderItem::getTotalPrice)
                .sum();

        this.status = OrderStatus.Cancelled;

        if (review != null && review.getStatus() == OrderReviewStatus.ToBeCollected) {
            review.setStatus(OrderReviewStatus.NotApplicable);
        }
    }

    @DynamoDBIgnore
    public boolean canBeSplit() {
        return hasOneOfStatuses(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly, OrderStatus.Assembled)
                && getPaidAmount() == 0
                && !getDocumentByType(DocumentType.GoodsIssue).isPresent()
                && !isInvoiced();
    }

    @DynamoDBIgnore
    public boolean canChangeFulfilmentType(List<OrderItem> orderItems) {
        return orderItems.stream()
                .filter(OrderItem::isProduct)
                .allMatch(OrderItem::isNew);
    }

    @DynamoDBIgnore
    public Order createSplit() {
        Order copy = new Order(this.storeId);
        copy.setBillingDetails(this.billingDetails == null ? null : this.billingDetails.copy());
        copy.setShippingDetails(this.shippingDetails == null ? null : this.shippingDetails.copy());
        copy.setFulfilmentType(this.fulfilmentType);
        copy.setSource(this.source);
        copy.setAffiliateId(this.affiliateId);
        copy.setOrderRealizationDays(this.orderRealizationDays);
        copy.setEmailNotificationsEnabled(this.emailNotificationsEnabled);
        copy.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));

        Shipment shipment = new Shipment();
        firstShipment().ifPresent(previous -> {
            shipment.setType(previous.getType());
            shipment.setCollectionPointCode(previous.getCollectionPointCode());
        });
        copy.addShipment(shipment);

        Payment payment = new Payment();
        payment.setSource(payments.isEmpty() ? PaymentSource.BankTransfer : payments.get(0).getSource());
        copy.addPayment(payment);

        copy.setSplitFromOrderId(this.orderId);
        copy.setExternalSupplierId(this.externalSupplierId);
        return copy;
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
        return payments.isEmpty() ? null : payments.get(payments.size() - 1);
    }

    @DynamoDBIgnore
    public Payment getPendingPayment() {
        return payments.stream().filter(Payment::isUnsettled).findFirst().orElse(null);
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

    @DynamoDBIgnore
    public String createClientOrderUrl(String domain) {
        return domain + "/store/" + this.storeId + "/client/order/" + this.orderId;
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
        this.email = billingDetails != null ? billingDetails.getEmail() : null; // projection
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
        // normalize at the boundary: every internal read (getPaidAmount, getPendingPayment, ...) assumes a list, never null
        this.payments = payments != null ? payments : new LinkedList<>();
    }

    public List<Shipment> getShipments() {
        return shipments;
    }

    public void setShipments(List<Shipment> shipments) {
        this.shipments = shipments;
    }

    public void replaceShipments(List<Shipment> replacements) {
        firstShipment().ifPresent(previous ->
                replacements.forEach(replacement -> replacement.inheritDeliveryChoiceFrom(previous)));
        this.shipments = replacements;
    }

    /**
     * The order's only shipment when it holds nothing but the customer's choice of delivery (every order is created
     * with one; a date cleared in the edit leaves one too): a new shipment fills it instead of standing next to it,
     * where the placeholder, never sent nor delivered, would hold the order back from Shipping and Delivered.
     */
    @DynamoDBIgnore
    public Optional<Shipment> onlyPlaceholder() {
        return shipments.size() == 1 && shipments.get(0).isPlaceholder() ? Optional.of(shipments.get(0)) : Optional.empty();
    }

    @DynamoDBIgnore
    public Optional<Shipment> firstShipment() {
        return shipments.isEmpty() ? Optional.empty() : Optional.of(shipments.get(0));
    }

    /** True when at least one shipment was registered for carrier tracking (drives the tracking column on the order page). */
    @DynamoDBIgnore
    public boolean hasTrackedShipments() {
        return shipments.stream().anyMatch(Shipment::hasTrackingSubscription);
    }

    /**
     * Something is left to book a courier for: a shipment without its shipping data, or no shipment at all (the only
     * one removed; the courier booking then creates it). An order whose every shipment is sent has nothing to book. A
     * shipment with a courier order (externalId) is booked whatever its dates say: booking again would pay for a second
     * label next to the first one, which the booking's replaced list would no longer let anyone cancel. A shipment being created is not "to book";
     * one whose creation failed is, even with a courier order id.
     */
    @DynamoDBIgnore
    public boolean hasShipmentToBook() {
        return shipments.isEmpty() || shipments.stream()
                .anyMatch(shipment -> shipment.creationFailed()
                        || (!shipment.isCreating() && shipment.getExternalId() == null && !shipment.hasShippingData()));
    }

    /** A shipment is still being created at the provider: no second booking may start meanwhile. */
    @DynamoDBIgnore
    public boolean hasShipmentBeingCreated() {
        return shipments.stream().anyMatch(Shipment::isCreating);
    }

    @DynamoDBIgnore
    public boolean hasShippingLabel() {
        return shipments.stream().anyMatch(Shipment::hasLabel);
    }

    /**
     * The shipment whose courier order "Cancel shipment" cancels (ShipmentCancelService): the first one booked with
     * a courier (externalId) whose parcel is not delivered yet. Found by the courier order, never by the shipped date,
     * which the operator may have changed.
     */
    @DynamoDBIgnore
    public Optional<Shipment> courierShipmentToCancel() {
        return shipments.stream().filter(s -> s.getExternalId() != null && s.getDeliveredAt() == null && s.getCreation() == null)
                .findFirst();
    }

    /** The first shipment handed to a carrier (its carrier, tracking number and shipped date), if any. */
    @DynamoDBIgnore
    public Optional<Shipment> firstShipmentWithShippingData() {
        return shipments.stream().filter(Shipment::hasShippingData).findFirst();
    }

    /**
     * Once a label exists the parcel address is fixed; before that the operator may still correct it, whatever the
     * status of an open order (a personal pickup already in Shipping can still get a corrected address). A tracking
     * number typed by hand counts as a label, as for the customer.
     */
    @DynamoDBIgnore
    public boolean canOperatorChangeShippingAddress() {
        return !isClosed() && !hasShippingLabel();
    }

    @DynamoDBIgnore
    public boolean isCourierDelivery() {
        return !isPersonalCollection() && firstShipment()
                .map(shipment -> shipment.getType() == ShipmentType.Courier && !shipment.isDeliveredToCollectionPoint())
                .orElse(true);
    }

    @DynamoDBIgnore
    public boolean hasBillingEmail() {
        return billingDetails != null && isNotBlank(billingDetails.getEmail());
    }

    @DynamoDBIgnore
    public boolean canChangeShippingAddress() {
        return shippingDetails != null
                && isCourierDelivery()
                && hasOneOfStatuses(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly, OrderStatus.Assembled, OrderStatus.Realization)
                && !hasShippingLabel()
                && !isMarketplaceOrder()
                && hasBillingEmail();
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

    public String getExternalSupplierId() {
        return externalSupplierId;
    }

    public void setExternalSupplierId(String externalSupplierId) {
        this.externalSupplierId = externalSupplierId;
    }

    @DynamoDBIgnore
    public boolean isBoundToExternalSupplier() {
        return externalSupplierId != null && !externalSupplierId.isBlank();
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

    /**
     * @param deliveryDate      when this leg's goods land, or null for a confirmation that carries no date
     * @param shippedBySupplier every leg of this order travels straight from a supplier to the customer,
     *                          so there is no in-house handling to add. Only a caller that asked the whole
     *                          order can tell: neither a single delivery nor the order's fulfilment type
     *                          is enough, because a direct-to-consumer order can still have goods arriving
     *                          at our warehouse to be forwarded by hand.
     */
    @DynamoDBIgnore
    public LocalDate updateEstimatedAssemblyAt(LocalDate deliveryDate, boolean shippedBySupplier) {
        // The assembly date only moves forward: the order is ready once its last leg has landed.
        if (deliveryDate != null && (estimatedAssemblyAt == null || deliveryDate.isAfter(estimatedAssemblyAt))) {
            estimatedAssemblyAt = deliveryDate;
        }
        if (estimatedAssemblyAt == null) {
            return null;
        }
        // The shipping date is derived again on every call, including one that moves no date at all: a leg
        // confirmed for an earlier date, or with no date, can still be the one that puts a warehouse stop on
        // the order, and nothing else would ever repair the handling time it owes.
        estimatedShippingAt = shippedBySupplier
                ? estimatedAssemblyAt
                : addWeekdayDays(estimatedAssemblyAt, orderRealizationDays);

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
    public LocalDate getShippingDueAt() {
        return preferredShippingAt != null ? preferredShippingAt : estimatedShippingAt;
    }

    @DynamoDBIgnore
    public boolean isCourierBookingEarlierThanPreferred(LocalDate today) {
        return preferredShippingAt != null && today.isBefore(preferredShippingAt);
    }

    @DynamoDBIgnore
    public boolean canClientSetPreferredShippingAt() {
        return estimatedShippingAt != null
                && hasOneOfStatuses(OrderStatus.Assembly, OrderStatus.Assembled, OrderStatus.Realization);
    }

    @DynamoDBIgnore
    public LocalDate getPreferredShippingWindowEnd() {
        return estimatedShippingAt == null ? null : estimatedShippingAt.plusDays(PREFERRED_SHIPPING_WINDOW_DAYS);
    }

    @DynamoDBIgnore
    public boolean isWithinPreferredShippingWindow(LocalDate date, Set<DayOfWeek> allowedDays) {
        return estimatedShippingAt != null
                && !date.isBefore(estimatedShippingAt)
                && !date.isAfter(getPreferredShippingWindowEnd())
                && allowedDays.contains(date.getDayOfWeek());
    }

    @DynamoDBIgnore
    public ShipmentType getShipmentType() {
        return firstShipment().map(Shipment::getType).orElse(ShipmentType.Courier);
    }

    @DynamoDBIgnore
    public boolean hasShippingDetails() {
        return shippingDetails != null && shippingDetails.isProperlyFilled();
    }

    @DynamoDBIgnore
    public boolean isPersonalCollection() {
        return shipments.stream().anyMatch(shipment -> shipment.getType() == ShipmentType.PersonalCollection);
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public static class Builder {

        private final Shipment shipment;

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
            shipment = new Shipment();
            shipment.setType(ShipmentType.Courier);
            order.addShipment(shipment);

            // payment
            order.addPayment(new Payment(PaymentSource.BankTransfer));
        }

        public Builder(Store store, Basket basket) {
            this(
                    store.getStoreId(), basket.getAffiliateId(), DeliveryDays.calculate(store, basket).getMaxRealizationDays(), true, basket.getComment(),
                    OrderReviewStatus.ToBeCollected, basket.getFulfilmentType(), basket.getTotalPrice(),
                    basket.getBillingDetails(), basket.getShippingDetails(), basket.getSource()
            );
            order.setGclid(basket.getGclid());
        }

        public Builder(Order original) {
            this(
                    original.getStoreId(), null, 1, false, null,
                    OrderReviewStatus.NotApplicable, FulfilmentType.WarehouseFulfilment, 0,
                    original.getBillingDetails(), original.getShippingDetails(), new OrderSource("RMA", OrderSourceType.Other)
            );
        }

        public static Builder forPos(Store store, BillingDetails billingDetails, ShippingDetails shippingDetails, String operatorName) {
            Builder builder = new Builder(
                    store.getStoreId(), null, 0, false, null,
                    OrderReviewStatus.NotApplicable, FulfilmentType.WarehouseFulfilment, 0,
                    billingDetails, shippingDetails, new OrderSource(operatorName, OrderSourceType.PointOfSale)
            );
            builder.withShipmentType(ShipmentType.PersonalCollection);
            builder.withPaymentSource(PaymentSource.Cash);
            return builder;
        }

        public Builder withShipmentType(ShipmentType shipmentType) {
            shipment.setType(shipmentType);
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

        public Builder withExternalSupplierId(String externalSupplierId) {
            order.setExternalSupplierId(externalSupplierId);
            return this;
        }

        public Builder withDeliveryCarrier(String deliveryCarrier) {
            shipment.setCarrier(deliveryCarrier);
            return this;
        }

        public Builder withCollectionPointCode(String collectionPointCode) {
            shipment.setCollectionPointCode(collectionPointCode);
            return this;
        }

        public Builder withPreferredShippingAt(LocalDate preferredShippingAt) {
            if (preferredShippingAt != null) {
                order.setPreferredShippingAt(preferredShippingAt);
            }
            return this;
        }

        public Order build() {
            return order;
        }
    }

}