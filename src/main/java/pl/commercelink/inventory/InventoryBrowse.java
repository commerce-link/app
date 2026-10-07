package pl.commercelink.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.search.OfferShipping;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Paged browsing of the assortment a store can buy, by PIM category, supplier and text. */
@Component
public class InventoryBrowse {

    // Each cached product is one reference: two million of them is about 8 MB, whatever the mix of selections.
    private static final long MAX_CACHED_PRODUCTS = 2_000_000;

    private final BrowseIndexHolder holder;
    private final StoresRepository storesRepository;
    private final StoreInventoryProvider storeInventoryProvider;
    private final SupplierRegistry supplierRegistry;
    // Same key and lifetime as Inventory.storeStatistics: a supplier change or a global feed reload (the index version)
    // makes a new key; an own-feed reload keeps the key and is picked up when the entry expires after two minutes.
    private final Cache<ScopeKey, BrowseScope> scopes = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();
    // The next page, the counts and the list of one selection are read within seconds of each other: the products a
    // selection holds and their order are kept briefly, so none of them walks the whole category again.
    private final Cache<SelectionKey, Selection> selections = Caffeine.newBuilder()
            .maximumWeight(MAX_CACHED_PRODUCTS)
            .<SelectionKey, Selection>weigher((key, selection) -> selection.entries().size() + 1)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();
    private final Cache<OrderKey, List<BrowseEntry>> orders = Caffeine.newBuilder()
            .maximumWeight(MAX_CACHED_PRODUCTS)
            .<OrderKey, List<BrowseEntry>>weigher((key, entries) -> entries.size() + 1)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    InventoryBrowse(BrowseIndexHolder holder, StoresRepository storesRepository,
                    StoreInventoryProvider storeInventoryProvider, SupplierRegistry supplierRegistry) {
        this.holder = holder;
        this.storesRepository = storesRepository;
        this.storeInventoryProvider = storeInventoryProvider;
        this.supplierRegistry = supplierRegistry;
        // Every cached scope and selection holds the previous generation of the inventory; let it go with the index.
        holder.onReplaced(this::forgetResults);
    }

    public BrowseSummary summary(@Nullable String storeId) {
        BrowseScope scope = scope(storeId);
        return scope == null ? BrowseSummary.EMPTY : scope.summary();
    }

    /** Counts of the products in the categories ({@code null}: all), from the suppliers (empty: all) and with the text. */
    public BrowseFacets facets(@Nullable String storeId, @Nullable Set<String> categoryIds, Set<String> suppliers,
                               @Nullable String text) {
        BrowseScope scope = scope(storeId);
        if (scope == null) {
            return BrowseFacets.EMPTY;
        }
        return selection(scope, categoryIds, suppliers, text).facets();
    }

    public BrowseResult browse(@Nullable String storeId, BrowseCriteria criteria) {
        BrowseScope scope = scope(storeId);
        if (scope == null) {
            return new BrowseResult(List.of(), 0, false);
        }
        Selection selection = selection(scope, criteria.categoryIds(), criteria.suppliers(), criteria.text());
        OrderKey orderKey = new OrderKey(selection.key(), criteria.sort(), criteria.descending());
        List<BrowseEntry> ordered = orders.get(orderKey, key -> order(scope, selection, key));
        int total = ordered.size();
        int offset = criteria.offset();
        if (total > 0 && offset >= total) {
            // A bookmarked page past the end shows the last page instead of an empty table.
            offset = ((total - 1) / criteria.limit()) * criteria.limit();
        }
        int from = Math.min(offset, total);
        int to = Math.min(from + criteria.limit(), total);
        List<BrowseRow> rows = new ArrayList<>(to - from);
        for (BrowseEntry entry : ordered.subList(from, to)) {
            rows.add(toRow(entry, offersFrom(scope, entry, criteria.suppliers())));
        }
        return new BrowseResult(List.copyOf(rows), total, selection.truncated());
    }

    private void forgetResults() {
        scopes.invalidateAll();
        selections.invalidateAll();
        orders.invalidateAll();
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
        return scopes.get(key, k -> BrowseScope.of(index, enabled::contains,
                storeInventoryProvider.ownInventory(store).items()));
    }

    private Selection selection(BrowseScope scope, @Nullable Set<String> categoryIds, Set<String> suppliers,
                                @Nullable String text) {
        String needle = text == null || text.isBlank() ? null : text.strip();
        SelectionKey key = new SelectionKey(scope, categoryIds == null ? null : Set.copyOf(categoryIds),
                Set.copyOf(suppliers), needle);
        return selections.get(key, this::select);
    }

    /** One walk over the candidates: the products the list shows and the counts of the menus around it. */
    private Selection select(SelectionKey key) {
        List<BrowseEntry> entries = new ArrayList<>();
        Map<String, Integer> byCategory = new HashMap<>();
        Map<String, Integer> bySupplier = new HashMap<>();
        List<String> seen = new ArrayList<>(4);
        for (BrowseEntry entry : (Iterable<BrowseEntry>) key.scope().entries(key.categoryIds())::iterator) {
            if (key.text() != null && !entry.matchesText(key.text())) {
                continue;
            }
            boolean selected = key.suppliers().isEmpty();
            seen.clear();
            for (InventoryItem offer : key.scope().offersOf(entry)) {
                String supplier = offer.supplier();
                if (!seen.contains(supplier)) {
                    seen.add(supplier);
                    bySupplier.merge(supplier, 1, Integer::sum);
                }
                selected |= key.suppliers().contains(supplier);
            }
            if (selected && !seen.isEmpty()) {
                entries.add(entry);
                byCategory.merge(entry.categoryId(), 1, Integer::sum);
            }
        }
        return new Selection(key, Collections.unmodifiableList(entries),
                new BrowseFacets(Map.copyOf(byCategory), Map.copyOf(bySupplier)),
                key.text() != null && entries.size() > BrowseCriteria.MAX_TEXT_MATCHES);
    }

    /** The selection in the requested order; a phrase keeps only its best matches, after sorting all of them. */
    private List<BrowseEntry> order(BrowseScope scope, Selection selection, OrderKey key) {
        Comparator<BrowseEntry> byName = Comparator.comparingLong(BrowseEntry::nameRank);
        List<BrowseEntry> ordered;
        if (key.sort() == BrowseCriteria.Sort.NAME) {
            ordered = new ArrayList<>(selection.entries());
            ordered.sort(key.descending() ? byName.reversed() : byName);
        } else {
            List<Ranked> ranked = new ArrayList<>(selection.entries().size());
            for (BrowseEntry entry : selection.entries()) {
                List<InventoryItem> offers = offersFrom(scope, entry, selection.key().suppliers());
                ranked.add(new Ranked(entry, price(offers).total(), totalQty(offers)));
            }
            Comparator<Ranked> primary = key.sort() == BrowseCriteria.Sort.COST
                    ? Comparator.comparingDouble(Ranked::cost)
                    : Comparator.comparingLong(Ranked::qty);
            if (key.descending()) {
                primary = primary.reversed();
            }
            ranked.sort(primary.thenComparing(Ranked::entry, byName));
            ordered = new ArrayList<>(ranked.size());
            for (Ranked item : ranked) {
                ordered.add(item.entry());
            }
        }
        if (selection.truncated()) {
            ordered = new ArrayList<>(ordered.subList(0, BrowseCriteria.MAX_TEXT_MATCHES));
        }
        return Collections.unmodifiableList(ordered);
    }

    private static List<InventoryItem> offersFrom(BrowseScope scope, BrowseEntry entry, Set<String> suppliers) {
        List<InventoryItem> offers = scope.offersOf(entry);
        return suppliers.isEmpty() ? offers : offers.stream().filter(offer -> suppliers.contains(offer.supplier())).toList();
    }

    private BrowseRow toRow(BrowseEntry entry, List<InventoryItem> offers) {
        Price price = price(offers);
        InventoryItem best = price.offer();
        List<String> suppliers = new ArrayList<>(offers.size());
        offers.forEach(offer -> {
            if (!suppliers.contains(offer.supplier())) {
                suppliers.add(offer.supplier());
            }
        });
        InventoryKey key = entry.group().getInventoryKey();
        String ean = best.ean() != null ? best.ean() : key.getProductEans().stream().findFirst().orElse(null);
        String mfn = best.mfn() != null ? best.mfn() : key.getProductCodes().stream().findFirst().orElse(null);
        return new BrowseRow(key, entry.name(), entry.brand(), ean, mfn, entry.categoryId(),
                entry.taxonomy().category(), price.total(), price.known(), best.supplier(), totalQty(offers),
                suppliers.size());
    }

    /** The cheapest offer that can ship now; only when nothing is in stock does an on-order price lead. */
    private Price price(List<InventoryItem> offers) {
        boolean anyInStock = offers.stream().anyMatch(offer -> offer.qty() > 0);
        InventoryItem best = null;
        double bestTotal = Double.MAX_VALUE;
        boolean bestKnown = false;
        for (InventoryItem offer : offers) {
            if (anyInStock && offer.qty() <= 0) {
                continue;
            }
            OfferShipping shipping = OfferShipping.forItem(supplierRegistry, offer);
            double total = offer.netPrice() + shipping.deliveryNet();
            // Seeded with the first candidate: a NaN total never compares lower and would leave no offer at all.
            if (best == null || total < bestTotal) {
                best = offer;
                bestTotal = total;
                bestKnown = shipping.known();
            }
        }
        return new Price(best, bestTotal, bestKnown);
    }

    private static long totalQty(List<InventoryItem> offers) {
        long qty = 0;
        for (InventoryItem offer : offers) {
            qty += Math.max(0, offer.qty());
        }
        return qty;
    }

    private record Price(InventoryItem offer, double total, boolean known) {
    }

    private record Ranked(BrowseEntry entry, double cost, long qty) {
    }

    private record ScopeKey(String storeId, long version, Set<String> enabledGlobal, List<String> ownFingerprint) {
    }

    /** A scope compares by identity: a rebuilt scope never shares results with the one it replaced. */
    private record SelectionKey(BrowseScope scope, Set<String> categoryIds, Set<String> suppliers, String text) {
    }

    private record OrderKey(SelectionKey selection, BrowseCriteria.Sort sort, boolean descending) {
    }

    private record Selection(SelectionKey key, List<BrowseEntry> entries, BrowseFacets facets, boolean truncated) {
    }
}
