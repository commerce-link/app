package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.ShippingProviders;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Wysyłam z Allegro is switched on only over a working Allegro connection that has the shipments consent. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AllegroShippingSettingsTest {

    @Mock private ShippingProviderFactory factory;
    @Mock private ShippingProviders shippingProviders;
    @Mock private ShippingProvider allegro;

    private AllegroShippingSettings settings;
    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.setMarketplaces(new ArrayList<>(List.of(connectedAllegro())));
        when(shippingProviders.isInstalled("allegro")).thenReturn(true);
        when(factory.get(store, "allegro")).thenReturn(allegro);
        when(allegro.getAvailableCarriers()).thenReturn(List.of(new Carrier("DPD", "DPD", "DPD")));
        settings = new AllegroShippingSettings(factory, shippingProviders);
    }

    private static MarketplaceIntegration connectedAllegro() {
        MarketplaceIntegration allegroMarketplace = new MarketplaceIntegration();
        allegroMarketplace.setName("Allegro");
        allegroMarketplace.setLoggedIn(true);
        return allegroMarketplace;
    }

    @Test
    void withoutTheAllegroMarketplaceNothingIsAskedAndItCannotBeEnabled() {
        // given
        store.setMarketplaces(new ArrayList<>());

        // when / then
        assertThat(settings.status(store)).isEqualTo(AllegroShippingStatus.MARKETPLACE_NOT_CONNECTED);
        verifyNoInteractions(allegro);
        assertThatThrownBy(() -> settings.enable(store, AllegroShippingLabelFormat.PDF_A6))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.hasShippingIntegration("allegro")).isFalse();
    }

    @Test
    void aForbiddenAnswerMeansTheShipmentsConsentIsMissing() {
        // given
        when(allegro.getAvailableCarriers()).thenThrow(new ShippingException("forbidden",
                new HttpClientException(403, "{\"errors\":[{\"code\":\"AccessDenied\"}]}")));

        // when / then
        assertThat(settings.status(store)).isEqualTo(AllegroShippingStatus.MISSING_SHIPMENTS_CONSENT);
    }

    @Test
    void anyOtherFailureIsACheckThatDidNotWork() {
        // given
        when(allegro.getAvailableCarriers()).thenThrow(new RuntimeException("timeout"));

        // when / then
        assertThat(settings.status(store)).isEqualTo(AllegroShippingStatus.CHECK_FAILED);
    }

    @Test
    void aWorkingConnectionIsReadyAndThenEnabled() {
        // when
        AllegroShippingStatus before = settings.status(store);
        settings.enable(store, AllegroShippingLabelFormat.ZPL);

        // then
        assertThat(before).isEqualTo(AllegroShippingStatus.READY);
        verify(factory).saveConfiguration(store, "allegro", Map.of("labelFormat", "ZPL"));
        assertThat(store.hasShippingIntegration("allegro")).isTrue();
        assertThat(settings.status(store)).isEqualTo(AllegroShippingStatus.ENABLED);
    }

    @Test
    void theSummaryNeverCallsAllegro() {
        // given
        store.addAdditionalShippingIntegration("allegro");
        when(factory.loadConfiguration(store, "allegro")).thenReturn(Map.of("labelFormat", "PDF_A4"));

        // when
        AllegroShippingSettings.AllegroShippingSummary summary = settings.summary(store);

        // then
        assertThat(summary.installed()).isTrue();
        assertThat(summary.enabled()).isTrue();
        assertThat(summary.marketplaceConnected()).isTrue();
        assertThat(summary.labelFormat()).isEqualTo(AllegroShippingLabelFormat.PDF_A4);
        verifyNoInteractions(allegro);
    }

    @Test
    void anUnknownStoredFormatReadsAsTheDefault() {
        // given
        when(factory.loadConfiguration(store, "allegro")).thenReturn(Map.of("labelFormat", "EPL"));

        // when / then
        assertThat(settings.labelFormat(store)).isEqualTo(AllegroShippingLabelFormat.PDF_A6);
    }

    @Test
    void disablingDropsTheIntegrationThroughTheSharedRule() {
        // given
        store.addAdditionalShippingIntegration("allegro");

        // when
        settings.disable(store);

        // then
        verify(shippingProviders).disconnectAdditional(store, "allegro");
    }
}
