package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@DynamoDBTable(tableName = "RMA")
public class RMA {

    public static final String EVENT_REFUND_REQUESTED = "RefundRequested";
    public static final String EVENT_REJECTION_SENT = "RejectionSent";
    public static final String EVENT_REFUNDED_BY_MARKETPLACE = "RefundedByMarketplace";
    public static final int MAX_REJECTION_REASON_LENGTH = 250;

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "rmaId")
    private String rmaId;
    @DynamoDBAttribute(attributeName = "orderId")
    private String orderId;
    @DynamoDBAttribute(attributeName = "email")
    private String email;
    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private RMAStatus status;
    @DynamoDBAttribute(attributeName = "shippingDetails")
    private ShippingDetails shippingDetails;
    @DynamoDBAttribute(attributeName = "shippingInsurance")
    private double shippingInsurance;
    @DynamoDBAttribute(attributeName = "shipments")
    private List<Shipment> shipments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "rejectionReason")
    private String rejectionReason;
    @DynamoDBAttribute(attributeName = "emailNotificationsEnabled")
    private boolean emailNotificationsEnabled;
    @DynamoDBAttribute(attributeName = "events")
    private List<Event> events = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAt;
    @DynamoDBAttribute(attributeName = "media")
    private List<String> media = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "marketplace")
    @Getter
    @Setter
    private String marketplace;
    @DynamoDBAttribute(attributeName = "externalReturnId")
    @Getter
    @Setter
    private String externalReturnId;
    @DynamoDBAttribute(attributeName = "externalReturnReference")
    @Getter
    @Setter
    private String externalReturnReference;
    @DynamoDBAttribute(attributeName = "externalReturnStatus")
    @DynamoDBTypeConvertedEnum
    @Getter
    @Setter
    private MarketplaceReturnStatus externalReturnStatus;
    @DynamoDBAttribute(attributeName = "marketplaceDecisions")
    @Getter
    @Setter
    private List<MarketplaceDecision> marketplaceDecisions = new LinkedList<>();
    @DynamoDBVersionAttribute
    private Long version;

    @DynamoDBIgnore
    private List<RMAItem> draftRmaItems = new LinkedList<>();

    public RMA() {
    }

    public RMA(String storeId) {
        this.storeId = storeId;
        this.rmaId = UUID.randomUUID().toString();
        this.status = RMAStatus.New;
        this.createdAt = LocalDateTime.now();
    }


    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getRmaId() {
        return rmaId;
    }

    public void setRmaId(String rmaId) {
        this.rmaId = rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public RMAStatus getStatus() {
        return status;
    }

    public void setStatus(RMAStatus status) {
        this.status = status;
    }

    @DynamoDBIgnore
    public void markAsApproved() {
        this.setStatus(RMAStatus.Approved);
    }

    @DynamoDBIgnore
    public void markAsRejected() {
        this.setStatus(RMAStatus.Rejected);
    }

    @DynamoDBIgnore
    public void markAsItemsReceived() {
        this.setStatus(RMAStatus.ItemsReceived);
    }

    @DynamoDBIgnore
    public void markAsWaitingForItems() {
        this.setStatus(RMAStatus.WaitingForItems);
    }

    @DynamoDBIgnore
    public void markAsProcessing() {
        this.setStatus(RMAStatus.Processing);
    }



    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(ShippingDetails shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    public List<Shipment> getShipments() {
        return shipments;
    }

    public void setShipments(List<Shipment> shipments) {
        this.shipments = shipments;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public List<Event> getEvents() {
        return events.stream()
                .sorted(Comparator.comparing(Event::getCreatedAt))
                .collect(Collectors.toList());
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public void addEvent(Event event) {
        this.events.add(event);
    }

    @DynamoDBIgnore
    public boolean hasEvent(Event other) {
        return events.stream().anyMatch(e -> e.isSameAs(other));
    }

    @DynamoDBIgnore
    public boolean hasActionEvent(String name) {
        return events.stream().anyMatch(e -> e.getType() == EventType.action && name.equals(e.getName()));
    }

    @DynamoDBIgnore
    public void addActionEvent(String name) {
        events.add(new Event(EventType.action, name, LocalDateTime.now()));
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getMedia() {
        return media;
    }

    public void setMedia(List<String> media) {
        this.media = media;
    }

    public List<RMAItem> getDraftRmaItems() {
        return draftRmaItems;
    }

    public void setDraftRmaItems(List<RMAItem> draftRmaItems) {
        this.draftRmaItems = draftRmaItems;
    }

    @DynamoDBIgnore
    public boolean hasOneOfTheStatuses(RMAStatus... statuses) {
        return Arrays.stream(statuses).anyMatch(s -> s == this.status);
    }

    @DynamoDBIgnore
    public String createClientRmaUrl(String domain) {
        return domain + "/store/" + this.storeId + "/client/rma/" + this.rmaId;
    }

    public double getShippingInsurance() {
        return shippingInsurance;
    }

    public void setShippingInsurance(double amount) {
        this.shippingInsurance = amount;
    }

    @DynamoDBIgnore
    public void increaseShippingInsurance(double amount) {
        this.shippingInsurance += amount;
    }

    @DynamoDBIgnore
    public void decreaseShippingInsurance(double amount) {
        this.shippingInsurance -= amount;
    }

    @DynamoDBIgnore
    public boolean isMarketplaceReturn() {
        return externalReturnId != null && !externalReturnId.isBlank();
    }

    /** A marketplace rejection is shown to the buyer and must carry a reason (1-250 chars); manual RMAs keep the old free-form rules. */
    @DynamoDBIgnore
    public boolean requiresRejectionReason(RMAStatus newStatus, String reason) {
        return isMarketplaceReturn() && turnsRejected(newStatus)
                && (reason == null || reason.isBlank() || reason.length() > MAX_REJECTION_REASON_LENGTH);
    }

    /** A refunded return must not also be rejected: the buyer would keep the money and get a rejection notice. */
    @DynamoDBIgnore
    public boolean blocksRejectionAfterRefund(RMAStatus newStatus) {
        return isMarketplaceReturn() && turnsRejected(newStatus) && hasActionEvent(EVENT_REFUND_REQUESTED);
    }

    private boolean turnsRejected(RMAStatus newStatus) {
        return newStatus == RMAStatus.Rejected && status != RMAStatus.Rejected;
    }

    @DynamoDBIgnore
    public void addMarketplaceDecision(MarketplaceDecision decision) {
        this.marketplaceDecisions.add(decision);
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
