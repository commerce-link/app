package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@DynamoDBTable(tableName = "RMA")
public class RMA {

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
}
