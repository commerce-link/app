package pl.commercelink.marketplace;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class MarketplaceExportRun {

    @Getter
    private final String storeId;
    @Getter
    private final String marketplace;
    @Getter
    private final String catalogId;

    private final Map<String, MarketplaceOfferRejection> rejectionsByPimId = new LinkedHashMap<>();

    private List<MarketplaceOfferSnapshot> offers = List.of();
    private String failure;

    public void offers(List<MarketplaceOfferSnapshot> offers) {
        this.offers = offers;
    }

    public void rejected(String productId, String reasonCode, String message) {
        rejectionsByPimId.put(productId, new MarketplaceOfferRejection(reasonCode, message));
    }

    public void failed(Throwable throwable) {
        this.failure = throwable.toString();
    }

    public boolean isFailed() {
        return failure != null;
    }

    public List<MarketplaceOfferSnapshot> toRows() {
        List<MarketplaceOfferSnapshot> rows = new ArrayList<>();
        Set<String> snapshotPimIds = new HashSet<>();
        for (MarketplaceOfferSnapshot offer : offers) {
            snapshotPimIds.add(offer.pimId());
            MarketplaceOfferRejection rejection = rejectionsByPimId.get(offer.pimId());
            rows.add(rejection == null ? offer : offer.rejected(rejection.reasonCode(), rejection.message()));
        }
        for (Map.Entry<String, MarketplaceOfferRejection> rejection : rejectionsByPimId.entrySet()) {
            if (!snapshotPimIds.contains(rejection.getKey())) {
                rows.add(MarketplaceOfferSnapshot.rejectedWithoutOffer(
                        rejection.getKey(), rejection.getValue().reasonCode(), rejection.getValue().message()));
            }
        }
        if (isFailed()) {
            rows.add(MarketplaceOfferSnapshot.exportAborted(failure));
        }
        return rows;
    }
}
