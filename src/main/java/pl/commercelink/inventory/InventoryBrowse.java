package pl.commercelink.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.search.OfferShipping;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.text.Collator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Paged browsing of the assortment a store can buy, by PIM category, supplier, stock and text. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class InventoryBrowse {

    private static final Collator POLISH = Collator.getInstance(Locale.forLanguageTag("pl-PL"));
    private static final Comparator<BrowseRow> BY_NAME =
            Comparator.comparing(BrowseRow::name, Comparator.nullsLast(POLISH));

    private final BrowseIndexHolder holder;
    private final GlobalMatchedInventory globalInventory;
    private final StoresRepository storesRepository;
    private final StoreInventoryProvider storeInventoryProvider;
    private final SupplierRegistry supplierRegistry;
    // Same key and lifetime as Inventory.storeStatistics: a supplier change or a feed reload makes a new key.
    private final Cache<ScopeKey, BrowseScope> scopes = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();

    public BrowseSummary summary(@Nullable String storeId) {
        BrowseScope scope = scope(storeId);
        return scope == null ? BrowseSummary.EMPTY : scope.summary();
    }

    public BrowseResult browse(@Nullable String storeId, BrowseCriteria criteria) {
        BrowseScope scope = scope(storeId);
        if (scope == null) {
            return new BrowseResult(List.of(), 0, false);
        }
        String text = criteria.text() == null || criteria.text().isBlank() ? null : criteria.text().strip();
        List<BrowseRow> rows = new ArrayList<>();
        boolean truncated = false;
        Iterator<BrowseEntry> entries = scope.entries(criteria.categoryIds()).iterator();
        while (entries.hasNext()) {
            BrowseEntry entry = entries.next();
            if (text != null && !entry.matchesText(text)) {
                continue;
            }
            List<InventoryItem> offers = scope.offersOf(entry);
            if (!criteria.suppliers().isEmpty()) {
                offers = offers.stream().filter(offer -> criteria.suppliers().contains(offer.supplier())).toList();
            }
            if (offers.isEmpty() || !stockMatches(criteria.stock(), offers)) {
                continue;
            }
            BrowseRow row = toRow(entry, offers);
            if (!criteria.rowFilter().test(row)) {
                continue;
            }
            if (text != null && rows.size() == BrowseCriteria.MAX_TEXT_MATCHES) {
                truncated = true;
                break;
            }
            rows.add(row);
        }
        rows.sort(comparator(criteria));
        int total = rows.size();
        int from = Math.min(criteria.offset(), total);
        int to = Math.min(from + criteria.limit(), total);
        return new BrowseResult(List.copyOf(rows.subList(from, to)), total, truncated);
    }

    private BrowseScope scope(@Nullable String storeId) {
        BrowseIndex index = holder.current();
        if (storeId == null) {
            return scopes.get(new ScopeKey(null, index.version(), Set.of(), List.of()),
                    key -> BrowseScope.everyGlobalSupplier(index));
        }
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return null;
        }
        Set<String> enabled = store.getGlobalSupplierNames().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        ScopeKey key = new ScopeKey(storeId, index.version(), enabled, Inventory.ownConnectionFingerprint(store));
        return scopes.get(key, k -> BrowseScope.of(index, globalInventory.index(), enabled::contains,
                storeInventoryProvider.ownInventory(store).items()));
    }

    private static boolean stockMatches(BrowseCriteria.Stock stock, List<InventoryItem> offers) {
        boolean inStock = offers.stream().anyMatch(offer -> offer.qty() > 0);
        return switch (stock) {
            case ALL -> true;
            case IN_STOCK -> inStock;
            case ON_ORDER -> !inStock;
        };
    }

    private BrowseRow toRow(BrowseEntry entry, List<InventoryItem> offers) {
        // The cheapest offer that can ship now; only when nothing is in stock does an on-order price lead.
        List<InventoryItem> candidates = offers.stream().anyMatch(offer -> offer.qty() > 0)
                ? offers.stream().filter(offer -> offer.qty() > 0).toList()
                : offers;
        InventoryItem best = null;
        double bestTotal = Double.MAX_VALUE;
        boolean bestKnown = false;
        for (InventoryItem offer : candidates) {
            OfferShipping shipping = OfferShipping.forItem(supplierRegistry, offer);
            double total = offer.netPrice() + shipping.deliveryNet();
            if (total < bestTotal) {
                best = offer;
                bestTotal = total;
                bestKnown = shipping.known();
            }
        }
        long qty = offers.stream().mapToLong(offer -> Math.max(0, offer.qty())).sum();
        Set<String> suppliers = new HashSet<>();
        offers.forEach(offer -> suppliers.add(offer.supplier()));
        InventoryKey key = entry.group().getInventoryKey();
        String ean = best.ean() != null ? best.ean() : key.getProductEans().stream().findFirst().orElse(null);
        String mfn = best.mfn() != null ? best.mfn() : key.getProductCodes().stream().findFirst().orElse(null);
        return new BrowseRow(key, entry.name(), entry.brand(), ean, mfn, entry.categoryId(),
                entry.taxonomy().category(), bestTotal, bestKnown, best.supplier(), qty, suppliers.size());
    }

    private static Comparator<BrowseRow> comparator(BrowseCriteria criteria) {
        Comparator<BrowseRow> primary = switch (criteria.sort()) {
            case NAME -> BY_NAME;
            case COST -> Comparator.comparingDouble(BrowseRow::lowestDeliveredNet);
            case QTY -> Comparator.comparingLong(BrowseRow::qty);
        };
        if (criteria.descending()) {
            primary = primary.reversed();
        }
        return primary.thenComparing(BY_NAME);
    }

    private record ScopeKey(String storeId, long version, Set<String> enabledGlobal, List<String> ownFingerprint) {
    }
}
