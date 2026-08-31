package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StoreCategoryResolver {

    private final ProductCatalogRepository productCatalogRepository;

    public Optional<String> findCategoryName(String storeId, String pimCategoryId) {
        if (StringUtils.isBlank(storeId) || StringUtils.isBlank(pimCategoryId))
            return Optional.empty();

        return Optional.ofNullable(categoryNamesByPimCategoryId(storeId).get(pimCategoryId));
    }

    private Map<String, String> categoryNamesByPimCategoryId(String storeId) {
        Map<String, String> names = new HashMap<>();
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(storeId);
        
        catalogs.stream()
                .sorted(Comparator.comparing(ProductCatalog::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .flatMap(catalog -> catalog.getCategories().stream()
                        .sorted(Comparator.comparing(CategoryDefinition::getSequenceNumber)))
                .filter(definition -> StringUtils.isNotBlank(definition.getName()))
                .forEach(definition -> definition.getPimCategoryIds()
                        .forEach(pimCategoryId -> names.putIfAbsent(pimCategoryId, definition.getName())));

        return names;
    }
}
