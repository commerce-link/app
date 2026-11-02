package pl.commercelink.inventory.supplier;

import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.FeedItem;
import pl.commercelink.inventory.supplier.api.SupplierInfo;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record ParsedRow(InventoryItem item, SupplierProduct product) {

    private static final Set<String> MISSING_BRAND_PLACEHOLDERS = Set.of(
            "no name", "noname", "no-name", "oem", "inne", "inny", "brak", "sonstige", "diverse", "mix",
            "unknown", "other", "n/a", "none", "-");

    public static ParsedRow of(FeedItem feedItem, SupplierInfo supplierInfo) {
        String brand = brandOf(feedItem.getBrand());
        var item = new InventoryItem(brand, feedItem.getSku(), feedItem.getEan(), feedItem.getMfn(), supplierInfo.name(),
                feedItem.getNetPrice(), feedItem.getCurrency(), feedItem.getQty(), feedItem.getLeadTimeDays(),
                feedItem.isSellable(), feedItem.isInStock(), feedItem.isInDelivery());
        var product = new SupplierProduct(feedItem.getEan(), feedItem.getMfn(), brand, feedItem.getName(),
                supplierInfo.accuracyScore(),
                feedItem.getNetWeightInGrams(), feedItem.getGrossWeightInGrams(),
                feedItem.getCategory());
        return new ParsedRow(item, product);
    }

    public static boolean hasQty(FeedItem feedItem) {
        return feedItem.getQty() > 0;
    }

    // A value may fail in the adapter's parse or only in a getter, so both are guarded together.
    public static Optional<ParsedRow> tryParse(CsvRowParser parser, String[] row, SupplierInfo supplierInfo) {
        try {
            FeedItem feedItem = parser.parse(row);
            return feedItem == null || !hasQty(feedItem) ? Optional.empty() : Optional.of(of(feedItem, supplierInfo));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String brandOf(String brand) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        return MISSING_BRAND_PLACEHOLDERS.contains(brand.trim().toLowerCase(Locale.ROOT)) ? null : brand;
    }
}
