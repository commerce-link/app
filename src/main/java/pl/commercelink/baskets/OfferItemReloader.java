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

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class OfferItemReloader {

    private final Inventory inventory;
    private final PricelistRepository pricelistRepository;
    private final BasketsRepository basketsRepository;

    OfferItemReloader(Inventory inventory, PricelistRepository pricelistRepository, BasketsRepository basketsRepository) {
        this.inventory = inventory;
        this.pricelistRepository = pricelistRepository;
        this.basketsRepository = basketsRepository;
    }

    public List<OfferItem> reload(String storeId, Basket basket) {
        return reload(inventory.withEnabledSuppliersOnly(storeId), basket);
    }

    public List<OfferItem> recalculate(String storeId, Basket basket) {
        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(storeId);

        updatePrices(basket.getBasketItems());
        updateCosts(enabledInventory, basket.getBasketItems());

        return reload(enabledInventory, basket);
    }

    private List<OfferItem> reload(InventoryView inventory, Basket basket) {
        updateEstimatedDeliveryDates(inventory, basket.getBasketItems());

        List<OfferItem> sortedOfferItems = convertBasketItemsIntoOffers(inventory, basket.getBasketItems());

        List<BasketItem> newOrderedBasketItems = sortedOfferItems.stream()
                .map(OfferItem::getBasketItem)
                .collect(Collectors.toList());

        basket.setBasketItems(newOrderedBasketItems);
        basketsRepository.save(basket);

        return sortedOfferItems;
    }

    private List<OfferItem> convertBasketItemsIntoOffers(InventoryView inventory, List<BasketItem> basketItems) {
        List<OfferItem> offerItems = new ArrayList<>();

        for (int index = 0; index < basketItems.size(); index++) {
            BasketItem basketItem = basketItems.get(index);
            offerItems.add(createOfferItem(inventory, basketItem, index));
        }

        return sort(offerItems);
    }

    private OfferItem createOfferItem(InventoryView inventory, BasketItem basketItem, int index) {
        if (isNotBlank(basketItem.getName())) {
            MatchedInventory matchedInventory = findMatchedInventoryLowestPricedSKU(inventory, basketItem);

            if (matchedInventory.hasAnyOffers()) {
                return new OfferItem(index, basketItem, matchedInventory);
            }

            return new OfferItem(index, basketItem);
        }

        return new OfferItem(index);
    }

    private List<OfferItem> sort(List<OfferItem> offerItems) {
        List<OfferItem> sortedOfferItems = offerItems.stream()
                .sorted(Comparator.comparingInt((OfferItem o) -> getCategoryOrdinal(o.getBasketItem())).reversed()
                        .thenComparing(Comparator.comparingInt(OfferItem::getSequenceNumber).reversed())
                )
                .collect(Collectors.toList());

        Collections.reverse(sortedOfferItems);
        for (int i = 0; i < sortedOfferItems.size(); i++) {
            sortedOfferItems.get(i).setSequenceNumber(i);
        }
        return sortedOfferItems;
    }

    private void updatePrices(List<BasketItem> basketItems) {
        basketItems.stream()
                .filter(i -> isNotBlank(i.getCatalogId()))
                .filter(i -> isNotBlank(i.getId()))
                .forEach(i -> {
                    String newestPricelistId = pricelistRepository.findNewestPricelistIdCached(i.getCatalogId());
                    Pricelist pricelist = pricelistRepository.find(i.getCatalogId(), newestPricelistId);
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

    private void updateEstimatedDeliveryDates(InventoryView inventory, List<BasketItem> basketItems) {
        basketItems.stream()
                .filter(i -> isNotBlank(i.getMfn()))
                .forEach(basketItem -> {
                    MatchedInventory matchedInventory = findMatchedInventoryLowestPricedSKU(inventory, basketItem);
                    if (matchedInventory.hasAnyOffers()) {
                        basketItem.setEstimatedDeliveryDays(matchedInventory.getEstimatedDeliveryDays());
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

    private int getCategoryOrdinal(BasketItem basketItem) {
        if (basketItem == null || basketItem.getCategory() == null) {
            return Integer.MAX_VALUE;
        }
        return basketItem.getCategory().ordinal();
    }

}
