package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.inventory.deliveries.Delivered;
import pl.commercelink.orders.fulfilment.FulfilmentSource;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ReservationRemovalItem;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.*;
import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@DynamoDBDocument
public abstract class Item implements Delivered {

    // general information
    @DynamoDBAttribute(attributeName = "category")
    @DynamoDBTypeConvertedEnum
    private ProductCategory category = ProductCategory.Other;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "qty")
    private int qty;
    @DynamoDBAttribute(attributeName = "comment")
    private String comment;

    // fulfilment
    @DynamoDBAttribute(attributeName = "ean")
    private String ean;
    @DynamoDBAttribute(attributeName = "mfn")
    private String manufacturerCode;
    @DynamoDBAttribute(attributeName = "unitCost")
    private double cost;
    @DynamoDBAttribute(attributeName = "tax")
    private double tax = DEFAULT_VAT_RATE;
    @DynamoDBAttribute(attributeName = "deliveryId")
    private String deliveryId;
    @DynamoDBAttribute(attributeName = "serialNo")
    private String serialNo;
    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private FulfilmentStatus  status = FulfilmentStatus.New;
    @DynamoDBAttribute(attributeName = "destroyedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate destroyedAt;

    public Item() {
        // Required for DynamoDB
    }

    public Item(ProductCategory category, String name, int qty, String comment) {
        this.category = category;
        this.name = name;
        this.qty = qty;
        this.comment = comment;
    }

    public void addFulfilment(FulfilmentSource source) {
        this.deliveryId = source.getProvider();
        this.ean = source.getEan();
        this.manufacturerCode = source.getMfn();
        this.cost = source.getPriceNet();
        this.status = FulfilmentStatus.New;
    }

    protected void addFulfilment(String ean, String mfn, double cost, double tax, String deliveryId, String serialNo, FulfilmentStatus status) {
        this.ean = ean;
        this.manufacturerCode = mfn;
        this.cost = cost;
        this.tax = tax;
        this.deliveryId = deliveryId;
        this.serialNo = serialNo;
        this.status = status;
    }

    @DynamoDBIgnore
    public void removeFulfilment() {
        this.ean = null;
        this.manufacturerCode = null;
        this.cost = 0;
        this.deliveryId = null;
        this.serialNo = null;
        this.status = FulfilmentStatus.New;
    }

    public void removeSerialNumbers(String sns) {
        String serialNo = getSerialNo();

        if (StringUtils.isBlank(sns)) {
            return;
        }

        if (StringUtils.isBlank(serialNo)) {
            return;
        }

        List<String> otherSerialNumbers = Arrays.asList(sns.split(","));
        String newSerialNumbers = Arrays.stream(serialNo.split(","))
                .map(String::trim)
                .filter(sn -> !otherSerialNumbers.contains(sn))
                .collect(Collectors.joining(","));

        setSerialNo(newSerialNumbers);
    }

    public List<String> getInvalidSerialNumbers(String sns) {
        String serialNo = getSerialNo();

        if (StringUtils.isBlank(sns)) {
            return new LinkedList<>();
        }

        List<String> snsToCheck = Arrays.stream(sns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (StringUtils.isBlank(serialNo)) {
            return snsToCheck;
        }

        List<String> snsInUse = Arrays.stream(serialNo.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return snsToCheck.stream().filter(sn -> !snsInUse.contains(sn)).collect(Collectors.toList());
    }

    @DynamoDBIgnore
    public boolean isNew() {
        return status == FulfilmentStatus.New;
    }

    @DynamoDBIgnore
    public boolean isReadyForAllocation() {
        return hasOneOfTheStatuses(FulfilmentStatus.New) && hasAllocationDetails();
    }

    @DynamoDBIgnore
    public boolean isInAllocation() {
        return hasOneOfTheStatuses(FulfilmentStatus.Allocation) && hasAllocationDetails();
    }

    @DynamoDBIgnore
    public boolean isInAllocationOrOrdered() {
        return hasOneOfTheStatuses(FulfilmentStatus.Allocation, FulfilmentStatus.Ordered) && hasAllocationDetails();
    }

    @DynamoDBIgnore
    public boolean isWaitingForCollection() {
        return hasOneOfTheStatuses(FulfilmentStatus.Ordered, FulfilmentStatus.InExternalService) && hasAllocationDetails();
    }

    @DynamoDBIgnore
    public boolean isReturned() {
        return hasOneOfTheStatuses(FulfilmentStatus.Returned);
    }

    @DynamoDBIgnore
    public boolean isReplacedOrReturned() {
        return hasOneOfTheStatuses(FulfilmentStatus.Replaced, FulfilmentStatus.Returned);
    }

    @DynamoDBIgnore
    public boolean isAllocated() {
        if (hasGroup(ProductGroup.Services) && this.status == FulfilmentStatus.Delivered) {
            return true;
        }
        return hasAllocationDetails() && hasOneOfTheStatuses(FulfilmentStatus.Ordered, FulfilmentStatus.Delivered);
    }

    @DynamoDBIgnore
    public boolean isOrdered() {
        if (hasGroup(ProductGroup.Services) && this.status == FulfilmentStatus.Delivered) {
            return true;
        }

        return hasAllocationDetails() && hasOneOfTheStatuses(FulfilmentStatus.Ordered, FulfilmentStatus.Delivered);
    }

    @DynamoDBIgnore
    public boolean isDelivered() {
        return status == FulfilmentStatus.Delivered;
    }

    @DynamoDBIgnore
    public boolean hasSameFulfilmentAs(GoodsReceiptItem goodsReceiptItem) {
        return this.deliveryId.equalsIgnoreCase(goodsReceiptItem.getDeliveryId())
                && areEansEq(this.ean, goodsReceiptItem.getEan())
                && areMfnsEq(this.manufacturerCode, goodsReceiptItem.getMfn());
    }

    @DynamoDBIgnore
    public boolean hasSameFulfilmentAs(ReservationRemovalItem removalItem) {
        return this.deliveryId.equalsIgnoreCase(removalItem.getDeliveryId())
                && areEansEq(this.ean, removalItem.getEan())
                && areMfnsEq(this.manufacturerCode, removalItem.getMfn());
    }

    @DynamoDBIgnore
    public boolean hasGroup(ProductGroup group) {
        return category.getProductGroup() == group;
    }

    @DynamoDBIgnore
    public boolean hasCategory(ProductCategory category) {
        return this.category == category;
    }

    @DynamoDBIgnore
    public boolean hasAllocationDetails() {
        return isNotBlank(ean) && isNotBlank(manufacturerCode) && isNotBlank(this.deliveryId);
    }

    @DynamoDBIgnore
    public boolean hasOneOfTheStatuses(FulfilmentStatus... statuses) {
        return Arrays.stream(statuses).anyMatch(s -> s == this.status);
    }

    @DynamoDBIgnore
    public void markAsOrdered(String deliveryId, double cost) {
        this.cost = cost;
        this.deliveryId = deliveryId;
        this.status = FulfilmentStatus.Ordered;
    }

    @DynamoDBIgnore
    public void markAsReceived() {
        this.setStatus(FulfilmentStatus.Delivered);
    }

    @DynamoDBIgnore
    public void markAsAvailable() {
        this.setStatus(FulfilmentStatus.Delivered);
    }

    @DynamoDBIgnore
    public void markAsInAllocation() {
        this.setStatus(FulfilmentStatus.Allocation);
    }

    @DynamoDBIgnore
    public double getTotalCost() {
        return cost * qty;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
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

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = unifyEan(ean);
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public void setManufacturerCode(String manufacturerCode) {
        this.manufacturerCode = unifyMfn(manufacturerCode);
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getTax() {
        return tax;
    }

    public void setTax(double tax) {
        this.tax = tax;
    }

    @DynamoDBIgnore
    public Price unitCost() {
        return Price.fromNet(cost, tax);
    }

    @DynamoDBIgnore
    public Price totalUnitCost() {
        return unitCost().times(qty);
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    @DynamoDBIgnore
    public String getShortenedDeliveryId() {
        return ConversionUtil.getShortenedId(deliveryId);
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public FulfilmentStatus getStatus() {
        return status;
    }

    public void setStatus(FulfilmentStatus status) {
        this.status = status;
    }

    public LocalDate getDestroyedAt() {
        return destroyedAt;
    }

    public void setDestroyedAt(LocalDate destroyedAt) {
        this.destroyedAt = destroyedAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
