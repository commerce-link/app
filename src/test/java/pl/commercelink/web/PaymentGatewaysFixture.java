package pl.commercelink.web;

import org.springframework.context.MessageSource;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.EventBinding.WebhookBinding;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.provider.api.WebhookExecutor;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Two installed payment gateways, Stripe with a webhook and a bank without one, both needing an API key. */
final class PaymentGatewaysFixture {

    static final String API_DOMAIN = "https://api.commercelink.pl";

    private PaymentGatewaysFixture() {
    }

    static PaymentProviderDescriptor gateway(String name, String displayName, boolean webhook) {
        PaymentProviderDescriptor descriptor = mock(PaymentProviderDescriptor.class);
        when(descriptor.name()).thenReturn(name);
        when(descriptor.displayName()).thenReturn(displayName);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("apiKey", "API Key", FieldType.PASSWORD, true, ""),
                new ProviderField("shopId", "Shop ID", FieldType.TEXT, false, "")));
        List<EventBinding<?>> bindings = webhook ? List.of(new WebhookBinding<>(name, mock(WebhookExecutor.class))) : List.of();
        doReturn(bindings).when(descriptor).bindings();
        return descriptor;
    }

    static PaymentGateways install(PaymentProviderFactory factory, MessageSource messageSource) {
        PaymentProviderDescriptor stripe = gateway("stripe", "Stripe", true);
        PaymentProviderDescriptor bank = gateway("bank", "Raty Banku", false);
        when(factory.availableProviders()).thenReturn(List.of(stripe, bank));
        when(factory.getDescriptor("stripe")).thenReturn(stripe);
        when(factory.getDescriptor("bank")).thenReturn(bank);
        return new PaymentGateways(factory, messageSource, API_DOMAIN);
    }
}
