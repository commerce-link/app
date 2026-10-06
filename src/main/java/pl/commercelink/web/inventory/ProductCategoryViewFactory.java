package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Optional;

/**
 * The PIM category of a product found by code, drawn like the "Kategoria w PIM" cell of the browse list. The prices and
 * availability page shows no catalog state and offers no add action, so the line never carries catalog places.
 */
@Component
@RequiredArgsConstructor
public class ProductCategoryViewFactory {

    private final TaxonomyCache taxonomyCache;
    private final PimCategoryTree tree;

    public Optional<CategoryLine> build(ProductHeader product) {
        InventoryKey key = new InventoryKey(product.ean(), product.mfn());
        Taxonomy taxonomy = taxonomyCache.find(key);
        if (!TaxonomyCache.hasCategory(taxonomy)) {
            return Optional.empty();
        }
        return Optional.of(CategoryLine.of(tree, null, taxonomy.categoryId(), taxonomy.category(), key, false));
    }
}
