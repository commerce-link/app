package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.taxonomy.Taxonomy;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class BasketItem {

    public static final String SHIPPING_SKU = "Shipping";

    @DynamoDBAttribute(attributeName = "id")
    private  String id;
    @DynamoDBAttribute(attributeName = "name")
    private  String name;
    @DynamoDBAttribute(attributeName = "sku")
    private String sku;
    private String category;
    @DynamoDBAttribute(attributeName = "brand")
    private String brand;
    @DynamoDBAttribute(attributeName = "qty")
    private  long qty;
    @DynamoDBAttribute(attributeName = "unitPrice")
    private double unitPrice;
    @DynamoDBAttribute(attributeName = "unitCost")
    private double unitCost;
    @DynamoDBAttribute(attributeName = "catalogId")
    private  String catalogId;
    @DynamoDBAttribute(attributeName = "estimatedDeliveryDays")
    private int estimatedDeliveryDays;
    @DynamoDBAttribute(attributeName = "consolidated")
    private boolean consolidated;
    @DynamoDBAttribute(attributeName = "position")
    private int position;
    private boolean service;
    @DynamoDBAttribute(attributeName = "variantGroupId")
    private String variantGroupId;
    @DynamoDBAttribute(attributeName = "variantSelected")
    private boolean variantSelected;

    public BasketItem() {
    }

    public BasketItem(String id, String name, String sku,
                      String category, double unitPrice, double unitCost, long qty,
                      String catalogId, int estimatedDeliveryDays, boolean consolidated) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
        this.catalogId = catalogId;
        this.estimatedDeliveryDays = estimatedDeliveryDays;
        this.consolidated = consolidated;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(name) && isNotBlank(sku) && (service || category != null) && qty > 0 && unitPrice >= 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @DynamoDBAttribute(attributeName = "category")
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public long getQty() {
        return qty;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setQty(long qty) {
        this.qty = qty;
    }

    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
    }

    public double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(double unitCost) {
        this.unitCost = unitCost;
    }

    public int getEstimatedDeliveryDays() {
        return estimatedDeliveryDays;
    }

    public void setEstimatedDeliveryDays(int estimatedDeliveryDays) {
        this.estimatedDeliveryDays = estimatedDeliveryDays;
    }

    public boolean isConsolidated() {
        return consolidated;
    }

    public void setConsolidated(boolean consolidated) {
        this.consolidated = consolidated;
    }

    @DynamoDBAttribute(attributeName = "service")
    public boolean isService() {
        return service;
    }

    public void setService(boolean service) {
        this.service = service;
    }

    @DynamoDBIgnore
    public boolean isProduct() {
        return !isService();
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getVariantGroupId() {
        return variantGroupId;
    }

    public void setVariantGroupId(String variantGroupId) {
        this.variantGroupId = isNotBlank(variantGroupId) ? variantGroupId.trim() : null;
    }

    public boolean isVariantSelected() {
        return variantSelected;
    }

    public void setVariantSelected(boolean variantSelected) {
        this.variantSelected = variantSelected;
    }

    @DynamoDBIgnore
    public boolean isInVariantGroup() {
        return variantGroupId != null;
    }

    @DynamoDBIgnore
    public boolean isInVariantGroup(String groupId) {
        return variantGroupId != null && variantGroupId.equals(groupId);
    }

    @DynamoDBIgnore
    public boolean isShippingItem() {
        return SHIPPING_SKU.equals(sku);
    }

    public static BasketItem shipping(String name, double shippingPrice) {
        BasketItem item = new BasketItem(UniqueIdentifierGenerator.generate(),
                name,
                SHIPPING_SKU,
                OrderItem.DELIVERY_CATEGORY,
                shippingPrice,
                0,
                1,
                null,
                1,
                false
        );
        item.setService(true);
        return item;
    }

    public static BasketItem shipping(double shippingPrice) {
        return shipping("Dostawa kurierem", shippingPrice);
    }

    public static BasketItem of(AvailabilityAndPrice availabilityAndPrice, long qty, String catalogId, boolean consolidated) {
        BasketItem item = new BasketItem(
                availabilityAndPrice.getPimId(),
                availabilityAndPrice.getName(),
                availabilityAndPrice.getEan(),
                availabilityAndPrice.getCategory(),
                availabilityAndPrice.getPrice(),
                0,
                qty,
                catalogId,
                availabilityAndPrice.getEstimatedDeliveryDays(),
                consolidated
        );
        item.setService(availabilityAndPrice.isService());
        item.setBrand(availabilityAndPrice.getBrand());
        return item;
    }

    public static BasketItem of(MatchedInventory matchedInventory, String ean, long qty, boolean consolidated) {
        if (!matchedInventory.hasAnyOffers()) {
            return BasketItem.missing(ean, qty, consolidated);
        } else {
            Taxonomy taxonomy = matchedInventory.getTaxonomy();
            return new BasketItem(
                    "",
                    taxonomy.name(),
                    ean,
                    taxonomy.category(),
                    matchedInventory.getMedianPrice().grossValue(),
                    // client 2026-10-27: an item outside the price list costs the estimate, not the lowest price
                    EstimatedCost.between(matchedInventory.getLowestPrice().grossValue(),
                            matchedInventory.getMedianPrice().grossValue()),
                    qty,
                    null,
                    matchedInventory.getEstimatedDeliveryDays(),
                    consolidated
            );
        }
    }

    private static BasketItem missing(String sku, long qty, boolean consolidated) {
        return new BasketItem(UniqueIdentifierGenerator.generate(),
                "Brak produktu",
                sku,
                Categories.UNCATEGORIZED,
                0,
                0,
                qty,
                null,
                3,
                consolidated
        );
    }

    @DynamoDBIgnore
    public double getTotalPrice() {
        return unitPrice * qty;
    }

    @DynamoDBIgnore
    public double getTotalCost() {
        return unitCost * qty;
    }

}
