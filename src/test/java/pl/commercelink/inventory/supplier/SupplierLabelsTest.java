package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierLabelsTest {

    private final StoresRepository storesRepository = mock(StoresRepository.class);
    private final SupplierLabels labels = new SupplierLabels(storesRepository);

    private Store store(String storeId, StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId(storeId);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private StoreSupplierConnection labelled(String identity, ConnectionMode mode, String label) {
        StoreSupplierConnection connection = new StoreSupplierConnection(identity, mode);
        connection.setLabel(label);
        return connection;
    }

    @Test
    void labelOfPrefersTheStoredLabelThenTheLegacyShape() {
        // given / when / then
        assertThat(SupplierLabels.labelOf(labelled("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Kosatec B2B"))).isEqualTo("Kosatec B2B");
        assertThat(SupplierLabels.labelOf(new StoreSupplierConnection("Kosatec", ConnectionMode.OWN))).isEqualTo("Kosatec");
        assertThat(SupplierLabels.labelOf(new StoreSupplierConnection("manual:Asus", ConnectionMode.MANUAL))).isEqualTo("Asus");
        assertThat(SupplierLabels.labelOf(labelled("Kosatec", ConnectionMode.OWN, "  "))).isEqualTo("Kosatec");
    }

    @Test
    void mapResolvesKnownIdentitiesAndFallsBackToTheIdentity() {
        // given
        Store store = store("s1",
                labelled("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Kosatec B2B"),
                new StoreSupplierConnection("manual:Asus", ConnectionMode.MANUAL));

        // when
        SupplierLabelMap map = labels.forStore(store);

        // then
        assertThat(map.of("Kosatec-k7f3a9c2")).isEqualTo("Kosatec B2B");
        assertThat(map.of("manual:Asus")).isEqualTo("Asus");
        assertThat(map.of("Warehouse")).isEqualTo("Warehouse");
        assertThat(map.of("Elko-gone0001")).isEqualTo("Elko-gone0001");
        assertThat(map.of(null)).isNull();
        assertThat(map.has("Kosatec-k7f3a9c2")).isTrue();
        assertThat(map.has("Warehouse")).isFalse();
    }

    @Test
    void mapForSeveralStoresKeysByStoreId() {
        // given
        when(storesRepository.findById("s1")).thenReturn(store("s1", labelled("Kosatec-aaaaaaaa", ConnectionMode.OWN, "A")));
        when(storesRepository.findById("s2")).thenReturn(store("s2", labelled("Kosatec-aaaaaaaa", ConnectionMode.OWN, "B")));

        // when
        SupplierLabelMap map = labels.forStoreIds(List.of("s1", "s2"));

        // then
        assertThat(map.of("s1", "Kosatec-aaaaaaaa")).isEqualTo("A");
        assertThat(map.of("s2", "Kosatec-aaaaaaaa")).isEqualTo("B");
    }

    @Test
    void optionsListEnabledConnectionsSortedByLabel() {
        // given
        StoreSupplierConnection disabled = labelled("manual-zzzzzzzz", ConnectionMode.MANUAL, "Zeta");
        disabled.setEnabled(false);
        Store store = store("s1",
                labelled("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Kosatec B2B"),
                new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL),
                disabled);

        // when
        List<SupplierLabelMap.Option> options = labels.forStore(store).options();

        // then
        assertThat(options).extracting(SupplierLabelMap.Option::identity).containsExactly("Acme", "Kosatec-k7f3a9c2");
        assertThat(options).extracting(SupplierLabelMap.Option::label).containsExactly("Acme", "Kosatec B2B");
    }
}
