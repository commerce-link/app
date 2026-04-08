package pl.commercelink.pricelist;

import pl.commercelink.taxonomy.ProductCategory;

import java.util.LinkedList;
import java.util.List;
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

    public Optional<AvailabilityAndPrice> findByCategoryLabelAndName(ProductCategory category, String label, String name) {
        return availabilityAndPrices.stream()
                .filter(a -> a.getCategory() == category)
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

    public List<ProductCategory> getAvailableCategories() {
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
