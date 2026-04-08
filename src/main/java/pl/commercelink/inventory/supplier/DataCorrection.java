package pl.commercelink.inventory.supplier;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.BrandMapper;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
class DataCorrection {

    private final PimCatalog pimCatalog;

    DataCorrection(PimCatalog pimCatalog) {
        this.pimCatalog = pimCatalog;
    }

    InventoryItem run(InventoryItem inventoryItem) {
        return resolveCorrectEanForMfn(inventoryItem.ean(), inventoryItem.mfn())
                .map(inventoryItem::withEan)
                .orElse(inventoryItem);
    }

    Taxonomy run(Taxonomy taxonomy) {
        String ean = resolveCorrectEanForMfn(taxonomy.ean(), taxonomy.mfn()).orElse(taxonomy.ean());
        String brand = BrandMapper.unifyBrand(taxonomy.brand());
        String name = taxonomy.name();
        ProductCategory category = taxonomy.category();
        int score = taxonomy.dataAccuracyScore();

        Optional<PimEntry> pim = resolveFromPim(ean, taxonomy.mfn());
        if (pim.isPresent()) {
            PimEntry entry = pim.get();
            if (isNotBlank(entry.brand())) brand = BrandMapper.unifyBrand(entry.brand());
            if (isNotBlank(entry.name())) name = entry.name();
            if (entry.category() != null && entry.category() != ProductCategory.Other) category = entry.category();
            score = 0;
        }

        return new Taxonomy(ean, taxonomy.mfn(), brand, name, category, score);
    }

    Optional<String> resolveCorrectEanForMfn(String ean, String mfn) {
        if (!requiresEanCorrection(ean)) {
            return Optional.empty();
        }

        return pimCatalog.findByMpn(mfn)
                .map(PimEntry::gtins)
                .flatMap(gtins -> gtins.stream().findFirst());
    }

    Optional<PimEntry> resolveFromPim(String ean, String mfn) {
        return pimCatalog.findByGtinOrMpn(ean, mfn).filter(PimEntry::approved);
    }

    private boolean requiresEanCorrection(String ean) {
        return StringUtils.isBlank(ean) || "1111111111111".equalsIgnoreCase(ean);
    }
}
