package pl.commercelink.inventory.supplier;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

public record InventoryItem(
        String brand,
        String sku,
        String ean,
        String mfn,
        String supplier,
        double netPrice,
        String currency,
        int qty,
        int leadTimeDays,
        boolean sellable,
        boolean inStock,
        boolean inDelivery
) {

    private static final String MISSING_EAN_PLACEHOLDER = "1111111111111";
    private static final Set<String> MISSING_MFN_PLACEHOLDERS = Set.of("0", "?", ".");

    public InventoryItem {
        ean = unifyEan(ean);
        mfn = unifyMfn(mfn);
    }

    public InventoryItem(String ean, String mfn, double netPrice, String currency, int qty, int leadTimeDays, String supplier) {
        this(null, null, ean, mfn, supplier, netPrice, currency, qty, leadTimeDays, true, false, false);
    }

    public InventoryItem(String ean, String mfn, double netPrice, String currency, int qty, int leadTimeDays, String supplier, boolean sellable) {
        this(null, null, ean, mfn, supplier, netPrice, currency, qty, leadTimeDays, sellable, false, false);
    }

    public InventoryItem(String ean, String mfn, double netPrice, String currency, int qty,
                         int leadTimeDays, String supplier, boolean sellable, boolean inStock, boolean inDelivery) {
        this(null, null, ean, mfn, supplier, netPrice, currency, qty, leadTimeDays, sellable, inStock, inDelivery);
    }

    public InventoryItem(String ean, String mfn, double netPrice, String currency, int qty,
                         int leadTimeDays, String supplier, boolean sellable, boolean inStock, boolean inDelivery,
                         String sku) {
        this(null, sku, ean, mfn, supplier, netPrice, currency, qty, leadTimeDays, sellable, inStock, inDelivery);
    }

    public InventoryItem withSupplier(String supplier) {
        return this.supplier.equals(supplier) ? this
                : new InventoryItem(brand, sku, ean, mfn, supplier, netPrice, currency, qty, leadTimeDays,
                        sellable, inStock, inDelivery);
    }

    public String uuid() {
        return supplier + "_" + ean + "_" + mfn;
    }

    public boolean hasQty() {
        return qty > 0;
    }

    public boolean hasEan() {
        return ean != null && !ean.isBlank() && !MISSING_EAN_PLACEHOLDER.equalsIgnoreCase(ean);
    }

    public boolean requiresEanCorrection() {
        return !hasEan();
    }

    public boolean requiresBrandCorrection() {
        return true;
    }

    public boolean hasMfn() {
        return mfn != null && !mfn.isBlank() && !MISSING_MFN_PLACEHOLDERS.contains(mfn);
    }

    public boolean hasSupplier(String other) {
        return supplier.equals(other);
    }

    public boolean isOfBrand(String other) {
        return brand != null && !brand.isBlank() && other != null && brand.trim().equalsIgnoreCase(other.trim());
    }

    public boolean isSellable() {
        return hasEan() && hasMfn() && hasQty() && sellable;
    }

    public Optional<InventoryItem> toLocalCurrency(String localCurrency, Double exchangeRate) {
        if (localCurrency.equals(currency)) return Optional.of(this);
        if (exchangeRate == null) return Optional.empty();
        return Optional.of(new InventoryItem(brand, sku, ean, mfn, supplier, Math.round(netPrice * exchangeRate), localCurrency,
                qty, leadTimeDays, sellable, inStock, inDelivery));
    }

    InventoryItem withBrandAndEan(String newBrand, String newEan) {
        if (Objects.equals(brand, newBrand) && Objects.equals(ean, newEan)) {
            return this;
        }
        return new InventoryItem(newBrand, sku, newEan, mfn, supplier, netPrice, currency, qty, leadTimeDays,
                sellable, inStock, inDelivery);
    }

}
