package pl.commercelink.taxonomy.mapping;

import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import pl.commercelink.taxonomy.TaxonomyCategoryMatchProperties;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class CategoryMappingCache {

    public record ActiveMapping(String categoryId, String categoryName) {}

    private final CategoryMappingRepository repository;
    private final TaxonomyCategoryMatchProperties properties;
    private final ConcurrentHashMap<String, ActiveMapping> activeByKey = new ConcurrentHashMap<>();

    CategoryMappingCache(CategoryMappingRepository repository, TaxonomyCategoryMatchProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @PostConstruct
    void onStartUp() {
        try {
            repository.findAll().stream()
                    .filter(CategoryMapping::isActive)
                    .forEach(mapping -> activeByKey.put(key(mapping.getSupplier(), mapping.getRawCategory()),
                            new ActiveMapping(mapping.getActiveCategoryId(), mapping.getActiveCategoryName())));
            System.out.println("Loaded " + activeByKey.size() + " active category mappings");
        } catch (ResourceNotFoundException e) {
            System.out.println("Category mappings table not found, starting empty: " + e.getMessage());
        }
    }

    public Optional<ActiveMapping> findActive(String supplier, String rawCategory) {
        if (isBlank(supplier) || isBlank(rawCategory)) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeByKey.get(key(supplier, normalize(rawCategory))));
    }

    public synchronized void recordSample(String supplier, String rawCategory, String categoryId, String categoryName) {
        if (isBlank(supplier) || isBlank(rawCategory) || isBlank(categoryId)) {
            return;
        }
        String rawKey = normalize(rawCategory);
        try {
            CategoryMapping mapping = repository.find(supplier, rawKey);
            if (mapping == null) {
                mapping = CategoryMapping.learning(supplier, rawKey, rawCategory.trim());
            }
            if (!mapping.recordSample(categoryId, categoryName,
                    properties.mappingMinSamples(), properties.mappingMinShare())) {
                return;
            }
            repository.save(mapping);
            if (mapping.isActive()) {
                activeByKey.put(key(supplier, rawKey),
                        new ActiveMapping(mapping.getActiveCategoryId(), mapping.getActiveCategoryName()));
            } else {
                activeByKey.remove(key(supplier, rawKey));
            }
        } catch (Exception e) {
            System.out.println("Category mapping sample skipped: " + e.getMessage());
        }
    }

    private static String key(String supplier, String rawCategoryKey) {
        return supplier + "|" + rawCategoryKey;
    }

    private static String normalize(String rawCategory) {
        return rawCategory.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
