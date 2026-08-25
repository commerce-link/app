package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Item;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ReservationRemovalItem;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@DynamoDBTable(tableName = "WarehouseItems")
public class WarehouseItem extends Item {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;

    @DynamoDBRangeKey(attributeName = "itemId")
    private String itemId;
    @DynamoDBVersionAttribute
    private Long version;

    @DynamoDBAttribute(attributeName = "purchaseClaimQty")
    private int purchaseClaimQty;

    @DynamoDBAttribute(attributeName = "unitSystemCost")
    private double unitSystemCost;

    // required for DynamoDB
    public WarehouseItem() {
    }

    public static WarehouseItem empty(String storeId) {
        return new WarehouseItem(storeId, "Other", Categories.UNCATEGORIZED, null, null, null, 0, 1);
    }

    public WarehouseItem(String storeId, String deliveryId, String category, String name, String ean, String mfn, double unitCost, int qty) {
        super(category, name, qty, null);

        this.storeId = storeId;
        this.itemId = UUID.randomUUID().toString();

        this.addFulfilment(
                ean,
                mfn,
                unitCost,
                DEFAULT_VAT_RATE,
                deliveryId,
                null,
                FulfilmentStatus.New
        );
    }

    @DynamoDBIgnore
    public void update(WarehouseItem other) {
        this.setCategory(other.getCategory());
        this.setName(other.getName());
        this.setComment(other.getComment());
        this.setSerialNo(other.getSerialNo());
        this.setUnitSystemCost(other.getUnitSystemCost());
        this.setCondition(other.getCondition());

        if (hasOneOfTheStatuses(FulfilmentStatus.New)) {
            setDeliveryId(other.getDeliveryId());
            setEan(other.getEan());
            setManufacturerCode(other.getManufacturerCode());
            setCost(other.getCost());
            setTax(other.getTax());
            setQty(other.getQty());
            setStatus(other.getStatus());
        }
    }

    @DynamoDBIgnore
    public boolean canBeDeleted() {
        return hasOneOfTheStatuses(FulfilmentStatus.New);
    }

    @DynamoDBIgnore
    public void markAsReserved() {
        this.setStatus(FulfilmentStatus.Reserved);
    }

    @DynamoDBIgnore
    public void returnToAllocationPool(String provider) {
        this.setStatus(FulfilmentStatus.Allocation);
        this.setDeliveryId(provider);
    }

    @DynamoDBIgnore
    public void markAsInRMA() {
        this.setStatus(FulfilmentStatus.InRMA);
    }

    @DynamoDBIgnore
    public void markAsInExternalService() {
        this.setStatus(FulfilmentStatus.InExternalService);
    }

    @DynamoDBIgnore
    public boolean isAvailable() {
        return Arrays.asList(FulfilmentStatus.Ordered, FulfilmentStatus.Delivered).contains(getStatus());
    }

    @DynamoDBIgnore
    public boolean isPhysicallyInStock() {
        return hasOneOfTheStatuses(FulfilmentStatus.Reserved, FulfilmentStatus.Delivered, FulfilmentStatus.InRMA);
    }

    @DynamoDBIgnore
    public void markAsDestroyed() {
        this.setStatus(FulfilmentStatus.Destroyed);
        this.setDestroyedAt(LocalDate.now());
    }

    @DynamoDBIgnore
    public boolean canJoinWith(WarehouseItem other) {
        if (other == null) return false;
        return Objects.equals(this.getEan(), other.getEan()) &&
               Objects.equals(this.getManufacturerCode(), other.getManufacturerCode()) &&
               Objects.equals(this.getCost(), other.getCost()) &&
               Objects.equals(this.getDeliveryId(), other.getDeliveryId()) &&
               this.getCondition() == other.getCondition();
    }

    @DynamoDBIgnore
    public void absorb(WarehouseItem other) {
        absorb(other.getQty(), other.getSerialNo(), other.getComment());
    }

    @DynamoDBIgnore
    public void absorb(ReservationRemovalItem other) {
        absorb(other.getQty(), other.getSerialNo(), other.getComment());
    }

    private void absorb(int qty, String serialNo, String comment) {
        setQty(getQty() + qty);
        appendSerialNumbers(serialNo);
        appendComment(comment);
    }

    @DynamoDBIgnore
    public double getEffectiveUnitSystemCost() {
        return unitSystemCost > 0 ? unitSystemCost : getCost();
    }

    @DynamoDBIgnore
    public boolean hasUnitSystemCost() {
        return unitSystemCost > 0;
    }

    @DynamoDBIgnore
    public Price systemCost() {
        return Price.fromNet(unitSystemCost, getTax());
    }

    @DynamoDBIgnore
    public WarehouseItem splitOff(int qtyToSplit) {
        if (qtyToSplit <= 0 || qtyToSplit >= getQty()) {
            throw new IllegalArgumentException("Split quantity must be between 1 and " + (getQty() - 1));
        }

        WarehouseItem splitItem = new WarehouseItem();
        splitItem.setStoreId(this.storeId);
        splitItem.setItemId(UUID.randomUUID().toString());
        splitItem.setCategory(getCategory());
        splitItem.setName(getName());
        splitItem.setComment(getComment());
        splitItem.setQty(qtyToSplit);
        splitItem.setEan(getEan());
        splitItem.setManufacturerCode(getManufacturerCode());
        splitItem.setCost(getCost());
        splitItem.setUnitSystemCost(getUnitSystemCost());
        splitItem.setTax(getTax());
        splitItem.setDeliveryId(getDeliveryId());
        splitItem.setStatus(getStatus());
        splitItem.setCondition(getCondition());

        this.setQty(getQty() - qtyToSplit);

        return splitItem;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public int getPurchaseClaimQty() {
        return purchaseClaimQty;
    }

    public void setPurchaseClaimQty(int purchaseClaimQty) {
        this.purchaseClaimQty = purchaseClaimQty;
    }

    public double getUnitSystemCost() {
        return unitSystemCost;
    }

    public void setUnitSystemCost(double unitSystemCost) {
        this.unitSystemCost = unitSystemCost;
    }
}
