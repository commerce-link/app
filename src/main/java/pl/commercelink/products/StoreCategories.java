package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.taxonomy.Categories;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StoreCategories {

    private final ProductCatalogRepository productCatalogRepository;

    public record Group(String catalog, List<String> names) {
    }

    public List<String> namesFor(String storeId) {
        return names(productCatalogRepository.findAll(storeId));
    }

    public List<String> names(List<ProductCatalog> catalogs) {
        return groups(catalogs).stream()
                .flatMap(group -> group.names().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Group> groupsFor(String storeId) {
        return groups(productCatalogRepository.findAll(storeId));
    }

    public List<Group> groups(List<ProductCatalog> catalogs) {
        List<Group> groups = catalogs.stream()
                .sorted(Comparator.comparing(ProductCatalog::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(catalog -> new Group(catalog.getName(), catalog.getCategories().stream()
                        .sorted(Comparator.comparing(CategoryDefinition::getSequenceNumber))
                        .map(CategoryDefinition::getName)
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .collect(Collectors.toList())))
                .filter(group -> !group.names().isEmpty())
                .collect(Collectors.toList());
        if (groups.stream().noneMatch(group -> group.names().contains(Categories.UNCATEGORIZED)))
            groups.add(new Group(Categories.UNCATEGORIZED, List.of(Categories.UNCATEGORIZED)));
        return groups;
    }
}
