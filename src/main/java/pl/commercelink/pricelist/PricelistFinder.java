package pl.commercelink.pricelist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.products.information.StorePimAliases;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PricelistFinder {

    private final PricelistRepository pricelistRepository;
    private final StorePimAliases aliases;

    public List<AvailabilityAndPrice> availabilityAndPrices(String storeId, String catalogId) {
        return newestPricelist(storeId, catalogId).getAvailabilityAndPrices();
    }

    public Optional<AvailabilityAndPrice> findByPimId(String storeId, String catalogId, String pimId) {
        return aliases.find(storeId, newestPricelist(storeId, catalogId), pimId);
    }

    public Pricelist newestPricelist(String storeId, String catalogId) {
        String pricelistId = pricelistRepository.findNewestPricelistIdCached(storeId, catalogId);
        Pricelist pricelist = pricelistId == null ? null : pricelistRepository.find(storeId, catalogId, pricelistId);
        return pricelist == null ? Pricelist.empty() : pricelist;
    }
}
