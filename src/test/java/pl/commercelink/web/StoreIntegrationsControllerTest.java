package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationCredentialsForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreIntegrationsControllerTest {

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private ShippingProviderFactory shippingProviderFactory;

    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private MarketplaceProviderFactory marketplaceProviderFactory;

    @Mock
    private MessageSource messageSource;

    @Mock
    private Store store;

    private MockedStatic<CustomSecurityContext> securityStub;

    private StoreIntegrationsController controller;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        lenient().when(store.getStoreId()).thenReturn("store-1");
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        controller = new StoreIntegrationsController(storesRepository, shippingProviderFactory,
                invoicingProviderFactory, paymentProviderFactory, marketplaceProviderFactory, messageSource);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    private IntegrationCredentialsForm marketplaceForm(String providerName) {
        IntegrationCredentialsForm form = new IntegrationCredentialsForm();
        form.setProviderType("marketplace");
        form.setProviderName(providerName);
        form.setProviderConfiguration(Map.of("clientId", "cid", "clientSecret", "sec"));
        return form;
    }

    @Test
    void savedDeviceFlowMarketplaceStartsAsRequiringAuthorization() {
        // given
        when(marketplaceProviderFactory.deviceAuthProviders()).thenReturn(List.of("Allegro"));
        when(store.getMarketplaceIntegration("Allegro")).thenReturn(null);
        List<MarketplaceIntegration> marketplaces = new ArrayList<>();
        when(store.getMarketplaces()).thenReturn(marketplaces);

        // when
        controller.saveIntegrationCredentials(marketplaceForm("Allegro"), Locale.getDefault(), new RedirectAttributesModelMap());

        // then
        assertEquals(1, marketplaces.size());
        assertFalse(marketplaces.get(0).isLoggedIn());
        verify(storesRepository).save(store);
    }

    @Test
    void savedManualTokenMarketplaceStartsConnected() {
        // given
        when(marketplaceProviderFactory.deviceAuthProviders()).thenReturn(List.of());
        when(store.getMarketplaceIntegration("Morele")).thenReturn(null);
        List<MarketplaceIntegration> marketplaces = new ArrayList<>();
        when(store.getMarketplaces()).thenReturn(marketplaces);

        // when
        controller.saveIntegrationCredentials(marketplaceForm("Morele"), Locale.getDefault(), new RedirectAttributesModelMap());

        // then
        assertTrue(marketplaces.get(0).isLoggedIn());
    }

    @Test
    void resavingCredentialsForDeviceFlowProviderDoesNotFakeRestoredConnection() {
        // given
        when(marketplaceProviderFactory.deviceAuthProviders()).thenReturn(List.of("Allegro"));
        when(store.getMarketplaceIntegration("Allegro")).thenReturn(new MarketplaceIntegration("Allegro"));

        // when
        controller.saveIntegrationCredentials(marketplaceForm("Allegro"), Locale.getDefault(), new RedirectAttributesModelMap());

        // then
        verify(store, never()).markConnectionAsRestored("Allegro");
        verify(storesRepository).save(store);
    }

    @Test
    void resavingCredentialsForManualTokenProviderRestoresConnection() {
        // given
        when(marketplaceProviderFactory.deviceAuthProviders()).thenReturn(List.of());
        when(store.getMarketplaceIntegration("Morele")).thenReturn(new MarketplaceIntegration("Morele"));

        // when
        controller.saveIntegrationCredentials(marketplaceForm("Morele"), Locale.getDefault(), new RedirectAttributesModelMap());

        // then
        verify(store).markConnectionAsRestored("Morele");
    }
}
