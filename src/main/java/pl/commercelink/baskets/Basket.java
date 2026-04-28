package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@DynamoDBTable(tableName = "Baskets")
public class Basket {

    @DynamoDBHashKey(attributeName = "storeId")
    @DynamoDBIndexHashKey(globalSecondaryIndexNames = "BasketCreatedAtIndex", attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "basketId")
    private String basketId;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private BasketType type = BasketType.Basket;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "affiliateId")
    private String affiliateId ;
    @DynamoDBAttribute(attributeName = "gclid")
    private String gclid;
    @DynamoDBAttribute(attributeName = "source")
    private OrderSource source;
    @DynamoDBAttribute(attributeName = "basketItems")
    private List<BasketItem> basketItems = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "BasketCreatedAtIndex", attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;
    @DynamoDBAttribute(attributeName = "expiresAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime expiresAt;
    @DynamoDBAttribute(attributeName = "contactDetails")
    private ContactDetails contactDetails;
    @DynamoDBAttribute(attributeName = "billingDetails")
    private BillingDetails billingDetails;
    @DynamoDBAttribute(attributeName = "shippingDetails")
    private ShippingDetails shippingDetails;
    @DynamoDBAttribute(attributeName = "fulfilmentType")
    @DynamoDBTypeConvertedEnum
    private FulfilmentType fulfilmentType = FulfilmentType.WarehouseFulfilment;
    @DynamoDBAttribute(attributeName = "showPrices")
    private boolean showPrices;

    @DynamoDBAttribute(attributeName = "comment")
    private String comment;

    @DynamoDBAttribute(attributeName = "deliveryOptionId")
    private String deliveryOptionId;

    public Basket() {
    }

    private Basket(String storeId) {
        this.storeId = storeId;
        this.basketId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusDays(3);
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getBasketId() {
        return basketId;
    }

    public void setBasketId(String basketId) {
        this.basketId = basketId;
    }

    public BasketType getType() { return type; }

    @DynamoDBIgnore
    public boolean hasType(BasketType type) { return this.type == type; }

    public void setType(BasketType type) { this.type = type; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public List<BasketItem> getBasketItems() {
        return basketItems;
    }

    @DynamoDBIgnore
    public List<BasketItem> getBasketItemsForProducts() {
        return basketItems.stream().filter(i -> !i.hasCategory(ProductCategory.Services)).collect(Collectors.toList());
    }

    @DynamoDBIgnore
    public List<BasketItem> getBasketItemsForServices() {
        return basketItems.stream().filter(i -> i.hasCategory(ProductCategory.Services)).collect(Collectors.toList());
    }

    public void setBasketItems(List<BasketItem> basketItems) {
        this.basketItems = basketItems.stream()
                .peek(i -> {
                    var unifiedMfn = UnifiedProductIdentifiers.unifyMfn(i.getMfn());
                    i.setMfn(unifiedMfn);
                }).collect(Collectors.toList());
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() { return expiresAt; }

    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public BillingDetails getBillingDetails() {
        return billingDetails;
    }

    public void setBillingDetails(BillingDetails billingDetails) {
        this.billingDetails = billingDetails;
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(ShippingDetails shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    public String getComment() { return comment; }

    public void setComment(String comment) { this.comment = comment; }

    public String getDeliveryOptionId() { return deliveryOptionId; }

    public void setDeliveryOptionId(String deliveryOptionId) { this.deliveryOptionId = deliveryOptionId; }

    public ContactDetails getContactDetails() { return contactDetails; }

    public void setContactDetails(ContactDetails contactDetails) { this.contactDetails = contactDetails; }

    public String getGclid() { return gclid; }

    public void setGclid(String gclid) { this.gclid = gclid; }

    public FulfilmentType getFulfilmentType() {
        return fulfilmentType;
    }

    public void setFulfilmentType(FulfilmentType fulfilmentType) {
        this.fulfilmentType = fulfilmentType;
    }

    public boolean isShowPrices() {
        return showPrices;
    }

    public void setShowPrices(boolean showPrices) {
        this.showPrices = showPrices;
    }

    public OrderSource getSource() {
        return source;
    }

    public void setSource(OrderSource source) {
        this.source = source;
    }

    @DynamoDBIgnore
    public Basket deepCopy(String suffix, BasketType desiredType) {
        Basket copy = new Basket(storeId);
        copy.setType(desiredType);
        copy.setName(name + suffix);
        copy.setBasketItems(new LinkedList<>(basketItems));
        copy.setFulfilmentType(fulfilmentType);
        copy.setShowPrices(showPrices);
        copy.setComment(comment);
        copy.setDeliveryOptionId(deliveryOptionId);
        copy.setContactDetails(null);
        copy.setGclid(null);
        return copy;
    }

    @DynamoDBIgnore
    public boolean isExpired() {
        return type == BasketType.Offer && expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    @DynamoDBIgnore
    public double getTotalPrice() {
        return basketItems.stream()
                .mapToDouble(BasketItem::getTotalPrice)
                .sum();
    }

    @DynamoDBIgnore
    public String createOfferUrl(String domain) {
        return domain + "/store/" + this.storeId + "/individual/offer/" + this.basketId;
    }

    @DynamoDBIgnore
    public Optional<DeliveryOption> resolveDeliveryOption(Store store) {
        if (deliveryOptionId == null) return Optional.empty();
        return Optional.of(store.getCheckoutConfiguration().findDeliveryOption(deliveryOptionId));
    }

    @DynamoDBIgnore
    public double getDeliveryPrice(Store store) {
        return resolveDeliveryOption(store).map(DeliveryOption::getPrice).orElse(0.0);
    }

    @DynamoDBIgnore
    public Optional<BasketItem> getShippingItem() {
        return basketItems.stream()
                .filter(i -> i.hasCategory(ProductCategory.Services))
                .filter(BasketItem::isShippingItem)
                .findFirst();
    }

    @DynamoDBIgnore
    public static Builder builder(Store store) {
        return new Builder(store.getStoreId())
                .withFulfilmentType(store.getDefaultFulfilmentType());
    }

    public static class Builder {

        private final Basket basket;

        public Builder(String storeId) {
            this.basket = new Basket(storeId);
        }

        public Builder withBasketId(String basketId) {
            this.basket.setBasketId(basketId);
            return this;
        }

        public Builder withType(BasketType type) {
            this.basket.setType(type);
            return this;
        }

        public Builder withName(String name) {
            this.basket.setName(name);
            return this;
        }

        public Builder withAffiliateId(String affiliateId) {
            this.basket.setAffiliateId(affiliateId);
            return this;
        }

        public Builder withBasketItems(List<BasketItem> basketItems) {
            this.basket.setBasketItems(basketItems.stream().filter(BasketItem::isComplete).collect(Collectors.toList()));
            return this;
        }

        public Builder withFulfilmentType(FulfilmentType fulfilmentType) {
            this.basket.setFulfilmentType(fulfilmentType);
            return this;
        }

        public Builder withShowPrices(boolean showPrices) {
            this.basket.setShowPrices(showPrices);
            return this;
        }

        public Builder withBillingDetails(BillingDetails billingDetails) {
            this.basket.setBillingDetails(billingDetails);
            return this;
        }

        public Builder withShippingDetails(ShippingDetails shippingDetails) {
            this.basket.setShippingDetails(shippingDetails);
            return this;
        }

        public Builder withSource(OrderSource orderSource) {
            this.basket.setSource(orderSource);
            return this;
        }

        public Builder withDeliveryOptionId(String deliveryOptionId) {
            this.basket.setDeliveryOptionId(deliveryOptionId);
            return this;
        }

        public Builder withContactDetails(ContactDetails contactDetails) {
            this.basket.setContactDetails(contactDetails);
            return this;
        }

        public Builder withGclid(String gclid) {
            this.basket.setGclid(gclid);
            return this;
        }

        public Basket build() {
            return basket;
        }
    }
}
