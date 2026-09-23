package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.TaxonomyCatalog;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TechnicalInventoryViewFactory {

    private final Inventory inventory;
    private final TaxonomyCatalog taxonomyCatalog;
    private final TaxonomyRepository taxonomyRepository;
    private final PimCatalog pimCatalog;

    public TechnicalInventoryView build(LocalDateTime now) {
        List<GlobalFeedRow> feeds = inventory.getMatchedSuppliers().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(supplier -> {
                    LocalDateTime loadedAt = inventory.getLastUpdateDate(supplier);
                    return new GlobalFeedRow(supplier, loadedAt, RelativeTime.between(loadedAt, now));
                })
                .toList();
        return new TechnicalInventoryView(inventory.size(), taxonomyCatalog.approximateSize(), taxonomyRepository.newestFileName(),
                pimCatalog.findAll().size(), feeds);
    }
}
