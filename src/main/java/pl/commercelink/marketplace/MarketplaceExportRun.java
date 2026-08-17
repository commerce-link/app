package pl.commercelink.marketplace;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class MarketplaceExportRun {

    private final String storeId;
    private final String marketplace;
    private final String catalogId;
    private final String pricelistId;

    private final List<MarketplaceExportExcludedItem> excluded = new ArrayList<>();
    private final Map<String, String> quantityZeroedReasonByPimId = new HashMap<>();

    private List<MarketplaceOfferSnapshot> offers = List.of();
    private List<String> failure;
    private boolean providerCalled;

    public String getStoreId() {
        return storeId;
    }

    public String getMarketplace() {
        return marketplace;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public void excludeCategory(CategoryDefinition category, MarketplaceExportSkipReason reason) {
        excluded.add(MarketplaceExportExcludedItem.category(category, reason));
    }

    public void excludeProduct(CategoryDefinition category,
                               Product product,
                               MarketplaceExportSkipReason reason,
                               MarketplaceExportThresholds detail) {
        excluded.add(MarketplaceExportExcludedItem.product(category, product, reason, detail));
        if (reason.isQuantityZeroed()) {
            quantityZeroedReasonByPimId.put(product.getPimId(), reason.name());
        }
    }

    public String quantityZeroedReason(String pimId) {
        return quantityZeroedReasonByPimId.get(pimId);
    }

    public void providerCalled(boolean called) {
        this.providerCalled = called;
    }

    public void failed(Throwable throwable) {
        this.failure = List.of(ExceptionUtils.getStackTrace(throwable).split("\\R"));
    }

    public void offers(List<MarketplaceOfferSnapshot> offers) {
        this.offers = offers;
    }

    public MarketplaceExportRunDocument toDocument(String runId, Instant finishedAt) {
        return new MarketplaceExportRunDocument(
                MarketplaceExportRunDocument.CURRENT_SCHEMA_VERSION,
                runId,
                finishedAt,
                failure == null
                        ? MarketplaceExportRunDocument.STATUS_SUCCEEDED
                        : MarketplaceExportRunDocument.STATUS_FAILED,
                failure,
                storeId,
                marketplace,
                catalogId,
                pricelistId,
                providerCalled,
                offers,
                List.copyOf(excluded));
    }
}
