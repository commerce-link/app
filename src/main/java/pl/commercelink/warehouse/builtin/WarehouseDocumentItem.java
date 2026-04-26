package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.UUID;

@DynamoDBTable(tableName = "WarehouseDocumentItems")
public class WarehouseDocumentItem {

    @DynamoDBHashKey(attributeName = "documentId")
    private String documentId;

    @DynamoDBRangeKey(attributeName = "itemId")
    private String itemId;

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "DeliveryIdIndex", attributeName = "deliveryId")
    private String deliveryId;

    @DynamoDBAttribute(attributeName = "ean")
    private String ean;

    @DynamoDBAttribute(attributeName = "mfn")
    private String mfn;

    @DynamoDBAttribute(attributeName = "name")
    private String name;

    @DynamoDBAttribute(attributeName = "qty")
    private int qty;

    @DynamoDBAttribute(attributeName = "unitPrice")
    private double unitPrice;

    @DynamoDBAttribute(attributeName = "documentType")
    @DynamoDBTypeConvertedEnum
    private DocumentType documentType;

    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;

    public WarehouseDocumentItem() {
    }

    public WarehouseDocumentItem(String documentId, DocumentType documentType, LocalDateTime createdAt, String deliveryId, String ean, String mfn, String name, int qty, double unitPrice) {
        this.itemId = UUID.randomUUID().toString();
        this.documentId = documentId;
        this.documentType = documentType;
        this.createdAt = createdAt;
        this.deliveryId = deliveryId;
        this.ean = ean;
        this.mfn = mfn;
        this.name = name;
        this.qty = qty;
        this.unitPrice = unitPrice;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public String getMfn() {
        return mfn;
    }

    public void setMfn(String mfn) {
        this.mfn = mfn;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
