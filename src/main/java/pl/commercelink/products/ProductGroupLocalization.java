package pl.commercelink.products;

import pl.commercelink.starter.localization.EnumMessageResolver;
import pl.commercelink.taxonomy.ProductGroup;

public class ProductGroupLocalization {

    public static final ProductGroupLocalization INSTANCE = new ProductGroupLocalization();

    public String name(ProductGroup g) {
        return EnumMessageResolver.get("product.group." + g.name());
    }
}
