package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryStatistics;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class InventorySourcesViewFactory {

    private static final Comparator<SourceRow> BY_LABEL = Comparator.comparing(SourceRow::label, String.CASE_INSENSITIVE_ORDER);

    private final SupplierConnectionViewFactory supplierConnectionViewFactory;

    public InventorySourcesView build(Store store, InventoryStatistics statistics, LocalDateTime now) {
        if (store == null) {
            return InventorySourcesView.EMPTY;
        }
        List<StoreSupplierConnection> connections = store.getSupplierConnections();
        Map<String, SupplierConnectionView> viewsByIdentity = new HashMap<>();
        SupplierConnectionViewFactory.SupplierConnectionViews views = supplierConnectionViewFactory.views(store);
        Stream.concat(views.external().stream(), views.manual().stream())
                .forEach(view -> viewsByIdentity.put(view.identity(), view));

        List<SourceRow> rows = connections.stream()
                .filter(StoreSupplierConnection::isEnabled)
                .map(connection -> row(connection, viewsByIdentity.get(connection.getSupplierName()), statistics, now))
                .toList();
        return new InventorySourcesView(
                rows.stream().filter(SourceRow::needsAttention).sorted(BY_LABEL).toList(),
                rows.stream().filter(row -> !row.needsAttention()).sorted(BY_LABEL).toList(),
                rows.size(),
                connections.size(),
                store.hasIntegration(IntegrationType.WMS_PROVIDER));
    }

    private static SourceRow row(StoreSupplierConnection connection, SupplierConnectionView view,
                                 InventoryStatistics statistics, LocalDateTime now) {
        String identity = connection.getSupplierName();
        LocalDateTime feed = view == null ? null : view.feedLastModified();
        return new SourceRow(
                identity,
                view == null ? identity : view.label(),
                connection.getMode(),
                statistics.productsOf(identity),
                feed,
                feed == null ? null : RelativeTime.between(feed, now),
                feed == null);
    }
}
