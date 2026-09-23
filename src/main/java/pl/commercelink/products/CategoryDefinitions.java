package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saves one section of a category definition at a time (basics, pricing, one marketplace, filters) so the four settings
 * pages never overwrite each other. Sanitising rules match ProductCatalog.addOrUpdateCategoryDefinition: blank labels
 * and PIM ids are dropped, incomplete price groups/marketplaces/filters are rejected by the forms before they get here.
 *
 * <p>The whole catalog is one versioned DynamoDB item, so two saves of different sections (or of different categories)
 * race for it. Every write therefore goes through {@link OptimisticLockingExecutor#modifyAndSave}: the catalog is read
 * again, only the section is applied to that fresh read and the save is retried when another write got there first.
 * The catalog and the category a caller passes in only say which record to change; they are never saved themselves.
 * When the retries run out, {@link OptimisticLockingExhaustedException} reaches the caller, which answers with a
 * message at the form. A later save of the same section still wins over an earlier one (no version travels with the
 * forms, ruling D-M15/OD-7): this removes the error, it does not detect a lost update of one section.
 */
@Service
@RequiredArgsConstructor
public class CategoryDefinitions {

    public record Basics(String name, int sequenceNumber, List<String> pimCategoryIds, CategoryDefinitionType type, int maxQty,
                         boolean requiredDuringOrder, boolean includedInDeliverySuggestions, boolean deletionProtection,
                         List<String> labels) {
    }

    /**
     * The Pricing page of a category: stock thresholds, availability and the price groups, applied as one section. The
     * one place the saved groups are put on the category: their order is the one CategoryDefinition.setPriceDefinitions
     * gives them, from the order of {@code groups} (the order of the form). Validation stays with the form.
     */
    public record Pricing(StockDefinition stock, AvailabilityDefinition availability, List<PriceDefinition> groups) {

        void applyTo(CategoryDefinition category) {
            category.setStockDefinition(stock);
            category.setAvailabilityDefinition(availability);
            category.setPriceDefinitions(new ArrayList<>(groups));
        }
    }

    public record RemoveResult(boolean productsKept, int productsDeleted) {
    }

    /** What remove() will do, for the confirmation page: the same decision, made in the same place, without deleting. */
    public record DeletionPreview(boolean productsKept, int productsToDelete) {
    }

    /** The catalog, or the category in it, is gone by the time the section is saved: another request removed it. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class CategoryNotFoundException extends RuntimeException {

        public CategoryNotFoundException(String catalogId, String categoryId) {
            super(categoryId == null ? "Catalog " + catalogId + " no longer exists"
                    : "Category " + categoryId + " of catalog " + catalogId + " no longer exists");
        }
    }

    private final ProductCatalogRepository catalogs;
    private final ProductRepository products;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public CategoryDefinition create(ProductCatalog catalog, Basics basics) {
        // Built once, outside the retried closure: a retry adds the same category, under the same id, to the fresh read.
        CategoryDefinition category = new CategoryDefinition()
                .withGeneratedId()
                .withStockDefinition(new StockDefinition(1, 10, 30))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.00, 0, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP));
        applyBasics(category, basics);
        modifyCatalog(catalog, null, fresh -> {
            fresh.getCategories().removeIf(existing -> category.getCategoryId().equals(existing.getCategoryId()));
            category.setSequenceNumber(basics.sequenceNumber() > 0 ? basics.sequenceNumber() : fresh.getNextSequenceNumber());
            fresh.getCategories().add(category);
            return Outcome.APPLIED;
        });
        return category;
    }

    public void saveBasics(ProductCatalog catalog, CategoryDefinition category, Basics basics) {
        modifyCategory(catalog, category, fresh -> {
            if (fresh.getType() != basics.type()) {
                fresh.setTypeChangedAt(LocalDateTime.now());
            }
            applyBasics(fresh, basics);
            if (basics.sequenceNumber() > 0) {
                fresh.setSequenceNumber(basics.sequenceNumber());
            }
        });
    }

    private void applyBasics(CategoryDefinition category, Basics basics) {
        category.setName(StringUtils.trimToNull(basics.name()));
        category.setPimCategoryIds(distinctNonBlank(basics.pimCategoryIds()));
        category.setType(basics.type());
        category.setMaxQty(basics.maxQty());
        category.setRequiredDuringOrder(basics.requiredDuringOrder());
        category.setIncludedInDeliverySuggestions(basics.includedInDeliverySuggestions());
        category.setDeletionProtection(basics.deletionProtection());
        category.setGroupingOrder(distinctNonBlank(basics.labels()));
    }

    public void savePricing(ProductCatalog catalog, CategoryDefinition category, Pricing pricing) {
        modifyCategory(catalog, category, pricing::applyTo);
    }

    public void saveMarketplace(ProductCatalog catalog, CategoryDefinition category, MarketplaceDefinition definition) {
        modifyCategory(catalog, category, fresh -> {
            fresh.getMarketplaceDefinitions().removeIf(m -> Objects.equals(m.getName(), definition.getName()));
            fresh.getMarketplaceDefinitions().add(definition);
        });
    }

    public void removeMarketplace(ProductCatalog catalog, CategoryDefinition category, String name) {
        modifyCategory(catalog, category, fresh -> fresh.getMarketplaceDefinitions().removeIf(m -> Objects.equals(m.getName(), name)));
    }

    public void saveFilters(ProductCatalog catalog, CategoryDefinition category, List<InventoryDefinition> filters) {
        modifyCategory(catalog, category, fresh -> fresh.setInventoryDefinitions(new LinkedList<>(filters)));
    }

    public DeletionPreview deletionPreview(ProductCatalog catalog, CategoryDefinition category) {
        boolean kept = productsSurviveRemoval(catalog, category);
        return new DeletionPreview(kept, kept ? 0 : products.findAll(category.getCategoryId()).size());
    }

    /** The same preview for a caller that has the products of the category in hand: the decision, without a second read. */
    public DeletionPreview deletionPreview(ProductCatalog catalog, CategoryDefinition category, List<Product> categoryProducts) {
        boolean kept = productsSurviveRemoval(catalog, category);
        return new DeletionPreview(kept, kept ? 0 : categoryProducts.size());
    }

    /**
     * The catalog is saved first and the products are deleted after, from the decision taken on the catalog that was
     * saved: when the save keeps losing the race, the exception leaves every product where it was, and a retry never
     * follows a deletion that already happened.
     */
    public RemoveResult remove(ProductCatalog catalog, CategoryDefinition category) {
        requireUnprotected(category);
        AtomicBoolean kept = new AtomicBoolean();
        modifyCatalog(catalog, category.getCategoryId(), fresh -> {
            Optional<CategoryDefinition> current = categoryIn(fresh, category.getCategoryId());
            if (current.isEmpty()) {
                return Outcome.CATEGORY_GONE;
            }
            // Checked again on the read that is saved: protection may have been switched on since the page's check.
            if (current.get().isDeletionProtection()) {
                return Outcome.PROTECTED;
            }
            kept.set(productsSurviveRemoval(fresh, current.get()));
            fresh.getCategories().remove(current.get());
            return Outcome.APPLIED;
        });
        int deleted = 0;
        if (!kept.get()) {
            List<Product> orphaned = products.findAll(category.getCategoryId());
            // Deleted whatever their version: a product saved between this read and its deletion goes with the
            // category all the same, instead of failing the removal halfway through the list.
            orphaned.forEach(products::deleteWhateverItsVersion);
            deleted = orphaned.size();
        }
        return new RemoveResult(kept.get(), deleted);
    }

    private static void requireUnprotected(CategoryDefinition category) {
        if (category.isDeletionProtection()) {
            throw new IllegalStateException("Category " + category.getCategoryId() + " is protected from deletion");
        }
    }

    /** The products outlive the category when another category of the catalog is mapped to one of its PIM categories. */
    private boolean productsSurviveRemoval(ProductCatalog catalog, CategoryDefinition category) {
        return category.hasCategoryMapping() && catalog.getCategories().stream()
                .filter(other -> other != category)
                .anyMatch(other -> other.getPimCategoryIds().stream().anyMatch(category.getPimCategoryIds()::contains));
    }

    public boolean priceGroupInUse(CategoryDefinition category, String group) {
        return productsInPriceGroup(category, group) > 0;
    }

    public int productsInPriceGroup(CategoryDefinition category, String group) {
        return (int) products.findAll(category.getCategoryId()).stream()
                .filter(p -> group.equalsIgnoreCase(p.getPricingGroup()))
                .count();
    }

    /** What a closure passed to the executor found on its read; the exceptions are thrown only once the executor is left. */
    private enum Outcome { APPLIED, CATEGORY_GONE, PROTECTED }

    /** Applies {@code section} to the category as a fresh read of the catalog holds it, and saves; retried on a conflict. */
    private void modifyCategory(ProductCatalog catalog, CategoryDefinition category, Consumer<CategoryDefinition> section) {
        modifyCatalog(catalog, category.getCategoryId(), fresh -> categoryIn(fresh, category.getCategoryId())
                .map(current -> {
                    section.accept(current);
                    return Outcome.APPLIED;
                })
                .orElse(Outcome.CATEGORY_GONE));
    }

    /**
     * The closure may run more than once, each time on a new read: it must not depend on what an earlier run did.
     *
     * <p>Nothing is thrown from inside the executor. Its Spring Retry proxy retries only
     * ConditionalCheckFailedException and has a recovery method for that exception alone, so any other exception of a
     * closure would reach the caller as an ExhaustedRetryException ("Cannot locate recovery method") and a catch or a
     * {@code @ResponseStatus} of the original type would never apply. The closure reports what it found instead, the
     * saver writes only an applied change, and the exception is thrown here, after the executor.
     *
     * @param categoryId the category the change needs, for the message of a missing one; null when it needs none
     */
    private void modifyCatalog(ProductCatalog catalog, String categoryId, Function<ProductCatalog, Outcome> change) {
        String storeId = catalog.getStoreId();
        String catalogId = catalog.getCatalogId();
        AtomicReference<Outcome> outcome = new AtomicReference<>();
        optimisticLockingExecutor.modifyAndSave(
                () -> catalogs.findById(storeId, catalogId),
                fresh -> outcome.set(fresh == null ? Outcome.CATEGORY_GONE : change.apply(fresh)),
                fresh -> {
                    if (outcome.get() == Outcome.APPLIED) {
                        fresh.getCategories().sort(Comparator.comparingInt(CategoryDefinition::getSequenceNumber));
                        catalogs.save(fresh);
                    }
                });
        switch (outcome.get()) {
            case CATEGORY_GONE -> throw new CategoryNotFoundException(catalogId, categoryId);
            case PROTECTED -> throw new IllegalStateException("Category " + categoryId + " is protected from deletion");
            case APPLIED -> {
            }
        }
    }

    private static Optional<CategoryDefinition> categoryIn(ProductCatalog catalog, String categoryId) {
        return catalog.getCategories().stream()
                .filter(category -> Objects.equals(category.getCategoryId(), categoryId))
                .findFirst();
    }

    private static List<String> distinctNonBlank(List<String> values) {
        return values == null ? new LinkedList<>() : values.stream()
                .map(StringUtils::trim).filter(StringUtils::isNotEmpty).distinct()
                .collect(Collectors.toCollection(LinkedList::new));
    }
}
