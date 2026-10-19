package pl.commercelink.products;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;

import java.text.Collator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The store's manual catalog categories by the PIM categories they take, and the products already in them. Read once
 * per two minutes per store (one query per category, a few at a time, the two codes only). A save of products or
 * categories evicts it on the instance that saved; the inventory page reads it fresh after "Uzupełnij dane", which may
 * have saved on another instance.
 */
@Component
@RequiredArgsConstructor
public class CatalogPlacement {

    private static final Collator POLISH = Collator.getInstance(Locale.forLanguageTag("pl-PL"));
    private static final Executor READERS = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "catalog-placement");
        thread.setDaemon(true);
        return thread;
    });

    private final ProductCatalogRepository catalogRepository;
    private final ProductRepository productRepository;
    private final Cache<String, StorePlacement> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();

    public StorePlacement forStore(String storeId) {
        return cache.get(storeId, this::load);
    }

    /** Read again, bypassing what this instance holds, and kept for the requests after it. */
    public StorePlacement forStoreFresh(String storeId) {
        StorePlacement placement = load(storeId);
        cache.put(storeId, placement);
        return placement;
    }

    public void evict(String storeId) {
        cache.invalidate(storeId);
    }

    private StorePlacement load(String storeId) {
        List<Target> targets = new ArrayList<>();
        List<ProductCatalog> catalogs = new ArrayList<>(catalogRepository.findAll(storeId));
        catalogs.sort(Comparator.comparing(ProductCatalog::getName, Comparator.nullsLast(POLISH)));
        for (ProductCatalog catalog : catalogs) {
            for (CategoryDefinition category : catalog.getCategories()) {
                // Automatic categories pick their own products; nothing can be added to them by hand.
                if (!category.hasType(CategoryDefinitionType.Managed)) {
                    continue;
                }
                targets.add(new Target(catalog.getCatalogId(), catalog.getName(), category.getCategoryId(),
                        category.getName(), List.copyOf(category.getPimCategoryIds())));
            }
        }
        // One query per category: run a few at once so a store with a hundred categories does not wait for them in turn.
        List<CompletableFuture<List<Existing>>> reads = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> existingIn(target), READERS))
                .toList();
        List<Existing> existing = new ArrayList<>();
        for (CompletableFuture<List<Existing>> read : reads) {
            existing.addAll(joined(read));
        }
        return new StorePlacement(targets, existing);
    }

    private List<Existing> existingIn(Target target) {
        return productRepository.codesOf(target.categoryId()).stream()
                .map(product -> new Existing(target.catalogId(), target.categoryId(),
                        new InventoryKey(product.ean(), product.manufacturerCode())))
                .toList();
    }

    private static <T> T joined(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    public record Target(String catalogId, String catalogName, String categoryId, String categoryName,
                         List<String> pimCategoryIds) {

        public String label() {
            return catalogName + " › " + categoryName;
        }

        public String value() {
            return catalogId + "/" + categoryId;
        }
    }

    public record Existing(String catalogId, String categoryId, InventoryKey key) {
    }

    public static final class StorePlacement {

        private final List<Target> targets;
        private final Map<String, List<Existing>> byEan = new HashMap<>();
        private final Map<String, List<Existing>> byCode = new HashMap<>();

        public StorePlacement(List<Target> targets, List<Existing> existing) {
            this.targets = List.copyOf(targets);
            for (Existing product : existing) {
                product.key().getProductEans().forEach(ean -> byEan.computeIfAbsent(ean, e -> new ArrayList<>()).add(product));
                product.key().getProductCodes().forEach(code -> byCode.computeIfAbsent(code, c -> new ArrayList<>()).add(product));
            }
        }

        public List<Target> targets() {
            return targets;
        }

        public Optional<Target> target(String value) {
            return targets.stream().filter(target -> target.value().equals(value)).findFirst();
        }

        public List<Existing> existing(InventoryKey key) {
            Set<Existing> found = new LinkedHashSet<>();
            key.getProductEans().forEach(ean -> found.addAll(byEan.getOrDefault(ean, List.of())));
            key.getProductCodes().forEach(code -> found.addAll(byCode.getOrDefault(code, List.of())));
            return List.copyOf(found);
        }

        public boolean isIn(String categoryId, InventoryKey key) {
            return existing(key).stream().anyMatch(product -> product.categoryId().equals(categoryId));
        }
    }
}
