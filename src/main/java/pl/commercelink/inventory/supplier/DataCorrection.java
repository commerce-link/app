package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@RequiredArgsConstructor
class DataCorrection {

    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;

    InventoryItem run(InventoryItem inventoryItem) {
        return resolveCorrectEanForMfn(inventoryItem.ean(), inventoryItem.mfn())
                .map(inventoryItem::withEan)
                .orElse(inventoryItem);
    }

    Taxonomy run(Taxonomy taxonomy) {
        String ean = resolveCorrectEanForMfn(taxonomy.ean(), taxonomy.mfn()).orElse(taxonomy.ean());
        String brand = brandMapper.unifyBrand(taxonomy.brand());
        String name = taxonomy.name();
        ProductCategory category = taxonomy.category();
        String categoryKey = taxonomy.categoryKey();
        int score = taxonomy.dataAccuracyScore();
        Integer netWeight = taxonomy.netWeightInGrams();
        Integer grossWeight = taxonomy.grossWeightInGrams();

        Optional<PimEntry> pim = resolveFromPim(ean, taxonomy.mfn());
        if (pim.isPresent()) {
            PimEntry entry = pim.get();
            if (isNotBlank(entry.brand())) brand = brandMapper.unifyBrand(entry.brand());
            if (isNotBlank(entry.name())) name = entry.name();
            if (isNotBlank(entry.categoryKey()) && !"Other".equals(entry.categoryKey())) {
                categoryKey = entry.categoryKey();
                category = toEnumOrKeep(entry.categoryKey(), category);
            }
            if (entry.netWeightInGrams() != null) netWeight = entry.netWeightInGrams();
            if (entry.grossWeightInGrams() != null) grossWeight = entry.grossWeightInGrams();
            score = 0;
        }

        return new Taxonomy(ean, taxonomy.mfn(), brand, name, category, score, netWeight, grossWeight, categoryKey, taxonomy.signals());
    }

    private static ProductCategory toEnumOrKeep(String categoryKey, ProductCategory current) {
        try {
            return ProductCategory.valueOf(categoryKey);
        } catch (IllegalArgumentException e) {
            return current;
        }
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
