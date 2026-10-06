package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductCategoryViewFactory {

    private final TaxonomyCache taxonomyCache;
    private final PimCategoryTree tree;
    private final CatalogPlacement catalogPlacement;

    public Optional<ProductCategoryView> build(@Nullable String storeId, ProductHeader product, boolean admin) {
        InventoryKey key = new InventoryKey(product.ean(), product.mfn());
        Taxonomy taxonomy = taxonomyCache.find(key);
        if (!TaxonomyCache.hasCategory(taxonomy)) {
            return Optional.empty();
        }
        boolean withCatalog = admin && storeId != null;
        CatalogPlacement.StorePlacement placement = withCatalog ? catalogPlacement.forStore(storeId) : null;
        CategoryLine line = CategoryLine.of(tree, placement, taxonomy.categoryId(), taxonomy.category(), key, false);
        String ean = product.ean();
        String addHref = BrowseQuery.start().hrefWith("open=add&ean=" + URLEncoder.encode(ean == null ? "" : ean, StandardCharsets.UTF_8));
        return Optional.of(new ProductCategoryView(line, ean, withCatalog && ean != null, addHref));
    }
}
