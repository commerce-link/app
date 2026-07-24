package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierProduct;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.taxonomy.Taxonomy;

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

    Taxonomy run(SupplierProduct product) {
        String ean = resolveCorrectEanForMfn(product.ean(), product.mfn()).orElse(product.ean());
        String brand = brandMapper.unifyBrand(product.brand());
        String name = product.name();
        String category = null;
        int score = product.dataAccuracyScore();
        Integer netWeight = product.netWeightInGrams();
        Integer grossWeight = product.grossWeightInGrams();
        String categoryId = null;

        Optional<PimEntry> pim = resolveFromPim(ean, product.mfn());
        if (pim.isPresent()) {
            PimEntry entry = pim.get();
            if (isNotBlank(entry.brand())) brand = brandMapper.unifyBrand(entry.brand());
            if (isNotBlank(entry.name())) name = entry.name();
            if (isNotBlank(entry.category())) category = entry.category();
            if (entry.categoryId() != null) categoryId = entry.categoryId();
            if (entry.netWeightInGrams() != null) netWeight = entry.netWeightInGrams();
            if (entry.grossWeightInGrams() != null) grossWeight = entry.grossWeightInGrams();
            score = 0;
        }

        if (category == null && Taxonomy.SERVICES.equalsIgnoreCase(product.rawCategory())) {
            category = Taxonomy.SERVICES;
        }

        return new Taxonomy(ean, product.mfn(), brand, name, category, score, netWeight, grossWeight,
                product.rawCategory(), categoryId);
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
