package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryStatistics;
import pl.commercelink.inventory.SupplierOfferStats;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventorySourcesViewFactoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock
    private SupplierConnectionViewFactory connectionViews;

    @InjectMocks
    private InventorySourcesViewFactory factory;

    private StoreSupplierConnection connection(String name, ConnectionMode mode, boolean enabled) {
        StoreSupplierConnection connection = mock(StoreSupplierConnection.class);
        when(connection.isEnabled()).thenReturn(enabled);
        if (enabled) {
            // a disabled connection is filtered out before its supplier name or mode is ever read
            when(connection.getSupplierName()).thenReturn(name);
            when(connection.getMode()).thenReturn(mode);
        }
        return connection;
    }

    private SupplierConnectionView view(String identity, String label, ConnectionMode mode, LocalDateTime feed) {
        return new SupplierConnectionView(identity, identity, label, mode, true, true, true, feed, null, null, null, true);
    }

    private Store storeWith(List<StoreSupplierConnection> connections, List<SupplierConnectionView> external,
                            List<SupplierConnectionView> manual) {
        Store store = mock(Store.class);
        when(store.getSupplierConnections()).thenReturn(connections);
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(false);
        when(connectionViews.views(store)).thenReturn(new SupplierConnectionViewFactory.SupplierConnectionViews(external, manual));
        return store;
    }

    @Test
    void activeConnectionWithoutFeedFileNeedsAttentionAndIsListedFirst() {
        // given
        Store store = storeWith(
                List.of(connection("Elko", ConnectionMode.GLOBAL, true),
                        connection("manual-nowak", ConnectionMode.MANUAL, true),
                        connection("AB", ConnectionMode.GLOBAL, true),
                        connection("manual-old", ConnectionMode.MANUAL, false)),
                List.of(view("Elko", "Elko", ConnectionMode.GLOBAL, NOW.minusHours(3)),
                        view("AB", "AB", ConnectionMode.GLOBAL, NOW.minusHours(2))),
                List.of(view("manual-nowak", "Hurtownia Nowak", ConnectionMode.MANUAL, null)));
        InventoryStatistics stats = new InventoryStatistics(10, 5,
                Map.of("Elko", new SupplierOfferStats(7, 3), "AB", new SupplierOfferStats(4, 2)));

        // when
        InventorySourcesView view = factory.build(store, stats, NOW);

        // then
        assertThat(view.attention()).extracting(SourceRow::label).containsExactly("Hurtownia Nowak");
        assertThat(view.working()).extracting(SourceRow::label).containsExactly("AB", "Elko");
        assertThat(view.working().get(1).products()).isEqualTo(7);
        assertThat(view.working().get(1).feedAge()).isEqualTo(new RelativeTime("inventory.time.hours", 3));
        assertThat(view.activeSupplierCount()).isEqualTo(3);
        assertThat(view.connectedSupplierCount()).isEqualTo(4);
        assertThat(view.hasIssues()).isTrue();
    }

    @Test
    void collapsedBarNamesAtMostTwoIssues() {
        // given
        Store store = storeWith(
                List.of(connection("manual-a", ConnectionMode.MANUAL, true),
                        connection("manual-b", ConnectionMode.MANUAL, true),
                        connection("manual-c", ConnectionMode.MANUAL, true)),
                List.of(),
                List.of(view("manual-a", "A", ConnectionMode.MANUAL, null),
                        view("manual-b", "B", ConnectionMode.MANUAL, null),
                        view("manual-c", "C", ConnectionMode.MANUAL, null)));

        // when
        InventorySourcesView view = factory.build(store, InventoryStatistics.EMPTY, NOW);

        // then
        assertThat(view.collapsedIssues()).extracting(SourceRow::label).containsExactly("A", "B");
        assertThat(view.moreIssues()).isEqualTo(1);
    }

    @Test
    void storeWithExternalWarehouseIsFlagged() {
        // given
        Store store = storeWith(List.of(), List.of(), List.of());
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(true);

        // when
        InventorySourcesView view = factory.build(store, InventoryStatistics.EMPTY, NOW);

        // then
        assertThat(view.externalWarehouse()).isTrue();
        assertThat(view.hasSuppliers()).isFalse();
    }

    @Test
    void missingStoreGivesEmptyView() {
        // when / then
        assertThat(factory.build(null, InventoryStatistics.EMPTY, NOW)).isEqualTo(InventorySourcesView.EMPTY);
    }

    @Test
    void sourceCountIncludesTheWarehouseRow() {
        // given
        InventorySourcesView view = new InventorySourcesView(List.of(), List.of(), 2, 3, false);

        // when
        int count = view.sourceCount();

        // then
        assertThat(count).isEqualTo(3);
    }
}
