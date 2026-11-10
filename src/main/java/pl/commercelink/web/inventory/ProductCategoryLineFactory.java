package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.products.ProductDataResolver;

import java.util.Optional;

/**
 * The PIM category of a product found by code, drawn like the "Kategoria w PIM" cell of the browse list. The prices and
 * availability page shows no catalog state and offers no add action, so the line never carries catalog places.
 */
@Component
@RequiredArgsConstructor
public class ProductCategoryLineFactory {

    private final ProductDataResolver productDataResolver;
    private final PimCategoryTree tree;

    public Optional<CategoryLine> build(String storeId, ProductHeader product) {
        return productDataResolver.ofEan(storeId, product.ean())
                .filter(found -> StringUtils.isNotBlank(found.category()))
                .map(found -> CategoryLine.of(tree, found.categoryId(), found.category(), false));
    }
}
