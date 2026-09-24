package pl.commercelink.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.web.catalog.CatalogPaths;

import java.util.Map;

/**
 * Bookmarks and links from before the catalog redesign. The seven product "views" of the old category menu map onto the
 * status and feature filters of the category page; the recommendations page became "Add products from inventory".
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class CatalogLegacyRedirects {

    private static final Map<String, String> VIEWS = Map.of(
            "Enabled", "status=active",
            "Disabled", "status=disabled",
            "Queued", "status=nopim",
            "MarketplaceEligible", "status=all&feature=marketplace",
            "ExpectedStock", "status=all&feature=stock",
            "SuggestedRetailPrice", "status=all&feature=srp",
            "MaxRetailPrice", "status=all&feature=mrp");

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products")
    public String products(@PathVariable String catalogId, @PathVariable String categoryId,
                           @RequestParam(required = false, defaultValue = "Enabled") String status) {
        return "redirect:" + CatalogPaths.category(catalogId, categoryId) + "?" + VIEWS.getOrDefault(status, "status=active");
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/recommendations")
    public String recommendations(@PathVariable String catalogId, @PathVariable String categoryId) {
        return "redirect:" + CatalogPaths.productsAdd(catalogId, categoryId);
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/bulk-new")
    public String bulkNew(@PathVariable String catalogId, @PathVariable String categoryId) {
        return "redirect:" + CatalogPaths.productsAdd(catalogId, categoryId);
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{ean:\\d{8,14}}/new")
    public String newProductFromEan(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String ean) {
        return "redirect:" + CatalogPaths.newProduct(catalogId, categoryId) + "?ean=" + ean;
    }
}
