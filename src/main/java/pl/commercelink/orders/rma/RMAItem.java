package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.inventory.deliveries.Delivered;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@DynamoDBTable(tableName = "RMAItems")
public class RMAItem implements Delivered {

    @DynamoDBHashKey(attributeName = "rmaId")
    private String rmaId;
    @DynamoDBRangeKey(attributeName = "rmaItemId")
    private String rmaItemId;

    @DynamoDBAttribute(attributeName    = "itemId")
    private String itemId;
    @DynamoDBAttribute(attributeName = "desiredResolution")
    @DynamoDBTypeConvertedEnum
    private RMAResolutionType desiredResolution;
    @DynamoDBAttribute(attributeName = "actualResolution")
    @DynamoDBTypeConvertedEnum
    private RMAResolutionType actualResolution;
    @DynamoDBAttribute(attributeName = "reason")
    private String reason;
    @DynamoDBAttribute(attributeName = "qty")
    private int qty = 1;
    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private RMAItemStatus status = RMAItemStatus.New;

    // Populated from OrderItem
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "deliveryId")
    private String deliveryId;
    @DynamoDBAttribute(attributeName = "ean")
    private String ean;
    @DynamoDBAttribute(attributeName = "mfn")
    private String mfn;
    @DynamoDBAttribute(attributeName = "price")
    private double price;
    @DynamoDBAttribute(attributeName = "cost")
    private double cost;
    @DynamoDBAttribute(attributeName = "tax")
    private double tax = DEFAULT_VAT_RATE;
    @DynamoDBAttribute(attributeName = "serialNo")
    private String serialNo;
    @DynamoDBAttribute(attributeName = "comment")
    private String comment;

    @DynamoDBIgnore
    private boolean selected;

    public RMAItem() {
    }

    public RMAItem(String rmaId, OrderItem orderItem, RMAItem source) {
        this.rmaId = rmaId;
        this.rmaItemId = UUID.randomUUID().toString();

        this.itemId = source.getItemId();
        this.desiredResolution = source.getDesiredResolution();
        this.reason = source.getReason();
        this.qty = source.getQty();
        this.status = RMAItemStatus.New;

        this.name = orderItem.getName();
        this.deliveryId = orderItem.getDeliveryId();
        this.ean = orderItem.getEan();
        this.mfn = orderItem.getManufacturerCode();
        this.price = orderItem.getPrice();
        this.cost = orderItem.getCost();
        this.tax = orderItem.getTax();
        this.serialNo = orderItem.getSerialNo();
    }

    public RMAItem(String rmaId, RMAItem source, int qty) {
        this.rmaId = rmaId;
        this.rmaItemId = UUID.randomUUID().toString();

        this.itemId = source.getItemId();
        this.desiredResolution = source.getDesiredResolution();
        this.actualResolution = source.getActualResolution();
        this.reason = source.getReason();
        this.qty = qty;
        this.status = source.getStatus();

        this.name = source.getName();
        this.deliveryId = source.deliveryId;
        this.ean = source.getEan();
        this.mfn = source.getMfn();
        this.price = source.getPrice();
        this.cost = source.getCost();
        this.tax = source.getTax();
        this.serialNo = null;
        this.comment = source.getComment();
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(itemId) && desiredResolution != null && qty > 0;
    }

    public String getRmaId() {
        return rmaId;
    }

    public void setRmaId(String rmaId) {
        this.rmaId = rmaId;
    }

    public String getRmaItemId() {
        return rmaItemId;
    }

    public void setRmaItemId(String rmaItemId) {
        this.rmaItemId = rmaItemId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public RMAResolutionType getDesiredResolution() {
        return desiredResolution;
    }

    public void setDesiredResolution(RMAResolutionType desiredResolution) {
        this.desiredResolution = desiredResolution;
    }

    public RMAResolutionType getActualResolution() {
        return actualResolution;
    }

    public void setActualResolution(RMAResolutionType actualResolution) {
        this.actualResolution = actualResolution;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public RMAItemStatus getStatus() {
        return status;
    }

    public boolean hasOneOfTheStatuses(RMAItemStatus... statuses) {
        return Arrays.stream(statuses).anyMatch(s -> s == this.status);
    }

    public void setStatus(RMAItemStatus status) {
        this.status = status;
    }

    @DynamoDBIgnore
    public double getTotalPrice() {
        return this.price * this.qty;
    }

    @DynamoDBIgnore
    public void markAsReceived() {
        this.setStatus(RMAItemStatus.Received);
    }

    @DynamoDBIgnore
    public void markAsSendToRepair() {
        this.setStatus(RMAItemStatus.SentForRepair);
        this.setActualResolution(RMAResolutionType.Repair);
    }

    @DynamoDBIgnore
    public void markAsReturnedToClient() {
        this.setStatus(RMAItemStatus.ReturnedToClient);
    }

    @DynamoDBIgnore
    public void markAsMovedToWarehouseAndReturned() {
        this.setStatus(RMAItemStatus.MovedToWarehouse);
        this.setActualResolution(RMAResolutionType.Return);
    }

    @DynamoDBIgnore
    public void markAsMovedToWarehouseAndReplaced() {
        this.setStatus(RMAItemStatus.MovedToWarehouse);
        this.setActualResolution(RMAResolutionType.Replacement);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    @Override
    public String getDeliveryId() {
        return deliveryId;
    }

    @DynamoDBIgnore
    public String getShortenedDeliveryId() {
        return ConversionUtil.getShortenedId(deliveryId);
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    @DynamoDBIgnore
    public Price unitCost() {
        return Price.fromNet(cost, tax);
    }

    public double getTax() {
        return tax;
    }

    public void setTax(double tax) {
        this.tax = tax;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @DynamoDBIgnore
    public RMAItem copyWithNewQty(int qty) {
        return new RMAItem(this.rmaId, this, qty);
    }

    @DynamoDBIgnore
    public static double computeTotalPrice(List<RMAItem> rmaItems) {
        return rmaItems.stream()
                .mapToDouble(RMAItem::getTotalPrice)
                .sum();
    }
}
