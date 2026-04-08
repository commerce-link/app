package pl.commercelink.payments;

import org.springframework.stereotype.Service;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.stores.IntegrationType;

@Service
public class PaymentProviderFactory extends ProviderFactory<PaymentProviderDescriptor, PaymentProvider> {

    public PaymentProviderFactory(ProviderConfigurationManager configurationManager) {
        super(PaymentProviderDescriptor.class, IntegrationType.PAYMENT_PROVIDER, configurationManager);
    }
}
