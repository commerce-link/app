package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierConnectionViewFactory {

    private final StoreFeedRepository storeFeedRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRegistry supplierRegistry;

    public record SupplierConnectionViews(List<SupplierConnectionView> external,
                                          List<SupplierConnectionView> manual) {
    }

    public SupplierConnectionViews views(Store store) {
        List<StoreSupplierConnection> connections = connections(store);
        if (connections.isEmpty()) {
            return new SupplierConnectionViews(List.of(), List.of());
        }
        Map<String, LocalDateTime> storeFeeds = storeFeedRepository.feedLastModifiedByIdentity(store.getStoreId());
        boolean hasGlobalConnection = connections.stream()
                .anyMatch(connection -> connection.getMode() == ConnectionMode.GLOBAL);
        // Global connections have no file in this store's namespace at all, so the platform-wide
        // feeds bucket is only worth listing when at least one connection actually needs it.
        Map<String, LocalDateTime> globalFeeds = hasGlobalConnection
                ? inventoryRepository.getLatestModifiedPerSupplier()
                : Map.of();
        List<SupplierConnectionView> external = new ArrayList<>();
        List<SupplierConnectionView> manual = new ArrayList<>();
        for (StoreSupplierConnection connection : connections) {
            SupplierConnectionView view = toView(connection, storeFeeds, globalFeeds);
            if (view.isManual()) {
                manual.add(view);
            } else {
                external.add(view);
            }
        }
        Comparator<SupplierConnectionView> byLabel =
                Comparator.comparing(SupplierConnectionView::label, String.CASE_INSENSITIVE_ORDER);
        external.sort(byLabel);
        manual.sort(byLabel);
        return new SupplierConnectionViews(external, manual);
    }

    private SupplierConnectionView toView(StoreSupplierConnection connection,
                                           Map<String, LocalDateTime> storeFeeds,
                                           Map<String, LocalDateTime> globalFeeds) {
        String identity = connection.getSupplierName();
        boolean manual = connection.getMode() == ConnectionMode.MANUAL;
        // Global connections are served by the platform-wide feeds bucket rather than this store's
        // own namespace, so a leftover file left there must not be mistaken for the global feed.
        LocalDateTime feed = connection.getMode() == ConnectionMode.GLOBAL
                ? globalFeeds.get(identity.toLowerCase(Locale.ROOT))
                : storeFeeds.get(identity.toLowerCase(Locale.ROOT));
        return new SupplierConnectionView(
                identity,
                manual ? null : identity,
                manual ? ManualSupplierInfos.label(identity) : identity,
                connection.getMode(),
                connection.isIncludeInPricing(),
                connection.isIncludeInFulfilment(),
                manual ? connection.isEnabled() : true,
                feed,
                manual || supplierRegistry.exists(identity));
    }

    private List<StoreSupplierConnection> connections(Store store) {
        FulfilmentConfiguration config = store.getFulfilmentConfiguration();
        return config == null ? List.of() : config.getSupplierConnections();
    }
}
