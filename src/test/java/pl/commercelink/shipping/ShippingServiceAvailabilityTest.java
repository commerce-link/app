package pl.commercelink.shipping;

import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.shipping.api.ShipmentAddress;
import pl.commercelink.orders.ShippingDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The courier is offered, priced and booked only for a store with a courier account; without one, no NPE. */
@ExtendWith(MockitoExtension.class)
class ShippingServiceAvailabilityTest {

    @Mock private ShippingProviderFactory shippingProviderFactory;
    @Mock private CarrierDictionary carrierDictionary;
    @Mock private ShippingProviders shippingProviders;
    @Mock private ShippingProvider provider;

    @InjectMocks
    private ShippingService shippingService;

    private static Store store(String provider) {
        Store store = new Store();
        store.setStoreId("store-1");
        if (provider != null) {
            store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, provider);
        }
        return store;
    }

    @Test
    void aStoreWithAnInstalledProviderCanBookACourier() {
        // given
        when(shippingProviderFactory.getDescriptor("furgonetka")).thenReturn(mock(ShippingProviderDescriptor.class));

        // when / then
        assertThat(shippingService.isAvailable(store("furgonetka"))).isTrue();
    }

    @Test
    void aStoreWithoutAProviderOrWithAnUninstalledOneCannot() {
        // when / then
        assertThat(shippingService.isAvailable(store(null))).isFalse();
        assertThat(shippingService.isAvailable(store("removed-adapter"))).isFalse();
        assertThat(shippingService.isAvailable(null)).isFalse();
    }

    private static Order orderFrom(String source, OrderSourceType type) {
        Order order = new Order("store-1");
        order.setExternalOrderId("29a9b8c0-a87a-11f1-8456-8d3ada2e8e1c");
        order.setSource(new OrderSource(source, type));
        return order;
    }

    @Test
    void anAllegroOrderOfAStoreWithOnlyWysylamZAllegroCanBeShipped() {
        // given
        Store store = store(null);
        store.addAdditionalShippingIntegration("allegro");

        // when / then
        assertThat(shippingService.isAvailableFor(store, orderFrom("Allegro", OrderSourceType.Marketplace))).isTrue();
        assertThat(shippingService.isAvailableFor(store, orderFrom("Sklep", OrderSourceType.Other))).isFalse();
    }

    @Test
    void aStoreWithoutAnyIntegrationCannotShipAnAllegroOrder() {
        // when / then
        assertThat(shippingService.isAvailableFor(store(null), orderFrom("Allegro", OrderSourceType.Marketplace))).isFalse();
    }

    @Test
    void theDefaultIntegrationShipsEveryOrder() {
        // given
        when(shippingProviderFactory.getDescriptor("furgonetka")).thenReturn(mock(ShippingProviderDescriptor.class));

        // when / then
        assertThat(shippingService.isAvailableFor(store("furgonetka"), orderFrom("Sklep", OrderSourceType.Other))).isTrue();
    }

    @Test
    void pricingOrBookingWithoutAProviderFailsWithTheDomainReasonNotANullPointer() {
        // given
        Store store = store(null);
        ShippingForm form = new ShippingForm("order-1", "orders");
        DeliveryTarget target = new DeliveryTarget(null, null, null);

        // when / then
        assertThatThrownBy(() -> shippingService.estimateServicePrices(form, store, target))
                .isInstanceOf(ShippingUnavailableException.class);
        assertThatThrownBy(() -> shippingService.buildRequest(form, store, target))
                .isInstanceOf(ShippingUnavailableException.class);
    }

    @Test
    void aProviderWhoseAuthorisationWasLostIsNotAvailable() {
        // given: ShippingProviderFactory#onAuthorizationLost keeps the integration with no name
        Store store = store("furgonetka");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, null);

        // when / then
        assertThat(shippingService.isAvailable(store)).isFalse();
    }

    @Test
    void noLabelsWhenTheAdapterHandsNoneOut() {
        // given
        Store store = store("furgonetka");
        when(shippingProviders.forName(store, "furgonetka")).thenReturn(java.util.Optional.of(provider));
        when(provider.supportsLabels()).thenReturn(false);

        // when / then
        assertThat(shippingService.supportsLabels(store, "furgonetka")).isFalse();
    }

    @Test
    void noLabelsWhenTheIntegrationIsDisconnected() {
        // when / then
        assertThat(shippingService.supportsLabels(store("furgonetka"), "furgonetka")).isFalse();
    }

    @Test
    void labelsAreOfferedForEveryIntegrationOfTheStoreThatHandsThemOut() {
        // given
        Store store = store("furgonetka");
        ShippingProvider allegro = mock(ShippingProvider.class);
        when(allegro.supportsLabels()).thenReturn(true);
        when(shippingProviders.forName(store, "allegro")).thenReturn(java.util.Optional.of(allegro));
        when(shippingProviders.forName(store, "removed")).thenReturn(java.util.Optional.empty());

        // when / then
        assertThat(shippingService.supportsLabels(store, "allegro")).isTrue();
        assertThat(shippingService.supportsLabels(store, "removed")).isFalse();
        assertThat(shippingService.supportsLabels(store, null)).isFalse();
    }

    @Test
    void thePickupAddressIsTheStoreAddressTheShipmentLeavesFrom() {
        // given
        Store store = store("furgonetka");
        ShippingDetails address = new ShippingDetails();
        address.setId("addr-1");
        address.setStreetAndNumber("Testowa 1");
        address.setPostalCode("00-001");
        address.setCity("Warszawa");
        address.setCountry("PL");
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.setPickUpAddresses(new java.util.ArrayList<>(java.util.List.of(address)));
        store.setShippingConfiguration(configuration);

        // when
        ShipmentAddress pickup = shippingService.pickupAddress(store, "addr-1");

        // then
        assertThat(pickup.street()).isEqualTo("Testowa 1");
        assertThat(shippingService.pickupAddress(store, null)).isNull();
    }
}
