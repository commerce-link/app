package pl.commercelink.pricelist;

import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
     * than the product now has (built before a clone was deleted, so its rows still carry the clone's id). Codes are
     * compared in their unified form. Every code present on both sides has to match and at least one has to be present
     * on both, so a product is never matched on a missing code and a shared EAN alone does not join two products that
     * differ in manufacturer code.
     */
    public Optional<AvailabilityAndPrice> findByCodes(String ean, String mfn) {
        String wantedEan = UnifiedProductIdentifiers.unifyEan(ean);
        String wantedMfn = UnifiedProductIdentifiers.unifyMfn(mfn);
        return availabilityAndPrices.stream()
                .filter(row -> codesMatch(wantedEan, wantedMfn, row))
                .findFirst();
    }

    private static boolean codesMatch(String wantedEan, String wantedMfn, AvailabilityAndPrice row) {
        String rowEan = UnifiedProductIdentifiers.unifyEan(row.getEan());
        String rowMfn = UnifiedProductIdentifiers.unifyMfn(row.getManufacturerCode());
        boolean compareEan = !isBlank(wantedEan) && !isBlank(rowEan);
        boolean compareMfn = !isBlank(wantedMfn) && !isBlank(rowMfn);
        if (!compareEan && !compareMfn) {
            return false;
        }
        return (!compareEan || wantedEan.equals(rowEan)) && (!compareMfn || wantedMfn.equals(rowMfn));
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
