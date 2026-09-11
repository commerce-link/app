package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierConnectionViewFactoryTest {

    private final StoreFeedRepository storeFeedRepository = mock(StoreFeedRepository.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final SupplierConnectionViewFactory factory =
            new SupplierConnectionViewFactory(storeFeedRepository, supplierRegistry);

    private Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private StoreSupplierConnection connection(String identity, ConnectionMode mode) {
        return new StoreSupplierConnection(identity, mode, true, true);
    }

    @Test
    void splitsExternalConnectionsFromManualOnes() {
        // given
        Store store = storeWith(connection("Elko", ConnectionMode.OWN),
                connection("manual:Hurtownia X", ConnectionMode.MANUAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external()).extracting(SupplierConnectionView::identity).containsExactly("Elko");
        assertThat(views.manual()).extracting(SupplierConnectionView::identity).containsExactly("manual:Hurtownia X");
    }

    @Test
    void takesTheManualLabelFromTheIdentityPrefix() {
        // given
        Store store = storeWith(connection("manual:Hurtownia X", ConnectionMode.MANUAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.manual().get(0).label()).isEqualTo("Hurtownia X");
    }

    @Test
    void matchesFeedTimestampsIgnoringCase() {
        // given
        Store store = storeWith(connection("Elko", ConnectionMode.OWN));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1"))
                .thenReturn(Map.of("elko", LocalDateTime.of(2026, 9, 11, 6, 0)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).feedLastModified()).isEqualTo(LocalDateTime.of(2026, 9, 11, 6, 0));
        assertThat(views.external().get(0).hasFeed()).isTrue();
    }

    @Test
    void reportsNoFeedForGlobalConnectionsEvenWhenAFileExists() {
        // given a leftover file from a previous own connection must not be shown as this store's feed
        Store store = storeWith(connection("Elko", ConnectionMode.GLOBAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1"))
                .thenReturn(Map.of("elko", LocalDateTime.of(2026, 9, 11, 6, 0)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).hasFeed()).isFalse();
    }

    @Test
    void marksConnectionsWhoseProviderIsNoLongerRegistered() {
        // given
        Store store = storeWith(connection("Retired", ConnectionMode.OWN));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(supplierRegistry.exists("Retired")).thenReturn(false);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).knownProvider()).isFalse();
    }

    @Test
    void returnsEmptyListsWhenTheStoreHasNoFulfilmentConfiguration() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external()).isEmpty();
        assertThat(views.manual()).isEmpty();
    }
}
