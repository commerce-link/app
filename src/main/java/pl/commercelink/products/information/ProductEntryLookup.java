package pl.commercelink.products.information;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.brand.BrandMapper;

import java.util.Optional;

final class ProductEntryLookup {

    private ProductEntryLookup() {
    }

    static Optional<PimEntry> find(PimView view, Product product, BrandMapper brandMapper) {
        if (StringUtils.isBlank(product.getBrand())) {
            return view.findByGtin(product.getEan());
        }
        // pim-api compares brands without aliases, and PIM stores the unified name
        return view.findByGtinOrMpnAndBrand(product.getEan(), product.getManufacturerCode(),
                brandMapper.unifyBrand(product.getBrand()));
    }
}
