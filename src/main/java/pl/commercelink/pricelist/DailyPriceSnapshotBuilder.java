package pl.commercelink.pricelist;

import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class DailyPriceSnapshotBuilder {

    private DailyPriceSnapshotBuilder() {
    }

    static List<DailyPriceSnapshot> build(InventoryView inventory, LocalDate date) {
        List<DailyPriceSnapshot> rows = new ArrayList<>();
        for (MatchedInventory matchedInventory : inventory.findAllWithPimId()) {
            if (matchedInventory.hasOffersFromMultipleSuppliers(2) || matchedInventory.hasTotalMinQty(2)) {
                rows.add(createSnapshot(matchedInventory, date));
            }
        }
        return rows;
    }

    private static DailyPriceSnapshot createSnapshot(MatchedInventory matchedInventory, LocalDate date) {
        List<InventoryItem> items = matchedInventory.getInventoryItems();

        Price lowestPrice = matchedInventory.getLowestPrice();
        Price medianPrice = matchedInventory.getMedianPrice();
        Price highestPrice = matchedInventory.getHighestPrice();

        // lookup item inventory item that is connected with the lowest price
        InventoryItem lowestPricedItem = items.stream()
                .filter(i -> Price.fromNet(i.netPrice()).netValue() == lowestPrice.netValue())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No inventory item found with lowest price"));

        // lookup three best value distributors starting with the lowest priced one
        int lowestIndex = items.indexOf(lowestPricedItem);
        List<InventoryItem> bestValueItems = items.subList(lowestIndex, Math.min(lowestIndex + 3, items.size()));

        String bestValueDistributorsPipeSeparated = bestValueItems.stream()
                .map(InventoryItem::supplier)
                .collect(Collectors.joining("|"));
        String bestValuePricesPipeSeparated = bestValueItems.stream()
                .map(i -> String.valueOf(i.netPrice()))
                .collect(Collectors.joining("|"));

        // all available distributors starting with the lowest priced one
        String allAvailableDistributorsPipeSeparated = items.subList(lowestIndex, items.size()).stream()
                .map(InventoryItem::supplier)
                .distinct()
                .collect(Collectors.joining("|"));

        return new DailyPriceSnapshot(
                matchedInventory.getInventoryKey().getId(),
                lowestPrice.netValue(),
                medianPrice.netValue(),
                highestPrice.netValue(),
                lowestPricedItem.qty(),
                matchedInventory.getTotalAvailableQty(),
                lowestPricedItem.supplier(),
                bestValueDistributorsPipeSeparated,
                bestValuePricesPipeSeparated,
                allAvailableDistributorsPipeSeparated,
                date
        );
    }
}
