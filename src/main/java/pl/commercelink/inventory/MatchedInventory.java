package pl.commercelink.inventory;

import jakarta.annotation.Nullable;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.*;
import java.util.stream.Collectors;

/**
 * It is crucial to make sure that we only add new InventoryItems while modifying key associations
 */
public class MatchedInventory {

    public static final double TOLERANCE = 1.05;

    private InventoryKey key;
    private Collection<InventoryItem> inventoryItems = new LinkedHashSet<>();
    private TaxonomyCache taxonomyCache;
    private SupplierRegistry supplierRegistry;

    public MatchedInventory(InventoryKey key, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.key = key;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    public MatchedInventory(InventoryKey key, InventoryItem inventoryItem, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.key = key;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
        addAlternativeInventoryItem(inventoryItem);
    }

    public MatchedInventory(InventoryKey key, Collection<InventoryItem> inventoryItems, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.key = key;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
        addAlternativeInventoryItems(inventoryItems);
    }

    public boolean matches(InventoryKey otherKey) {
        return key != null && key.matches(otherKey);
    }

    public boolean matchedByMfn(InventoryKey otherKey) {
        return key != null && key.hasAnyProductCode(otherKey.getProductCodes());
    }

    public boolean isEmpty() {
        return inventoryItems.isEmpty();
    }

    public InventoryKey getInventoryKey() {
        return key;
    }

    public Collection<String> getSuppliers() {
        return getInventoryItems().stream().map(InventoryItem::supplier).collect(Collectors.toSet());
    }

    public List<InventoryItem> getInventoryItems() {
        return getInventoryItems(null);
    }

    public List<InventoryItem> getInventoryItems(@Nullable SupplierType supplierType) {
        return inventoryItems.stream()
                .filter(i -> supplierType == null || supplierRegistry.get(i.supplier()).type() == supplierType)
                .sorted(Comparator.comparing(InventoryItem::netPrice))
                .collect(Collectors.toList());
    }

    public List<InventoryItem> getInventoryItemsFromSupplier(String supplierName) {
        return inventoryItems.stream()
                .filter(i -> i.supplier().equalsIgnoreCase(supplierName))
                .collect(Collectors.toList());
    }

    public boolean hasTotalMinQty(int minQty) {
        return inventoryItems.stream().mapToInt(InventoryItem::qty).sum() >= minQty;
    }

    public boolean hasAnyOffers() {
        return !inventoryItems.isEmpty();
    }

    public boolean hasOffersFromMultipleSuppliers(int minSuppliersCount) {
        return inventoryItems.stream().map(InventoryItem::supplier).distinct().count() >= minSuppliersCount;
    }

    public boolean hasOffersFromMultipleSuppliers(int minSuppliersCount, int minQtyPerSupplier) {
        long count = inventoryItems.stream()
                .collect(Collectors.groupingBy(InventoryItem::supplier, Collectors.summingLong(InventoryItem::qty)))
                .values()
                .stream()
                .filter(totalQty -> totalQty >= minQtyPerSupplier)
                .count();
        return count >= minSuppliersCount;
    }

    public Price getLowestPrice() {
        return getLowestPrice(false, null);
    }

    public Price getLowestPrice(SupplierType supplierType) {
        return getLowestPrice(false, supplierType);
    }

    public Price getLowestPrice(boolean skipLowestPriceIfFromForeignSupplier, @Nullable SupplierType supplierType) {
        List<InventoryItem> items = getInventoryItems(supplierType).stream()
                .filter(i -> i.netPrice() > 0)
                .collect(Collectors.toList());
        if (items.isEmpty()) {
            return Price.fromNet(0);
        }

        Iterator<InventoryItem> iterator = items.iterator();
        InventoryItem lowestPricedInventoryItem = iterator.next();

        double lowestNetPrice = lowestPricedInventoryItem.netPrice();

        if (items.size() > 1 && !SupplierRegistry.WAREHOUSE.equalsIgnoreCase(lowestPricedInventoryItem.supplier())) {
            // skip the lowest priced item if it's qty is 1 as it typically indicates as broken item
            if (lowestPricedInventoryItem.qty() == 1) {
                return Price.fromNet(iterator.next().netPrice());
            }

            // skip the lowest priced item if it's from foreign supplier while we have local suppliers with reasonable price
            if (skipLowestPriceIfFromForeignSupplier && !supplierRegistry.get(lowestPricedInventoryItem.supplier()).isLocalFor("PL")) {

                // analyse price to see if the difference is significant
                double secondLowestNetPrice = iterator.next().netPrice();
                double priceDifferencePercent = (secondLowestNetPrice - lowestNetPrice) / lowestNetPrice * 100.0;

                if (lowestNetPrice >= 10000 && priceDifferencePercent <= 3.0) {
                    lowestNetPrice = secondLowestNetPrice;
                } else if (lowestNetPrice >= 5000 && priceDifferencePercent <= 5.0) {
                    lowestNetPrice = secondLowestNetPrice;
                } else if (lowestNetPrice >= 2500 && priceDifferencePercent <= 7.5) {
                    lowestNetPrice = secondLowestNetPrice;
                } else if (lowestNetPrice >= 1000 && priceDifferencePercent <= 10.0) {
                    lowestNetPrice = secondLowestNetPrice;
                } else if (priceDifferencePercent <= 15.0) {
                    lowestNetPrice = secondLowestNetPrice;
                }
            }
        }

        return Price.fromNet(lowestNetPrice);
    }

    public Price getMedianPrice() {
        if (hasOffersFromMultipleSuppliers(2)) {
            List<InventoryItem> items = getInventoryItems().stream()
                    .filter(i -> i.netPrice() > 0)
                    .collect(Collectors.toList());

            double priceNet;

            int size = items.size();
            if (size % 2 == 0) {
                priceNet = (items.get(size/2 - 1).netPrice() + items.get(size/2).netPrice()) / 2.0;
            } else {
                priceNet = items.get(size/2).netPrice();
            }

            return Price.max(Price.fromNet(priceNet), getLowestPrice());
        } else {
            return getLowestPrice();
        }
    }

    public List<String> getMedianPriceSuppliers() {
        Price medianPrice = getMedianPrice();
        return getInventoryItems().stream()
                .filter(i -> i.netPrice() <= medianPrice.netValue())
                .map(InventoryItem::supplier)
                .distinct()
                .collect(Collectors.toList());
    }

    public int getMedianAvailableQty() {
        Price medianPrice = getMedianPrice();
        return (int) getInventoryItems().stream()
                .filter(i -> i.netPrice() <= medianPrice.netValue())
                .mapToLong(InventoryItem::qty)
                .sum();
    }

    public Price getHighestPrice() {
        return getInventoryItems().stream()
                .max(Comparator.comparing(InventoryItem::netPrice))
                .map(i -> Price.fromNet(i.netPrice()))
                .orElse(Price.fromNet(0));
    }

    public InventoryItem getLowestPricedInventoryItem() {
        return inventoryItems.stream().min(Comparator.comparing(i -> i.netPrice())).orElse(null);
    }

    public long getTotalAvailableQty() {
        return inventoryItems.stream().mapToLong(InventoryItem::qty).sum();
    }

    public long getTotalAvailableQty(SupplierType supplierType) {
        return inventoryItems.stream()
                .filter(i -> supplierRegistry.get(i.supplier()).type() == supplierType)
                .mapToLong(InventoryItem::qty)
                .sum();
    }

    public long getTotalAvailableQty(long grossPrice) {
        return inventoryItems.stream()
                .filter(i -> Price.fromNet(i.netPrice()).grossValue() <= grossPrice)
                .mapToLong(InventoryItem::qty)
                .sum();
    }

    public long getNoOfSuppliersWithProduct(SupplierType supplierType) {
        return inventoryItems.stream()
                .map(InventoryItem::supplier)
                .filter(supplierName -> supplierRegistry.get(supplierName).type() == supplierType)
                .distinct()
                .count();
    }

    public boolean canBeFulfilledFromWarehouseAtPricePoint(double grossPrice) {
        return inventoryItems.stream()
                .filter(i -> Price.fromNet(i.netPrice()).grossValue() <= (grossPrice * TOLERANCE))
                .anyMatch(i -> SupplierRegistry.WAREHOUSE.equalsIgnoreCase(i.supplier()));
    }

    public boolean hasOffersFromLocalSuppliers() {
        return inventoryItems.stream().anyMatch(i -> supplierRegistry.get(i.supplier()).isLocalFor("PL"));
    }

    public boolean hasOffersFrom(String supplierName) {
        return inventoryItems.stream().anyMatch(i -> i.supplier().equalsIgnoreCase(supplierName) && i.netPrice() > 0);
    }

    public static MatchedInventory empty(InventoryKey inventoryKey) {
        return new MatchedInventory(inventoryKey, null, null);
    }

    void addAlternativeInventoryItems(Collection<? extends InventoryItem> items) {
        items.forEach(this::addAlternativeInventoryItem);
    }

    void addAlternativeInventoryItem(InventoryItem item) {
        key.addEan(item.ean());
        key.addManufacturerCode(item.mfn());
        inventoryItems.add(item);
    }

    public Taxonomy getTaxonomy() {
        return taxonomyCache.find(key);
    }

    public int size() {
        return inventoryItems.size();
    }

    public Collection<String> getEans(){
        return key.getProductEans();
    }

    public Collection<String> getMfnCodes(){
        return key.getProductCodes();
    }

    public int getEstimatedDeliveryDays() {
        return getInventoryItems()
                .stream()
                .mapToInt(i -> i.leadTimeDays() + supplierRegistry.get(i.supplier()).shippingTermsFor("PL").arrivalDays())
                .min()
                .orElse(1);
    }

    public int getEstimatedDeliveryDays(long grossPrice) {
        return getInventoryItems()
                .stream()
                .filter(i -> Price.fromNet(i.netPrice()).grossValue() <= grossPrice)
                .mapToInt(i -> i.leadTimeDays() + supplierRegistry.get(i.supplier()).shippingTermsFor("PL").arrivalDays())
                .min()
                .orElse(2);
    }
}
