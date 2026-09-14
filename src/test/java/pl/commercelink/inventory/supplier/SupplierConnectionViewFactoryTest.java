package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierConnectionViewFactoryTest {

    private final StoreFeedRepository storeFeedRepository = mock(StoreFeedRepository.class);
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final SupplierConnectionViewFactory factory =
            new SupplierConnectionViewFactory(storeFeedRepository, inventoryRepository, supplierRegistry);

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
    void carriesTheStoredFeedScheduleOntoTheRow() {
        // given -- the row is what the modal reads the current schedule back from
        StoreSupplierConnection elko = connection("Elko", ConnectionMode.OWN);
        elko.setFeedSchedule("0 5,17 * * ? *");
        Store store = storeWith(elko);
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        SupplierConnectionView row = views.external().get(0);
        assertThat(row.feedSchedule()).isEqualTo("0 5,17 * * ? *");
        assertThat(row.hasSchedule()).isTrue();
        assertThat(row.scheduleDescription().code()).isEqualTo("store.supplier.schedule.summary.at");
    }

    @Test
    void aGlobalConnectionHasNoScheduleOfItsOwn() {
        // given -- global connections ride the platform-wide feed
        Store store = storeWith(connection("Elko", ConnectionMode.GLOBAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(inventoryRepository.getLatestModifiedPerSupplier()).thenReturn(Map.of());
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).hasSchedule()).isFalse();
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
    void globalConnectionIgnoresALeftoverFileInTheStoreNamespace() {
        // given a leftover file from a previous own connection must not be shown as this store's
        // feed - the global connection is served by the platform-wide bucket, which has no entry
        Store store = storeWith(connection("Elko", ConnectionMode.GLOBAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1"))
                .thenReturn(Map.of("elko", LocalDateTime.of(2026, 9, 11, 6, 0)));
        when(inventoryRepository.getLatestModifiedPerSupplier()).thenReturn(Map.of());
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).hasFeed()).isFalse();
        assertThat(views.external().get(0).feedLastModified()).isNull();
    }

    @Test
    void globalConnectionResolvesTimestampFromTheGlobalListingNotTheStoreListing() {
        // given the store listing carries a different, older timestamp under the same key, so a
        // pass finding it there instead of in the global listing would be caught by this assertion
        Store store = storeWith(connection("Elko", ConnectionMode.GLOBAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1"))
                .thenReturn(Map.of("elko", LocalDateTime.of(2020, 1, 1, 0, 0)));
        when(inventoryRepository.getLatestModifiedPerSupplier())
                .thenReturn(Map.of("elko", LocalDateTime.of(2026, 9, 11, 6, 0)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).feedLastModified()).isEqualTo(LocalDateTime.of(2026, 9, 11, 6, 0));
        assertThat(views.external().get(0).hasFeed()).isTrue();
    }

    @Test
    void globalConnectionAbsentFromTheGlobalListingReportsNoFeed() {
        // given
        Store store = storeWith(connection("Elko", ConnectionMode.GLOBAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(inventoryRepository.getLatestModifiedPerSupplier())
                .thenReturn(Map.of("otherSupplier", LocalDateTime.of(2026, 9, 11, 6, 0)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).hasFeed()).isFalse();
        assertThat(views.external().get(0).feedLastModified()).isNull();
    }

    @Test
    void globalConnectionMatchesTheGlobalListingCaseInsensitively() {
        // given
        Store store = storeWith(connection("ELKO", ConnectionMode.GLOBAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(inventoryRepository.getLatestModifiedPerSupplier())
                .thenReturn(Map.of("elko", LocalDateTime.of(2026, 9, 11, 6, 0)));
        when(supplierRegistry.exists("ELKO")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).feedLastModified()).isEqualTo(LocalDateTime.of(2026, 9, 11, 6, 0));
    }

    @Test
    void ownConnectionResolvesFromTheStoreListingAndIsNotAffectedByASameNamedGlobalEntry() {
        // given a global entry for the same supplier name carries a different timestamp - if the
        // implementation ever mixed up which map an OWN connection reads from, this would catch it
        Store store = storeWith(connection("Elko", ConnectionMode.OWN));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1"))
                .thenReturn(Map.of("elko", LocalDateTime.of(2026, 9, 11, 6, 0)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        SupplierConnectionViewFactory.SupplierConnectionViews views = factory.views(store);

        // then
        assertThat(views.external().get(0).feedLastModified()).isEqualTo(LocalDateTime.of(2026, 9, 11, 6, 0));
        verify(inventoryRepository, never()).getLatestModifiedPerSupplier();
    }

    @Test
    void globalListingIsNotConsultedWhenTheStoreHasNoGlobalConnections() {
        // given
        Store store = storeWith(connection("Elko", ConnectionMode.OWN),
                connection("manual:Hurtownia X", ConnectionMode.MANUAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(supplierRegistry.exists("Elko")).thenReturn(true);

        // when
        factory.views(store);

        // then
        verify(inventoryRepository, never()).getLatestModifiedPerSupplier();
    }

    @Test
    void globalListingIsFetchedAtMostOnceEvenWithSeveralGlobalConnections() {
        // given
        Store store = storeWith(connection("Elko", ConnectionMode.GLOBAL),
                connection("Kosatec", ConnectionMode.GLOBAL));
        when(storeFeedRepository.feedLastModifiedByIdentity("store-1")).thenReturn(Map.of());
        when(inventoryRepository.getLatestModifiedPerSupplier()).thenReturn(Map.of());
        when(supplierRegistry.exists("Elko")).thenReturn(true);
        when(supplierRegistry.exists("Kosatec")).thenReturn(true);

        // when
        factory.views(store);

        // then
        verify(inventoryRepository, times(1)).getLatestModifiedPerSupplier();
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
