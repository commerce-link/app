package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingIntegrationNamesTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private ShippingProviderFactory providerFactory;
    @Mock private ShippingProviderDescriptor furgonetka;
    @Mock private ShippingProviderDescriptor allegro;

    private ShippingIntegrationNames names;

    @BeforeEach
    void setUp() {
        when(furgonetka.displayName()).thenReturn("Furgonetka");
        when(allegro.displayName()).thenReturn("Wysyłam z Allegro");
        when(providerFactory.getDescriptor("furgonetka")).thenReturn(furgonetka);
        when(providerFactory.getDescriptor("allegro")).thenReturn(allegro);
        names = new ShippingIntegrationNames(providerFactory, ShippingIntegrationNamesFixture.bundles());
    }

    private static Store storeShippingThrough(String provider) {
        Store store = new Store();
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, provider);
        return store;
    }

    @Test
    void aShipmentIsNamedAfterTheIntegrationThatCreatedItNotTheStoresCurrentOne() {
        // when
        String name = names.of("allegro", storeShippingThrough("furgonetka"), PL);

        // then
        assertThat(name).isEqualTo("Wysyłam z Allegro");
    }

    @Test
    void aShipmentWithoutAProviderIsNamedAfterTheStoresIntegration() {
        // when
        String name = names.of(null, storeShippingThrough("furgonetka"), PL);

        // then
        assertThat(name).isEqualTo("Furgonetka");
    }

    @Test
    void withoutAnyKnownIntegrationTheNameIsGenericInTheViewersLanguage() {
        // given
        Store withoutIntegration = new Store();

        // when / then
        assertThat(names.of(null, withoutIntegration, PL)).isEqualTo("integracja wysyłki");
        assertThat(names.of(null, null, Locale.ENGLISH)).isEqualTo("the shipping integration");
    }

    @Test
    void aProviderWhoseAdapterIsGoneIsNotNamedAfterTheStoresOtherIntegration() {
        // when
        String name = names.of("uninstalled", storeShippingThrough("furgonetka"), PL);

        // then
        assertThat(name).isEqualTo("integracja wysyłki");
    }

    @Test
    void withoutTheStoreAtHandTheProviderStillNamesTheShipment() {
        // when / then
        assertThat(names.of("furgonetka", PL)).isEqualTo("Furgonetka");
        assertThat(names.of(null, PL)).isEqualTo("integracja wysyłki");
    }
}
