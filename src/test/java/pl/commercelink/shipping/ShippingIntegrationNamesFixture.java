package pl.commercelink.shipping;

import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Names every installed shipping provider DISPLAY_NAME, with the generic name from the real message bundles. */
public final class ShippingIntegrationNamesFixture {

    public static final String DISPLAY_NAME = "Furgonetka";

    private ShippingIntegrationNamesFixture() {
    }

    public static ShippingIntegrationNames names() {
        ShippingProviderDescriptor descriptor = mock(ShippingProviderDescriptor.class,
                withSettings().strictness(Strictness.LENIENT));
        when(descriptor.displayName()).thenReturn(DISPLAY_NAME);
        ShippingProviderFactory factory = mock(ShippingProviderFactory.class, withSettings().strictness(Strictness.LENIENT));
        when(factory.getDescriptor(anyString())).thenReturn(descriptor);
        return new ShippingIntegrationNames(factory, bundles());
    }

    public static ResourceBundleMessageSource bundles() {
        ResourceBundleMessageSource bundles = new ResourceBundleMessageSource();
        bundles.setBasename("messages");
        bundles.setDefaultEncoding("UTF-8");
        bundles.setFallbackToSystemLocale(false);
        bundles.setDefaultLocale(Locale.forLanguageTag("pl"));
        return bundles;
    }
}
