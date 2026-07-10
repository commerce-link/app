package pl.commercelink.baskets;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentItem;
import pl.commercelink.orders.fulfilment.FulfilmentGroupsGenerator;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.stores.SupplierScope;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class OfferItemReloader {

    private final Inventory inventory;
    private final PricelistRepository pricelistRepository;

    OfferItemReloader(Inventory inventory, PricelistRepository pricelistRepository) {
        this.inventory = inventory;
        this.pricelistRepository = pricelistRepository;
    }

    public List<OfferItem> reload(Basket basket) {
        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(basket.getStoreId(), SupplierScope.PRICING);
        return convertBasketItemsIntoOffers(enabledInventory, basket.getBasketItems());
    }

    public List<OfferItem> recalculate(Basket basket) {
        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(basket.getStoreId(), SupplierScope.PRICING);

        updatePrices(basket);
        updateCosts(enabledInventory, basket.getBasketItems());

        return convertBasketItemsIntoOffers(enabledInventory, basket.getBasketItems());
    }

    private List<OfferItem> convertBasketItemsIntoOffers(InventoryView inventory, List<BasketItem> basketItems) {
        List<OfferItem> offerItems = new ArrayList<>();

        for (int i = 0; i < basketItems.size(); i++) {
            OfferItem offerItem = createOfferItem(inventory, basketItems.get(i));
            offerItem.setSequenceNumber(i);
            offerItems.add(offerItem);
        }

        return sort(offerItems);
    }

    private OfferItem createOfferItem(InventoryView inventory, BasketItem basketItem) {
        if (isNotBlank(basketItem.getName())) {
            MatchedInventory matchedInventory = findMatchedInventoryLowestPricedSKU(inventory, basketItem);

            if (matchedInventory.hasAnyOffers()) {
                return new OfferItem(basketItem, matchedInventory);
            }

            return new OfferItem(basketItem);
        }

        return new OfferItem();
    }

    private List<OfferItem> sort(List<OfferItem> offerItems) {
        return offerItems.stream()
                .sorted(Comparator.comparingInt(OfferItem::getPosition)
                        .thenComparing(Comparator.comparingDouble(OfferItem::getUnitPrice).reversed()))
                .collect(Collectors.toList());
    }

    private void updatePrices(Basket basket) {
        String storeId = basket.getStoreId();
        basket.getBasketItems().stream()
                .filter(i -> isNotBlank(i.getCatalogId()))
                .filter(i -> isNotBlank(i.getId()))
                .forEach(i -> {
                    String newestPricelistId = pricelistRepository.findNewestPricelistIdCached(storeId, i.getCatalogId());
                    Pricelist pricelist = pricelistRepository.find(storeId, i.getCatalogId(), newestPricelistId);
                    if (pricelist == null) return;

                    Optional<AvailabilityAndPrice> op = pricelist.findByPimId(i.getId());
                    if (op.isPresent()) {
                        AvailabilityAndPrice availability = op.get();

                        i.setMfn(availability.getManufacturerCode());
                        i.setUnitPrice((double) availability.getPrice());
                    }
                });
    }

    private void updateCosts(InventoryView inventory, List<BasketItem> basketItems) {
        basketItems.stream()
                .filter(i -> isNotBlank(i.getMfn()))
                .filter(i -> i.getUnitCost() == 0)
                .forEach(basketItem -> {
                    MatchedInventory matchedInventory = findMatchedInventoryLowestPricedSKU(inventory, basketItem);
                    if (matchedInventory.hasAnyOffers()) {
                        basketItem.setEstimatedDeliveryDays(matchedInventory.getEstimatedDeliveryDays());
                        basketItem.setUnitCost(matchedInventory.getMedianPrice().grossValue());
                    }
                });
    }

    private MatchedInventory findMatchedInventoryLowestPricedSKU(InventoryView inventory, BasketItem basketItem) {
        FulfilmentItem lowestPricedFulfilmentItem = findLowestPricedFulfilmentCandidate(inventory, basketItem);
        if (lowestPricedFulfilmentItem != null) {
            return inventory.findByProductCode(lowestPricedFulfilmentItem.getSource().getMfn());
        }
        return MatchedInventory.empty(new InventoryKey());
    }

    // We use FulfilmentCandidatesGenerator instead of MatchedInventory as some items have multiple inventory items assigned, and we want to find the lowest priced one.
    private FulfilmentItem findLowestPricedFulfilmentCandidate(InventoryView inventory, BasketItem basketItem) {
        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory)
                .build();

        OrderItem orderItem = OrderItem.fromBasketItem(null, basketItem);

        return generator.run(Collections.singletonList(orderItem)).stream()
                .filter(c -> c.isFor(orderItem))
                .findFirst()
                .orElse(null);
    }

}
