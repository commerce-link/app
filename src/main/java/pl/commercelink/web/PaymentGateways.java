package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.PaymentWebhookRegistry;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.provider.api.EventBinding.WebhookBinding;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.settings.PaymentGatewayView;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the payments settings pages need to know about the store's payment gateways: the installed ones, the gateways
 * the store uses (several, one of them the default), their adapter settings and the webhook address each gateway's own
 * panel has to call when a customer pays. Without that address no order is created after a payment.
 */
@Component
class PaymentGateways {

    private final PaymentProviderFactory paymentProviderFactory;
    private final MessageSource messageSource;
    private final String apiDomain;

    PaymentGateways(PaymentProviderFactory paymentProviderFactory, MessageSource messageSource,
                    @Value("${api.domain}") String apiDomain) {
        this.paymentProviderFactory = paymentProviderFactory;
        this.messageSource = messageSource;
        this.apiDomain = apiDomain;
    }

    List<PaymentProviderDescriptor> installed() {
        return paymentProviderFactory.availableProviders().stream()
                .sorted(Comparator.comparing(PaymentProviderDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Installed gateways the store does not use yet, the choice when adding one. */
    List<PaymentProviderDescriptor> addable(Store store) {
        return installed().stream()
                .filter(descriptor -> store.getPaymentIntegration(descriptor.name()) == null)
                .toList();
    }

    PaymentProviderDescriptor descriptor(String name) {
        return paymentProviderFactory.getDescriptor(name);
    }

    List<PaymentGatewayView> views(Store store, String gatewaysPath) {
        return store.getPayments().stream()
                .map(integration -> view(store, integration, gatewaysPath))
                .toList();
    }

    /** The adapter's name for the gateway, or the stored name when the adapter is not installed. */
    String displayName(String name) {
        PaymentProviderDescriptor descriptor = descriptor(name);
        return descriptor == null ? name : descriptor.displayName();
    }

    /** What disconnecting does to customers; the list's dialog and the confirmation page say the same. */
    String disconnectMessage(Store store, String name, Locale locale) {
        List<PaymentIntegration> gateways = store.getPayments();
        if (gateways.size() == 1) {
            return messageSource.getMessage("store.payments.gateway.disconnect.message.last", new Object[]{displayName(name)}, locale);
        }
        PaymentIntegration gateway = store.getPaymentIntegration(name);
        if (gateway != null && gateway.is_default()) {
            // Store.removePaymentIntegration hands the default to the first remaining gateway
            String next = gateways.stream().filter(g -> !g.getName().equals(name)).findFirst()
                    .map(PaymentIntegration::getName).orElse(name);
            return messageSource.getMessage("store.payments.gateway.disconnect.message.default",
                    new Object[]{displayName(name), displayName(next)}, locale);
        }
        return messageSource.getMessage("store.payments.gateway.disconnect.message", new Object[]{displayName(name)}, locale);
    }

    private PaymentGatewayView view(Store store, PaymentIntegration integration, String gatewaysPath) {
        String name = integration.getName();
        PaymentProviderDescriptor descriptor = descriptor(name);
        String base = gatewaysPath + "/" + name;
        return new PaymentGatewayView(name, descriptor == null ? name : descriptor.displayName(), integration.is_default(),
                descriptor != null, descriptor != null && configured(store, descriptor), base, base + "/default",
                base + "/disconnect");
    }

    private boolean configured(Store store, PaymentProviderDescriptor descriptor) {
        Map<String, String> stored = storedSettings(store, descriptor.name());
        return descriptor.configurationFields().stream()
                .filter(ProviderField::required)
                .allMatch(field -> field.type() == FieldType.PASSWORD
                        ? stored.containsKey(field.key())
                        : stored.get(field.key()) != null && !stored.get(field.key()).isBlank());
    }

    /** The gateway's settings, secrets present but blanked. */
    Map<String, String> storedSettings(Store store, String name) {
        return paymentProviderFactory.loadConfigurationForUI(store, name);
    }

    /** Secrets are only stored for a gateway the store already uses. */
    Set<String> storedSecretKeys(Store store, String name) {
        if (name == null || store.getPaymentIntegration(name) == null) {
            return Set.of();
        }
        List<ProviderField> fields = fieldsOf(name);
        Map<String, String> stored = storedSettings(store, name);
        return fields == null ? Set.of() : fields.stream()
                .filter(field -> field.type() == FieldType.PASSWORD && stored.containsKey(field.key()))
                .map(ProviderField::key)
                .collect(Collectors.toSet());
    }

    /** The adapter settings of a gateway, or null when it is not installed (or none is given). */
    List<ProviderField> fieldsOf(String name) {
        PaymentProviderDescriptor descriptor = descriptor(name);
        return descriptor == null ? null : descriptor.configurationFields();
    }

    /** The address the gateway's panel calls when a payment succeeds, or null when its adapter declares no webhook. */
    String webhookUrl(String storeId, PaymentProviderDescriptor descriptor) {
        return descriptor.bindings().stream()
                .filter(WebhookBinding.class::isInstance)
                .map(binding -> ((WebhookBinding<?>) binding).path())
                .findFirst()
                .map(path -> apiDomain + PaymentWebhookRegistry.WEBHOOK_PATH_PREFIX.replace("{storeId}", storeId) + path)
                .orElse(null);
    }

    void save(Store store, String name, Map<String, String> configuration, boolean makeDefault) {
        paymentProviderFactory.saveConfiguration(store, name, configuration);
        store.addPaymentIntegration(name);
        if (makeDefault) {
            store.setDefaultPaymentIntegration(name);
        }
    }

    void disconnect(Store store, String name) {
        paymentProviderFactory.deleteConfiguration(store, name);
        store.removePaymentIntegration(name);
    }
}
