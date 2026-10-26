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
import pl.commercelink.products.information.StorePimAliases;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
    @JsonProperty("paymentOptionId")
    private String paymentOptionId;
    @JsonProperty("discountCode")
    private String discountCode;

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
        return toBasketItems(catalogId, pricelist, null, null);
    }

    /** As above; an item whose id is the source or the clone of a pricelist row of the store is found by that row. */
    public List<BasketItem> toBasketItems(String catalogId, Pricelist pricelist, String storeId, StorePimAliases aliases) {
        return getCheckoutItems(catalogId).stream()
                .map(i -> toBasketItem(storeId, i, pricelist, aliases))
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

    public List<BasketItem> toBasketItems(String storeId, PricelistRepository pricelistRepository) {
        return toBasketItems(storeId, pricelistRepository, null);
    }

    /** As above; an item whose id is the source or the clone of a pricelist row of the store is found by that row. */
    public List<BasketItem> toBasketItems(String storeId, PricelistRepository pricelistRepository, StorePimAliases aliases) {
        return checkoutItems.stream()
                .map(i -> toBasketItem(storeId, i, pricelistRepository, aliases))
                .collect(Collectors.toList());
    }

    private BasketItem toBasketItem(String storeId, CheckoutItem checkoutItem, PricelistRepository pricelistRepository, StorePimAliases aliases) {
        Pricelist pricelist = pricelistRepository.find(storeId, checkoutItem.getCatalogId(), checkoutItem.getPricelistId());

        return toBasketItem(storeId, checkoutItem, pricelist, aliases);
    }

    private BasketItem toBasketItem(String storeId, CheckoutItem checkoutItem, Pricelist pricelist, StorePimAliases aliases) {
        if (pricelist == null) {
            throw new CheckoutException(CheckoutError.PricelistOutdated,
                    "pricelist " + checkoutItem.getPricelistId() + " of catalog " + checkoutItem.getCatalogId() + " not found");
        }
        Optional<AvailabilityAndPrice> found = aliases == null
                ? pricelist.findByPimId(checkoutItem.getItemId())
                : aliases.find(storeId, pricelist, checkoutItem.getItemId());
        AvailabilityAndPrice availabilityAndPrice = found
                .orElseThrow(() -> new CheckoutException(CheckoutError.ItemNotFound,
                        "item " + checkoutItem.getItemId() + " not found in pricelist " + pricelist.getPricelistId()));

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

    public String getPaymentOptionId() {
        return paymentOptionId;
    }

    public String getDiscountCode() {
        return discountCode;
    }
}
