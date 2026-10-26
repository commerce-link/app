package pl.commercelink.pricelist;

import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Pricelist {

    private String pricelistId ;
    private String lastModified;
    private List<AvailabilityAndPrice> availabilityAndPrices;

    public Pricelist(String pricelistId, String lastModified ) {
        this.lastModified = lastModified;
        this.pricelistId  = pricelistId ;
    }

    public Pricelist(String pricelistId, List<AvailabilityAndPrice> availabilityAndPrices ) {
        this.availabilityAndPrices = availabilityAndPrices;
        this.pricelistId  = pricelistId ;
    }

    public Optional<AvailabilityAndPrice> findByPimId(String pimId) {
        return availabilityAndPrices.stream()
                .filter(availabilityAndPrice -> availabilityAndPrice.getPimId().equals(pimId))
                .findFirst();
    }

    /**
     * The row of the product with the given EAN and manufacturer code, for a pricelist whose rows carry another pimId
     * than the product now has (built before a clone was deleted, so its rows still carry the clone's id).
     *
     * <p>The strictest rule the data supports: BOTH codes must be present on the product and on the row and equal in
     * their unified form. An EAN alone is shared by different products (bundles, regional variants) and an MFN alone
     * by different brands, and a product deliberately absent from the pricelist must not take another row's price.
     * Rows whose pimId is in {@code excludedPimIds} (other products of the same export, which have their own row) are
     * never taken. The first matching row in pricelist order wins.
     */
    public Optional<AvailabilityAndPrice> findByCodes(String ean, String mfn, Set<String> excludedPimIds) {
        String wantedEan = UnifiedProductIdentifiers.unifyEan(ean);
        String wantedMfn = UnifiedProductIdentifiers.unifyMfn(mfn);
        if (isBlank(wantedEan) || isBlank(wantedMfn)) {
            return Optional.empty();
        }
        return availabilityAndPrices.stream()
                .filter(row -> excludedPimIds == null || !excludedPimIds.contains(row.getPimId()))
                .filter(row -> wantedEan.equals(UnifiedProductIdentifiers.unifyEan(row.getEan()))
                        && wantedMfn.equals(UnifiedProductIdentifiers.unifyMfn(row.getManufacturerCode())))
                .findFirst();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public Optional<AvailabilityAndPrice> findByCategoryLabelAndName(String category, String label, String name) {
        return availabilityAndPrices.stream()
                .filter(a -> Objects.equals(a.getCategory(), category))
                .filter(a -> a.getLabel().equals(label))
                .filter(a -> a.getName().equals(name))
                .findFirst();
    }

    public String getPricelistId() {
        return pricelistId;
    }

    public List<AvailabilityAndPrice> getAvailabilityAndPrices() {
        return availabilityAndPrices;
    }

    public List<String> getAvailableCategories() {
        return availabilityAndPrices.stream()
                .map(AvailabilityAndPrice::getCategory)
                .distinct()
                .collect(Collectors.toList());
    }

    public String getLastModified() {
        return lastModified;
    }

    public static Pricelist empty() {
        return new Pricelist("", new LinkedList<>());
    }

}
