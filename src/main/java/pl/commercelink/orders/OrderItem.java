package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.warehouse.api.ReservationConfirmation;

import java.util.UUID;

@DynamoDBTable(tableName = "OrderItems")
public class OrderItem extends Item {

    public static final String GENERIC_WAREHOUSE_ORDER_NO = "Warehouse";

    @DynamoDBHashKey(attributeName = "orderId")
    private String orderId;
    @DynamoDBRangeKey(attributeName = "itemId")
    private String itemId;

    @DynamoDBAttribute(attributeName = "sku")
    private String sku;
    @DynamoDBAttribute(attributeName = "unitPrice")
    private double price;
    @DynamoDBAttribute(attributeName = "consolidated")
    private boolean consolidated;
    @DynamoDBAttribute(attributeName = "externalItemId")
    private String externalItemId;

    @DynamoDBIgnore
    private boolean selected;

    // required for DynamoDB
    public OrderItem() {
    }

    public OrderItem(String orderId, ProductCategory category, String name, int qty, double price, String sku, boolean consolidated) {
        super(category, name, qty, null);
        this.orderId = orderId;
        this.itemId = UUID.randomUUID().toString();
        this.sku = sku;
        this.price = price;
        this.consolidated = consolidated;
    }

    public OrderItem(String orderId, OrderItem source, int qty) {
        super(source.getCategory(), source.getName(), qty, source.getComment());
        this.orderId = orderId;
        this.itemId = UUID.randomUUID().toString();
        this.sku = source.getSku();
        this.price = source.getPrice();
        this.consolidated = source.isConsolidated();
        this.externalItemId = source.getExternalItemId();

        this.addFulfilment(
            source.getEan(),
            source.getManufacturerCode(),
            source.getCost(),
            source.getTax(),
            source.getDeliveryId(),
            source.getSerialNo(),
            source.getStatus()
        );
    }

    public void markAsWarehouseFulfilled() {
        if (hasCategory(ProductCategory.Services)) {
            setDeliveryId(GENERIC_WAREHOUSE_ORDER_NO);
            markAsReceived();
        }
    }

    public void update(OrderItem other) {
        if (isNew()) {
            updateAllFields(other);
        } else {
            updateLimitedFields(other);
        }

        if (canBeFulfilledInternally()) {
            markAsReceived();
        }
    }

    public void updateAllFields(OrderItem other) {
        this.setCategory(other.getCategory());
        this.setSku(other.getSku());
        this.setName(other.getName());
        this.setPrice(other.getPrice());
        this.setTax(other.getTax());
        this.setQty(other.getQty());
        this.setSerialNo(other.getSerialNo());

        // fulfilment related fields
        this.setEan(other.getEan());
        this.setManufacturerCode(other.getManufacturerCode());
        this.setCost(other.getCost());
        this.setDeliveryId(other.getDeliveryId());

        this.setConsolidated(other.isConsolidated());
        this.setComment(other.getComment());
    }

    public void updateLimitedFields(OrderItem other) {
        this.setCategory(other.getCategory());
        this.setName(other.getName());
        this.setComment(other.getComment());
        this.setSerialNo(other.getSerialNo());
        this.setConsolidated(other.isConsolidated());
    }

    public void copyFulfilmentFrom(ReservationConfirmation confirmation) {
        this.setQty(confirmation.qty());
        this.addFulfilment(
                confirmation.ean(),
                confirmation.mfn(),
                confirmation.cost().netValue(),
                confirmation.cost().vatRate(),
                confirmation.deliveryId(),
                null,
                confirmation.inStock() ? FulfilmentStatus.Delivered : FulfilmentStatus.Ordered
        );
        this.setComment("Magazyn");
    }

    @DynamoDBIgnore
    public void markAsReturned() {
        this.setStatus(FulfilmentStatus.Returned);
        this.setPrice(0);
        this.setCost(0);
    }

    @DynamoDBIgnore
    public boolean isReturned() {
        return this.getStatus() == FulfilmentStatus.Returned;
    }

    @DynamoDBIgnore
    public void markAsReplaced() {
        this.setStatus(FulfilmentStatus.Replaced);
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getItemId() {
        return itemId;
    }

    @DynamoDBIgnore
    public String getShortenedItemId() {
        return ConversionUtil.getShortenedId(itemId);
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getExternalItemId() {
        return externalItemId;
    }

    public void setExternalItemId(String externalItemId) {
        this.externalItemId = externalItemId;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isConsolidated() {
        return consolidated;
    }

    public void setConsolidated(boolean consolidated) {
        this.consolidated = consolidated;
    }

    @DynamoDBIgnore
    public double getTotalPrice() {
        return price * getQty();
    }

    @DynamoDBIgnore
    public boolean isSelected() {
        return selected;
    }

    @DynamoDBIgnore
    public boolean hasSKU() {
        return sku != null && !sku.isEmpty();
    }

    @DynamoDBIgnore
    public int getSequenceNumber() {
        return getCategory().ordinal();
    }

    @DynamoDBIgnore
    public boolean canBeFulfilledInternally() {
        return getCategory() == ProductCategory.Services && isWarehouseFulfilled();
    }

    @DynamoDBIgnore
    public boolean isWarehouseFulfilled() {
        return GENERIC_WAREHOUSE_ORDER_NO.equals(getDeliveryId());
    }

    @DynamoDBIgnore
    public static OrderItem fromBasketItem(String orderId, BasketItem basketItem) {
        return new OrderItem(
                orderId,
                basketItem.getCategory(),
                basketItem.getName(),
                (int) basketItem.getQty(),
                basketItem.getPrice(),
                basketItem.getManufacturerCode(),
                basketItem.isConsolidated()
        );
    }

    public static OrderItem fromDeliveryOption(String orderId, DeliveryOption opt) {
        return new OrderItem(
                orderId,
                ProductCategory.Services,
                opt.getName(),
                1,
                opt.getPrice(),
                null,
                false
        );
    }

    @DynamoDBIgnore
    public OrderItem copyWithNewQty(int qty) {
        return new OrderItem(this.orderId, this, qty);
    }

}
