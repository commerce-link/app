package pl.commercelink.web;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two installed marketplaces: Allegro, whose account is connected on its own page and which imports returns, and
 * CS-Cart, connected with keys only, whose adapter labels carry a trailing asterisk. Messages are the real Polish ones.
 */
final class MarketplacesFixture {

    static final Locale PL = Locale.forLanguageTag("pl");
    static final int MIN_INTERVAL = 15;

    private MarketplacesFixture() {
    }

    static MessageSource polishMessages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    static MarketplaceProviderDescriptor allegro() {
        MarketplaceProviderDescriptor descriptor = mock(MarketplaceProviderDescriptor.class);
        when(descriptor.name()).thenReturn("Allegro");
        when(descriptor.displayName()).thenReturn("Allegro");
        when(descriptor.supportsReturns()).thenReturn(true);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("clientId", "Client ID", FieldType.TEXT, true, "Client ID"),
                new ProviderField("clientSecret", "Client Secret", FieldType.PASSWORD, true, "******")));
        return descriptor;
    }

    static MarketplaceProviderDescriptor csCart() {
        MarketplaceProviderDescriptor descriptor = mock(MarketplaceProviderDescriptor.class);
        when(descriptor.name()).thenReturn("CsCart");
        when(descriptor.displayName()).thenReturn("CS-Cart Multi-Vendor");
        when(descriptor.supportsReturns()).thenReturn(false);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("apiUrl", "Adres sklepu *", FieldType.URL, true, "https://sklep.example.com"),
                new ProviderField("apiKey", "API Key *", FieldType.PASSWORD, true, "******"),
                new ProviderField("shippingId", "ID metody wysyłki", FieldType.NUMBER, false, "")));
        return descriptor;
    }

    /** Installs both marketplaces into the mocks and returns the component the pages use. */
    static MarketplaceConnections install(MarketplaceProviderFactory factory, MarketplaceConnectionService service,
                                          MarketplaceAuthorization authorization, ProductCatalogRepository catalogs,
                                          MessageSource messageSource) {
        MarketplaceProviderDescriptor allegro = allegro();
        MarketplaceProviderDescriptor csCart = csCart();
        when(factory.availableProviders()).thenReturn(List.of(allegro, csCart));
        when(factory.getDescriptor("Allegro")).thenReturn(allegro);
        when(factory.getDescriptor("CsCart")).thenReturn(csCart);
        when(factory.loadConfigurationForUI(any(), anyString())).thenReturn(new java.util.HashMap<>());
        when(authorization.supports("Allegro")).thenReturn(true);
        when(service.minIntervalMinutes()).thenReturn(MIN_INTERVAL);
        when(service.defaultIntervalMinutes()).thenReturn(10);
        when(service.returnsDefaultIntervalMinutes()).thenReturn(60);
        return new MarketplaceConnections(factory, service, authorization, catalogs, messageSource);
    }
}
