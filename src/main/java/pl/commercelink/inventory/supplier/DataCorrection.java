package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@RequiredArgsConstructor
class DataCorrection {

    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;

    InventoryItem run(InventoryItem item) {
        String brand = item.requiresBrandCorrection() ? correctBrand(item) : item.brand();
        String ean = item.requiresEanCorrection() ? correctEan(item, brand) : item.ean();
        return item.withBrandAndEan(brand, ean);
    }

    Taxonomy run(SupplierProduct product, InventoryItem offer) {
        String brand = offer.brand();
        String name = product.name();
        String category = null;
        int score = product.dataAccuracyScore();
        Integer netWeight = product.netWeightInGrams();
        Integer grossWeight = product.grossWeightInGrams();
        String categoryId = null;

        Optional<PimEntry> pim = resolveFromPim(offer);
        if (pim.isPresent()) {
            PimEntry entry = pim.get();
            if (isNotBlank(entry.brand())) brand = entry.brand();
            if (isNotBlank(entry.name())) name = entry.name();
            if (isNotBlank(entry.category())) category = entry.category();
            if (isNotBlank(entry.categoryId())) categoryId = entry.categoryId();
            if (entry.netWeightInGrams() != null) netWeight = entry.netWeightInGrams();
            if (entry.grossWeightInGrams() != null) grossWeight = entry.grossWeightInGrams();
            score = 0;
        }

        if (category == null && Taxonomy.SERVICES.equalsIgnoreCase(product.rawCategory())) {
            category = Taxonomy.SERVICES;
        }

        return new Taxonomy(offer.ean(), offer.mfn(),
                brand, name, category, score, netWeight, grossWeight,
                product.rawCategory(), categoryId);
    }

    private String correctEan(InventoryItem item, String brand) {
        return pimCatalog.withGlobalData().findByMpnAndBrand(item.mfn(), brand)
                .flatMap(PimEntry::primaryGtin)
                .map(UnifiedProductIdentifiers::unifyEan)
                .orElse(item.ean());
    }

    private String correctBrand(InventoryItem item) {
        if (isNotBlank(item.brand())) {
            return brandMapper.unifyBrand(item.brand());
        }
        if (item.requiresEanCorrection()) {
            return null;
        }
        return pimCatalog.withGlobalData().findByGtin(item.ean())
                .filter(PimEntry::approved)
                .map(PimEntry::brand)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
    }

    private Optional<PimEntry> resolveFromPim(InventoryItem offer) {
        return pimCatalog.withGlobalData()
                .findByGtinOrMpnAndBrand(offer.ean(), offer.mfn(), offer.brand())
                .filter(PimEntry::approved);
    }
}
