package pl.commercelink.inventory.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

@Component
@RequiredArgsConstructor
public class InventorySearch {

    // The rest of the application quotes supplier shipping for Poland; the page must not disagree with it.
    private static final String DESTINATION = "PL";

    private final Inventory inventory;
    private final StoresRepository storesRepository;
    private final PimCatalog pimCatalog;
    private final TaxonomyCache taxonomyCache;
    private final Warehouse warehouse;
    private final SupplierLabels supplierLabels;
    private final SupplierRegistry supplierRegistry;

    public InventorySearchResult search(String storeId, String query) {
        Store store = storesRepository.findById(storeId);
        Map<String, ConnectionMode> modes = new HashMap<>();
        if (store != null) {
            store.getSupplierConnections().forEach(connection -> modes.putIfAbsent(connection.getSupplierName(), connection.getMode()));
        }
        StockQueryService stock = store != null && !store.hasIntegration(IntegrationType.WMS_PROVIDER)
                ? warehouse.stockQueryService(storeId)
                : null;
        return run(query, inventory.withEnabledSuppliersOnly(storeId), modes::get, supplierLabels.forStore(store), storeId, stock);
    }

    public InventorySearchResult searchGlobal(String query) {
        // offers span every store here, so no connection labels apply: identities fall back to their legacy shape
        return run(query, inventory.withGlobalData(), supplier -> ConnectionMode.GLOBAL, supplierLabels.forStore(null), null, null);
    }

    private InventorySearchResult run(String query, InventoryView view, Function<String, ConnectionMode> modeOf,
                                      SupplierLabelMap labels, String storeId, StockQueryService stock) {
        Match match = firstMatch(query, view);
        List<WarehouseItemView> warehouseItems = stock == null
                ? List.of()
                : stock.searchAllAvailableByMfns(storeId, warehouseCodes(match, query));
        if (match == null && warehouseItems.isEmpty()) {
            return known(query);
        }
        MatchedBy matchedBy = match == null ? MatchedBy.MFN : match.by();
        List<InventoryItem> items = match == null ? List.of() : match.inventory().getInventoryItems();
        ProductCodes codes = ProductCodes.resolve(matchedBy, query,
                items.isEmpty() ? warehouseItems.stream().map(WarehouseItemView::getEan).toList() : items.stream().map(InventoryItem::ean).toList(),
                items.isEmpty() ? warehouseItems.stream().map(WarehouseItemView::getMfn).toList() : items.stream().map(InventoryItem::mfn).toList());
        ProductHeader product = match == null ? warehouseHeader(query, codes) : header(match.inventory(), codes);
        ProductCodes anchor = new ProductCodes(product.ean(), product.mfn());
        List<OfferRow> offers = offers(items, modeOf, labels, anchor);
        List<WarehouseRow> rows = warehouseRows(warehouseItems, anchor);
        return new InventorySearchResult.Found(matchedBy, product, offers, rows, prices(offers, rows), stock != null);
    }

    private Match firstMatch(String query, InventoryView view) {
        MatchedInventory byEan = view.findByEan(query);
        if (byEan.hasAnyOffers()) {
            return new Match(MatchedBy.EAN, byEan);
        }
        MatchedInventory byMfn = view.findByProductCode(query);
        if (byMfn.hasAnyOffers()) {
            return new Match(MatchedBy.MFN, byMfn);
        }
        Optional<PimEntry> pimEntry = pimCatalog.findByPimId(query);
        if (pimEntry.isPresent()) {
            MatchedInventory byPimId = view.findByInventoryKey(InventoryKey.fromPimEntry(pimEntry.get()));
            if (byPimId.hasAnyOffers()) {
                return new Match(MatchedBy.PIM_ID, byPimId);
            }
        }
        return null;
    }

    private Collection<String> warehouseCodes(Match match, String query) {
        if (match != null) {
            return match.inventory().getInventoryKey().getProductCodes();
        }
        // a product nobody offers can still sit in the warehouse, which is only searchable by manufacturer code
        String mfn = unifyMfn(query);
        return mfn == null ? List.of() : List.of(mfn);
    }

    private List<OfferRow> offers(List<InventoryItem> items, Function<String, ConnectionMode> modeOf,
                                  SupplierLabelMap labels, ProductCodes product) {
        record Quoted(InventoryItem item, OfferShipping shipping) {
            double totalNet() {
                return item.netPrice() + shipping.deliveryNet();
            }

            boolean buyable() {
                return item.qty() > 0 && item.netPrice() > 0;
            }
        }
        List<Quoted> quoted = items.stream().map(item -> new Quoted(item, shippingFor(item))).toList();
        // two connections of one supplier, or one feed listing a product twice, both render the same name
        Set<String> repeatedLabels = quoted.stream()
                .collect(Collectors.groupingBy(row -> labels.of(row.item().supplier()), Collectors.counting()))
                .entrySet().stream().filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        // an offer nobody can deliver is not a price anyone can buy at, so it neither wins nor leads the list
        double lowestTotal = quoted.stream().filter(Quoted::buyable).mapToDouble(Quoted::totalNet).min().orElse(0);
        return quoted.stream()
                .sorted(Comparator.comparing((Quoted row) -> row.item().qty() <= 0)
                        .thenComparing(row -> row.item().netPrice() <= 0)
                        .thenComparingDouble(Quoted::totalNet))
                .map(row -> new OfferRow(
                        row.item().supplier(),
                        labels.of(row.item().supplier()),
                        modeOf.apply(row.item().supplier()),
                        row.item().ean(),
                        row.item().mfn(),
                        Price.fromNet(row.item().netPrice()).netValue(),
                        Price.fromNet(row.item().netPrice()).grossValue(),
                        row.shipping().deliveryNet(),
                        row.shipping().freeFrom(),
                        row.shipping().known(),
                        row.shipping().totalDays(),
                        row.item().qty(),
                        row.buyable() && row.totalNet() == lowestTotal,
                        repeatedLabels.contains(labels.of(row.item().supplier())),
                        CodeMatch.of(product, row.item().ean(), row.item().mfn())))
                .toList();
    }

    /**
     * Shipping terms come from the supplier plugin, the same source the fulfilment planner quotes.
     * A supplier the registry does not know falls back to a placeholder policy there, so rather than
     * print an invented cost the row reports that shipping is simply unknown.
     */
    private OfferShipping shippingFor(InventoryItem item) {
        String supplier = item.supplier();
        if (!supplierRegistry.exists(supplier) && !SupplierIdentity.isManual(supplier)) {
            return OfferShipping.UNKNOWN;
        }
        return OfferShipping.of(supplierRegistry.get(supplier).shippingTermsFor(DESTINATION),
                item.netPrice(), item.leadTimeDays());
    }

    private static List<WarehouseRow> warehouseRows(List<WarehouseItemView> items, ProductCodes product) {
        return items.stream()
                .sorted(Comparator.comparing(WarehouseItemView::isInDelivery))
                .map(item -> new WarehouseRow(item.getEan(), item.getMfn(), item.getPrice().netValue(),
                        item.getPrice().grossValue(), item.getQty(), item.isInDelivery(), item.getCondition(),
                        CodeMatch.of(product, item.getEan(), item.getMfn())))
                .toList();
    }

    private static PriceSummary prices(List<OfferRow> offers, List<WarehouseRow> rows) {
        int inStock = rows.stream().filter(row -> !row.inDelivery()).mapToInt(WarehouseRow::qty).sum();
        int inDelivery = rows.stream().filter(WarehouseRow::inDelivery).mapToInt(WarehouseRow::qty).sum();
        List<OfferRow> buyable = offers.stream().filter(offer -> offer.hasStock() && offer.hasPrice()).toList();
        List<Double> pricesInStock = buyable.stream().map(OfferRow::netPrice).sorted().toList();
        // the headline figure names one offer, so it must be the same one the table marks as cheapest
        OfferRow lowest = buyable.stream().min(Comparator.comparingDouble(OfferRow::totalNet)).orElse(null);
        return new PriceSummary(
                lowest == null ? 0 : lowest.netPrice(),
                lowest == null ? 0 : lowest.totalNet(),
                median(pricesInStock),
                pricesInStock.size(),
                offers.stream().mapToLong(OfferRow::qty).sum(),
                inStock,
                inDelivery,
                lowest != null && lowest.codeMatch().isWarning());
    }

    // listed offers, not warehouse stock, drive the summary price; MatchedInventory's own
    // lowest/median can disagree with the cheapest row (skips qty==1) or blow up on an all-zero-price match
    private static double median(List<Double> pricedOffers) {
        if (pricedOffers.isEmpty()) {
            return 0;
        }
        int size = pricedOffers.size();
        return size % 2 == 0
                ? (pricedOffers.get(size / 2 - 1) + pricedOffers.get(size / 2)) / 2.0
                : pricedOffers.get(size / 2);
    }

    private ProductHeader header(MatchedInventory matched, ProductCodes codes) {
        InventoryKey key = matched.getInventoryKey();
        Taxonomy taxonomy = matched.getTaxonomy();
        if (taxonomy != null && taxonomy != Taxonomy.EMPTY) {
            ProductCodes shown = codes.orElse(taxonomy.ean(), taxonomy.mfn());
            return new ProductHeader(taxonomy.name(), taxonomy.brand(), shown.ean(), shown.code());
        }
        Optional<PimEntry> pimEntry = key.getId() == null ? Optional.empty() : pimCatalog.findByPimId(key.getId());
        return new ProductHeader(pimEntry.map(PimEntry::name).orElse(null), pimEntry.map(PimEntry::brand).orElse(null), codes.ean(), codes.code());
    }

    private ProductHeader warehouseHeader(String query, ProductCodes codes) {
        String mfn = unifyMfn(query);
        Taxonomy taxonomy = mfn == null ? null : taxonomyCache.findByMfn(mfn);
        if (taxonomy != null) {
            ProductCodes shown = codes.orElse(taxonomy.ean(), taxonomy.mfn());
            return new ProductHeader(taxonomy.name(), taxonomy.brand(), shown.ean(), shown.code());
        }
        return new ProductHeader(null, null, codes.ean(), codes.code());
    }

    private InventorySearchResult known(String query) {
        String mfn = unifyMfn(query);
        Taxonomy taxonomy = mfn == null ? null : taxonomyCache.findByMfn(mfn);
        if (taxonomy != null) {
            return new InventorySearchResult.KnownWithoutOffers(MatchedBy.MFN,
                    new ProductHeader(taxonomy.name(), taxonomy.brand(), taxonomy.ean(), taxonomy.mfn()));
        }
        Optional<PimEntry> byGtin = pimCatalog.findByGtin(query);
        if (byGtin.isPresent()) {
            return new InventorySearchResult.KnownWithoutOffers(MatchedBy.EAN, header(byGtin.get()));
        }
        Optional<PimEntry> byPimId = pimCatalog.findByPimId(query);
        if (byPimId.isPresent()) {
            return new InventorySearchResult.KnownWithoutOffers(MatchedBy.PIM_ID, header(byPimId.get()));
        }
        return new InventorySearchResult.NotFound(query);
    }

    private static ProductHeader header(PimEntry entry) {
        return new ProductHeader(entry.name(), entry.brand(), first(entry.gtins()), first(entry.mpns()));
    }

    private static String first(Collection<String> values) {
        return values == null ? null : values.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private record Match(MatchedBy by, MatchedInventory inventory) {
    }
}
