package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
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
    void labelsAreOfferedForThePackagesOfTheStoresOwnIntegrationWhenItsAdapterHandsThemOut() {
        // given
        Store store = store("furgonetka");
        when(shippingProviderFactory.getDescriptor("furgonetka")).thenReturn(mock(ShippingProviderDescriptor.class));
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        when(provider.supportsLabels()).thenReturn(true);

        // when / then
        assertThat(shippingService.supportsLabels(store, "furgonetka")).isTrue();
    }

    @Test
    void noLabelsForAPackageOfAnotherIntegrationThanTheStoresWithoutLoadingTheAccount() {
        // when / then: its label lives on an account the store has no access to
        assertThat(shippingService.supportsLabels(store("furgonetka"), "allegro")).isFalse();
        assertThat(shippingService.supportsLabels(store("furgonetka"), null)).isFalse();
        verifyNoInteractions(shippingProviderFactory);
    }

    @Test
    void noLabelsWhenTheAdapterHandsNoneOut() {
        // given
        Store store = store("furgonetka");
        when(shippingProviderFactory.getDescriptor("furgonetka")).thenReturn(mock(ShippingProviderDescriptor.class));
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        when(provider.supportsLabels()).thenReturn(false);

        // when / then
        assertThat(shippingService.supportsLabels(store, "furgonetka")).isFalse();
    }

    @Test
    void noLabelsWhenTheIntegrationIsDisconnected() {
        // when / then
        assertThat(shippingService.supportsLabels(store("furgonetka"), "furgonetka")).isFalse();
    }
}
