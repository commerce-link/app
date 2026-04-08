package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class BasketItem {

    public static final String SHIPPING_MFN_CODE = "Shipping";

    @DynamoDBAttribute(attributeName = "id")
    private  String id;
    @DynamoDBAttribute(attributeName = "name")
    private  String name;
    @DynamoDBAttribute(attributeName = "manufacturerCode")
    private  String manufacturerCode;
    @DynamoDBAttribute(attributeName = "category")
    @DynamoDBTypeConvertedEnum
    private  ProductCategory category;
    @DynamoDBAttribute(attributeName = "qty")
    private  long qty;// --
    @DynamoDBAttribute(attributeName = "price")
    private double price;
    @DynamoDBAttribute(attributeName = "cost")
    private double cost;
    @DynamoDBAttribute(attributeName = "catalogId")
    private  String catalogId;
    @DynamoDBAttribute(attributeName = "estimatedDeliveryDays")
    private int estimatedDeliveryDays;
    @DynamoDBAttribute(attributeName = "consolidated")
    private boolean consolidated;

    public BasketItem() {
    }

    public BasketItem(String id, String name, String manufacturerCode,
                      ProductCategory category, double price, double cost, long qty,
                      String catalogId, int estimatedDeliveryDays, boolean consolidated) {
        this.id = id;
        this.name = name;
        this.manufacturerCode = manufacturerCode;
        this.category = category;
        this.qty = qty;
        this.price = price;
        this.cost = cost;
        this.catalogId = catalogId;
        this.estimatedDeliveryDays = estimatedDeliveryDays;
        this.consolidated = consolidated;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(name) && isNotBlank(manufacturerCode) && category != null && qty > 0 && price >= 0;
    }

    @DynamoDBIgnore
    public boolean isProduct() {
        return category != ProductCategory.Services;
    }

    @DynamoDBIgnore
    public boolean isService() {
        return category == ProductCategory.Services;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public double getPrice() {
        return price;
    }

    public long getQty() {
        return qty;
    }

    public String getManufacturerCode() {
        return this.manufacturerCode;
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

    public void setManufacturerCode(String manufacturerCode) {
        this.manufacturerCode = manufacturerCode;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public void setQty(long qty) {
        this.qty = qty;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
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

    @DynamoDBIgnore
    public boolean hasCategory(ProductCategory category) {
        return this.category == category;
    }

    @DynamoDBIgnore
    public boolean isShippingItem() {
        return SHIPPING_MFN_CODE.equals(this.manufacturerCode);
    }

    public static BasketItem shipping(String name, double shippingPrice) {
        return new BasketItem(UniqueIdentifierGenerator.generate(),
                name,
                SHIPPING_MFN_CODE,
                ProductCategory.Services,
                shippingPrice,
                0,
                1,
                null,
                1,
                false
        );
    }

    public static BasketItem shipping(double shippingPrice) {
        return shipping("Dostawa kurierem", shippingPrice);
    }

    public static BasketItem of(AvailabilityAndPrice availabilityAndPrice, long qty, String catalogId, boolean consolidated) {
        return new BasketItem(
                availabilityAndPrice.getPimId(),
                availabilityAndPrice.getName(),
                availabilityAndPrice.getManufacturerCode(),
                availabilityAndPrice.getCategory(),
                availabilityAndPrice.getPrice(),
                0,
                qty,
                catalogId,
                availabilityAndPrice.getEstimatedDeliveryDays(),
                consolidated
        );
    }

    public static BasketItem of(MatchedInventory matchedInventory, long qty, boolean consolidated) {
        if (!matchedInventory.hasAnyOffers()) {
            return BasketItem.missing(matchedInventory.getInventoryKey().getProductCodes().iterator().next(), qty, consolidated);
        } else {
            Taxonomy taxonomy = matchedInventory.getTaxonomy();
            return new BasketItem(
                    "",
                    taxonomy.name(),
                    taxonomy.mfn(),
                    taxonomy.category(),
                    matchedInventory.getMedianPrice().grossValue(),
                    matchedInventory.getLowestPrice().grossValue(),
                    qty,
                    null,
                    matchedInventory.getEstimatedDeliveryDays(),
                    consolidated
            );
        }
    }

    private static BasketItem missing(String mfn, long qty, boolean consolidated) {
        return new BasketItem(UniqueIdentifierGenerator.generate(),
                "Brak produktu",
                mfn,
                ProductCategory.Other,
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
        return price * qty;
    }

    @DynamoDBIgnore
    public double getTotalCost() {
        return cost * qty;
    }

}
