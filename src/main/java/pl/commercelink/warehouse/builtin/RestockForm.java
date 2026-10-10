package pl.commercelink.warehouse.builtin;

import pl.commercelink.products.ProductCatalog;

import java.util.List;
import java.util.Map;

public record RestockForm(List<ProductCatalog> catalogs,
                          Map<String, List<Map<String, String>>> categoriesByCatalog,
                          String selectedCatalogId,
                          String error) {
}
