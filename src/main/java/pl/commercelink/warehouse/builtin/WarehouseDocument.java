package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.UUID;

@DynamoDBTable(tableName = "WarehouseDocuments")
public class WarehouseDocument {

    @DynamoDBHashKey(attributeName = "storeId")
    @DynamoDBIndexHashKey(globalSecondaryIndexNames = {"DeliveryIdIndex", "CreatedAtIndex", "OrderIdIndex", "RMAIdIndex"}, attributeName = "storeId")
    private String storeId;

    @DynamoDBRangeKey(attributeName = "documentId")
    private String documentId;

    @DynamoDBAttribute(attributeName = "documentNo")
    private String documentNo;

    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private DocumentType type;

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "OrderIdIndex", attributeName = "orderId")
    private String orderId;

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "RMAIdIndex", attributeName = "rmaId")
    private String rmaId;

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "DeliveryIdIndex", attributeName = "deliveryId")
    private String deliveryId;

    @DynamoDBAttribute(attributeName = "warehouseId")
    private String warehouseId;

    @DynamoDBAttribute(attributeName = "issuer")
    private IssuerDetails issuer;

    @DynamoDBAttribute(attributeName = "counterparty")
    private CounterpartyDetails counterparty;

    @DynamoDBAttribute(attributeName = "deliveryAddress")
    private DeliveryAddress deliveryAddress;

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "CreatedAtIndex", attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;

    @DynamoDBAttribute(attributeName = "createdBy")
    private String createdBy;

    @DynamoDBAttribute(attributeName = "reason")
    @DynamoDBTypeConvertedEnum
    private DocumentReason reason;

    @DynamoDBAttribute(attributeName = "note")
    private String note;

    public WarehouseDocument() {
    }

    public WarehouseDocument(String storeId, String documentNo, DocumentType type) {
        this.documentId = UUID.randomUUID().toString();
        this.storeId = storeId;
        this.documentNo = documentNo;
        this.type = type;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public String getOrderId() {
        return orderId;
    }

    @DynamoDBIgnore
    public String getShortenedOrderId() {
        return ConversionUtil.getShortenedId(orderId);
    }

    @DynamoDBIgnore
    public String getShortenedDeliveryId() {
        return ConversionUtil.getShortenedId(deliveryId);
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRmaId() {
        return rmaId;
    }

    public void setRmaId(String rmaId) {
        this.rmaId = rmaId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public IssuerDetails getIssuer() {
        return issuer;
    }

    public void setIssuer(IssuerDetails issuer) {
        this.issuer = issuer;
    }

    public CounterpartyDetails getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(CounterpartyDetails counterparty) {
        this.counterparty = counterparty;
    }

    public DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(DeliveryAddress deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public DocumentReason getReason() {
        return reason;
    }

    public void setReason(DocumentReason reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
