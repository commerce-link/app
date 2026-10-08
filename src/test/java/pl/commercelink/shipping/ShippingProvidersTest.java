package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Every action on an existing shipment goes to the integration that created it, never to "the store's". */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingProvidersTest {

    @Mock private ShippingProviderFactory factory;
    @Mock private ShippingProvider furgonetka;
    @Mock private ShippingProvider allegro;

    private ShippingProviders providers;
    private Store store;

    @BeforeEach
    void setUp() {
        when(factory.getDescriptor("furgonetka")).thenReturn(mock(ShippingProviderDescriptor.class));
        when(factory.getDescriptor("allegro")).thenReturn(mock(ShippingProviderDescriptor.class));
        store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.addAdditionalShippingIntegration("allegro");
        when(factory.get(store, "furgonetka")).thenReturn(furgonetka);
        when(factory.get(store, "allegro")).thenReturn(allegro);
        providers = new ShippingProviders(factory);
    }

    private static Shipment shipment(String provider) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider(provider);
        shipment.setExternalId("ext-1");
        return shipment;
    }

    @Test
    void aShipmentGoesToTheIntegrationThatCreatedIt() {
        // when / then
        assertThat(providers.forShipment(store, shipment("allegro"))).containsSame(allegro);
        assertThat(providers.forShipment(store, shipment("furgonetka"))).containsSame(furgonetka);
    }

    @Test
    void aShipmentWithoutAnIntegrationGoesToTheDefault() {
        // when / then
        assertThat(providers.forShipment(store, shipment(null))).containsSame(furgonetka);
        assertThat(providers.nameFor(store, shipment(null))).isEqualTo("furgonetka");
        assertThat(providers.nameFor(store, shipment("allegro"))).isEqualTo("allegro");
    }

    @Test
    void anIntegrationTheStoreDoesNotHaveIsNotUsedEvenWhenInstalled() {
        // given
        store.removeAdditionalShippingIntegration("allegro");

        // when / then
        assertThat(providers.forName(store, "allegro")).isEmpty();
        assertThat(providers.forShipment(store, shipment("allegro"))).isEmpty();
        verify(factory, never()).get(store, "allegro");
    }

    @Test
    void anUninstalledAdapterIsNotUsed() {
        // given
        when(factory.getDescriptor("allegro")).thenReturn(null);

        // when / then
        assertThat(providers.forName(store, "allegro")).isEmpty();
        assertThat(providers.isInstalled("allegro")).isFalse();
    }

    @Test
    void aCommandWithoutAnIntegrationGoesToTheDefault() {
        // when / then
        assertThat(providers.forCommand(store, null)).containsSame(furgonetka);
        assertThat(providers.forCommand(store, "allegro")).containsSame(allegro);
        assertThat(providers.defaultFor(store)).containsSame(furgonetka);
    }

    @Test
    void aStoreWithoutADefaultHasNoneForShipmentsWithoutAnIntegration() {
        // given
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, null);

        // when / then
        assertThat(providers.defaultFor(store)).isEmpty();
        assertThat(providers.forShipment(store, shipment(null))).isEmpty();
        assertThat(providers.forCommand(store, null)).isEmpty();
        assertThat(providers.forName(store, "allegro")).containsSame(allegro);
    }

    @Test
    void disconnectingAnAdditionalIntegrationDropsItsSettingsAndEntryOnly() {
        // when
        providers.disconnectAdditional(store, "allegro");

        // then
        verify(factory).deleteConfiguration(store, "allegro");
        assertThat(store.hasShippingIntegration("allegro")).isFalse();
        assertThat(store.defaultShippingIntegration()).isEqualTo("furgonetka");
    }

    @Test
    void noStoreMeansNoProvider() {
        // when / then
        assertThat(providers.forName(null, "furgonetka")).isEmpty();
        assertThat(providers.forShipment(null, shipment("furgonetka"))).isEmpty();
    }
}
