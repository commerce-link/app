package pl.commercelink.pricelist;

import pl.commercelink.externaloffers.OfferEans;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Pricelist {

    private String pricelistId ;
    private String lastModified;
    private List<AvailabilityAndPrice> availabilityAndPrices;
    private final Map<String, AvailabilityAndPrice> byPimId = new HashMap<>();
    private final Map<String, AvailabilityAndPrice> byEan = new HashMap<>();

    public Pricelist(String pricelistId, String lastModified ) {
        this.lastModified = lastModified;
        this.pricelistId  = pricelistId ;
    }

    public Pricelist(String pricelistId, List<AvailabilityAndPrice> availabilityAndPrices ) {
        this.availabilityAndPrices = availabilityAndPrices;
        this.pricelistId  = pricelistId ;
        for (AvailabilityAndPrice row : availabilityAndPrices) {
            if (row.getPimId() != null) {
                byPimId.putIfAbsent(row.getPimId(), row);
            }
            String ean = OfferEans.unify(row.getEan());
            if (ean != null) {
                byEan.putIfAbsent(ean, row);
            }
        }
    }

    public Optional<AvailabilityAndPrice> findByPimId(String pimId) {
        return Optional.ofNullable(byPimId.get(pimId));
    }

    public Optional<AvailabilityAndPrice> findByEan(String ean) {
        return Optional.ofNullable(byEan.get(OfferEans.unify(ean)));
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
