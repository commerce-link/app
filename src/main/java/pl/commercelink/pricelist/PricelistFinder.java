package pl.commercelink.pricelist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PricelistFinder {

    private final PricelistRepository pricelistRepository;

    public List<AvailabilityAndPrice> availabilityAndPrices(String storeId, String catalogId) {
        Pricelist pricelist = newestPricelist(storeId, catalogId);
        return pricelist == null ? List.of() : pricelist.getAvailabilityAndPrices();
    }

    public Optional<AvailabilityAndPrice> findByPimId(String storeId, String catalogId, String pimId) {
        Pricelist pricelist = newestPricelist(storeId, catalogId);
        return pricelist == null ? Optional.empty() : pricelist.findByPimId(pimId);
    }

    private Pricelist newestPricelist(String storeId, String catalogId) {
        String pricelistId = pricelistRepository.findNewestPricelistIdCached(storeId, catalogId);
        return pricelistId == null ? null : pricelistRepository.find(storeId, catalogId, pricelistId);
    }
}
