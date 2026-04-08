package pl.commercelink.checkout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckoutRequest {

    @JsonProperty("items")
    private List<CheckoutItem> checkoutItems;
    @JsonProperty("billingDetails")
    private BillingDetails billingDetails;
    @JsonProperty("shippingDetails")
    private ShippingDetails shippingDetails;
    @JsonProperty("affiliateId")
    private String affiliateId;
    @JsonProperty("sendInvoice")
    private boolean sendInvoice;
    @JsonProperty("deliveryOptionId")
    private String deliveryOptionId;

    @JsonIgnore
    public Collection<String> getCatalogIds() {
        return checkoutItems.stream()
                .filter(CheckoutItem::hasPricelistInformation)
                .map(CheckoutItem::getCatalogId)
                .collect(Collectors.toSet());
    }

    public String getPricelistId(String catalogId) {
        return getCheckoutItems(catalogId).iterator().next().getPricelistId();
    }

    public List<BasketItem> toBasketItems(String catalogId, Pricelist pricelist) {
        return getCheckoutItems(catalogId).stream()
                .map(i -> toBasketItem(i, pricelist))
                .collect(Collectors.toList());
    }

    public boolean isListed(String itemId) {
        return checkoutItems.stream().filter(i -> i.getItemId().equalsIgnoreCase(itemId)).findFirst().get().isListed();
    }

    private List<CheckoutItem> getCheckoutItems(String catalogId) {
        return checkoutItems.stream()
                .filter(i -> i.hasCatalogId(catalogId))
                .collect(Collectors.toList());
    }

    public List<BasketItem> toBasketItems(PricelistRepository pricelistRepository) {
        return checkoutItems.stream()
                .map(i -> toBasketItem(i, pricelistRepository))
                .collect(Collectors.toList());
    }

    private BasketItem toBasketItem(CheckoutItem checkoutItem, PricelistRepository pricelistRepository) {
        Pricelist pricelist = pricelistRepository.find(checkoutItem.getCatalogId(), checkoutItem.getPricelistId());

        return toBasketItem(checkoutItem, pricelist);
    }

    private BasketItem toBasketItem(CheckoutItem checkoutItem, Pricelist pricelist) {
        AvailabilityAndPrice availabilityAndPrice = pricelist.findByPimId(checkoutItem.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found in pricelist: " + checkoutItem.getItemId()));

        return BasketItem.of(availabilityAndPrice, checkoutItem.getQty(), checkoutItem.getCatalogId(), !checkoutItem.isListed());
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public List<CheckoutItem> getCheckoutItems() {
        return checkoutItems;
    }

    public BillingDetails getBillingDetails() {
        return billingDetails;
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public boolean isSendInvoice() {
        return sendInvoice;
    }

    public String getDeliveryOptionId() {
        return deliveryOptionId;
    }
}
