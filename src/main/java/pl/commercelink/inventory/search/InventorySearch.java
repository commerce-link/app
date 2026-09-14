package pl.commercelink.inventory.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
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
import java.util.function.Function;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

@Component
@RequiredArgsConstructor
public class InventorySearch {

    private final Inventory inventory;
    private final StoresRepository storesRepository;
    private final PimCatalog pimCatalog;
    private final TaxonomyCache taxonomyCache;
    private final Warehouse warehouse;

    public InventorySearchResult search(String storeId, String query) {
        Store store = storesRepository.findById(storeId);
        Map<String, ConnectionMode> modes = new HashMap<>();
        if (store != null) {
            store.getSupplierConnections().forEach(connection -> modes.putIfAbsent(connection.getSupplierName(), connection.getMode()));
        }
        StockQueryService stock = store != null && !store.hasIntegration(IntegrationType.WMS_PROVIDER)
                ? warehouse.stockQueryService(storeId)
                : null;
        return run(query, inventory.withEnabledSuppliersOnly(storeId), modes::get, storeId, stock);
    }

    public InventorySearchResult searchGlobal(String query) {
        return run(query, inventory.withGlobalData(), supplier -> ConnectionMode.GLOBAL, null, null);
    }

    private InventorySearchResult run(String query, InventoryView view, Function<String, ConnectionMode> modeOf,
                                      String storeId, StockQueryService stock) {
        Match match = firstMatch(query, view);
        List<WarehouseItemView> warehouseItems = stock == null
                ? List.of()
                : stock.searchAllAvailableByMfns(storeId, warehouseCodes(match, query));
        if (match == null && warehouseItems.isEmpty()) {
            return known(query);
        }
        List<OfferRow> offers = match == null ? List.of() : offers(match.inventory(), modeOf);
        List<WarehouseRow> rows = warehouseRows(warehouseItems);
        return new InventorySearchResult.Found(
                match == null ? MatchedBy.MFN : match.by(),
                match == null ? warehouseHeader(query, warehouseItems) : header(match.inventory()),
                offers,
                rows,
                prices(match, offers, rows));
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

    private List<OfferRow> offers(MatchedInventory matched, Function<String, ConnectionMode> modeOf) {
        List<InventoryItem> items = matched.getInventoryItems();
        double lowestNet = items.stream().mapToDouble(InventoryItem::netPrice).filter(price -> price > 0).min().orElse(0);
        return items.stream()
                .sorted(Comparator.comparing((InventoryItem item) -> item.netPrice() <= 0)
                        .thenComparingDouble(InventoryItem::netPrice))
                .map(item -> new OfferRow(
                        item.supplier(),
                        ManualSupplierInfos.label(item.supplier()),
                        modeOf.apply(item.supplier()),
                        item.ean(),
                        item.mfn(),
                        Price.fromNet(item.netPrice()).grossValue(),
                        item.qty(),
                        item.netPrice() > 0 && item.netPrice() == lowestNet,
                        percentAbove(item.netPrice(), lowestNet)))
                .toList();
    }

    private static Double percentAbove(double netPrice, double lowestNet) {
        if (netPrice <= 0 || lowestNet <= 0 || netPrice == lowestNet) {
            return null;
        }
        return Math.round((netPrice - lowestNet) / lowestNet * 1000) / 10.0;
    }

    private static List<WarehouseRow> warehouseRows(List<WarehouseItemView> items) {
        return items.stream()
                .sorted(Comparator.comparing(WarehouseItemView::isInDelivery))
                .map(item -> new WarehouseRow(item.getEan(), item.getMfn(), item.getPrice().grossValue(), item.getQty(),
                        item.isInDelivery(), item.getCondition()))
                .toList();
    }

    private static PriceSummary prices(Match match, List<OfferRow> offers, List<WarehouseRow> rows) {
        int inStock = rows.stream().filter(row -> !row.inDelivery()).mapToInt(WarehouseRow::qty).sum();
        int inDelivery = rows.stream().filter(WarehouseRow::inDelivery).mapToInt(WarehouseRow::qty).sum();
        if (match == null) {
            return new PriceSummary(0, null, 0, 0, 0, 0, 0, inStock, inDelivery);
        }
        MatchedInventory matched = match.inventory();
        return new PriceSummary(
                matched.getLowestPrice().grossValue(),
                offers.stream().filter(OfferRow::cheapest).map(OfferRow::supplierLabel).findFirst().orElse(null),
                matched.getMedianPrice().grossValue(),
                offers.size(),
                offers.stream().mapToLong(OfferRow::qty).sum(),
                (int) offers.stream().filter(OfferRow::hasStock).map(OfferRow::supplier).distinct().count(),
                (int) offers.stream().map(OfferRow::supplier).distinct().count(),
                inStock,
                inDelivery);
    }

    private ProductHeader header(MatchedInventory matched) {
        InventoryKey key = matched.getInventoryKey();
        String ean = first(key.getProductEans());
        String mfn = first(key.getProductCodes());
        Taxonomy taxonomy = matched.getTaxonomy();
        if (taxonomy != null && taxonomy != Taxonomy.EMPTY) {
            return new ProductHeader(taxonomy.name(), taxonomy.brand(), ean != null ? ean : taxonomy.ean(), mfn != null ? mfn : taxonomy.mfn());
        }
        Optional<PimEntry> pimEntry = key.getId() == null ? Optional.empty() : pimCatalog.findByPimId(key.getId());
        return new ProductHeader(pimEntry.map(PimEntry::name).orElse(null), pimEntry.map(PimEntry::brand).orElse(null), ean, mfn);
    }

    private ProductHeader warehouseHeader(String query, List<WarehouseItemView> items) {
        String mfn = unifyMfn(query);
        Taxonomy taxonomy = mfn == null ? null : taxonomyCache.findByMfn(mfn);
        if (taxonomy != null) {
            return new ProductHeader(taxonomy.name(), taxonomy.brand(), taxonomy.ean(), taxonomy.mfn());
        }
        WarehouseItemView item = items.get(0);
        return new ProductHeader(null, null, item.getEan(), item.getMfn());
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
