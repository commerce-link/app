package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the shipping settings pages need to know about the store's courier account (Furgonetka today): the installed
 * providers, the status shown on the shipping page, the adapter settings edited on the account subpage, the webhook
 * address the provider posts delivery updates to, and the carriers the account offers.
 */
@Component
class ShippingAccounts {

    /** Adapter setting holding the shared secret the provider signs its webhook calls with. */
    static final String WEBHOOK_TOKEN = "webhookToken";

    private static final int MAX_ERROR_LENGTH = 300;

    private final ShippingProviderFactory shippingProviderFactory;
    private final String apiDomain;

    ShippingAccounts(ShippingProviderFactory shippingProviderFactory, @Value("${api.domain}") String apiDomain) {
        this.shippingProviderFactory = shippingProviderFactory;
        this.apiDomain = apiDomain;
    }

    List<ShippingProviderDescriptor> installed() {
        return shippingProviderFactory.availableProviders().stream()
                .sorted(Comparator.comparing(ShippingProviderDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    String current(Store store) {
        return store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER);
    }

    /**
     * The value a required address setting takes when left empty: the adapter's example, when that is a full http(s)
     * address. ProviderField has no default value, and Furgonetka 0.3.0 requires "API URL" (example
     * https://api.furgonetka.pl) while reading FURGONETKA_API_URL instead, so an empty dead field blocked the save and
     * an account stored without it looked incomplete. Kept to the courier account: elsewhere an example address is an
     * example of the operator's own value (a shop URL), not a default.
     */
    static String defaultValue(ProviderField field) {
        String example = StringUtils.trimToNull(field.placeholder());
        boolean addressField = field.type() == FieldType.TEXT || field.type() == FieldType.URL;
        return field.required() && addressField && example != null && example.matches("https?://\\S+") ? example : null;
    }

    /** Puts the default value into every empty setting that has one, for each installed courier's fields. */
    void fillDefaults(IntegrationSettingsForm form) {
        for (ShippingProviderDescriptor descriptor : installed()) {
            for (ProviderField field : descriptor.configurationFields()) {
                String value = defaultValue(field);
                if (value != null) {
                    form.fillBlank(descriptor.name(), field, value);
                }
            }
        }
    }

    /** Configured means every required setting is stored (or has a default); nothing tests the connection. */
    IntegrationStatus status(Store store) {
        String providerName = current(store);
        if (providerName == null) {
            return IntegrationStatus.none();
        }
        ShippingProviderDescriptor descriptor = shippingProviderFactory.getDescriptor(providerName);
        if (descriptor == null) {
            return new IntegrationStatus(providerName, providerName, false, false);
        }
        Map<String, String> stored = storedSettings(store);
        boolean configured = descriptor.configurationFields().stream()
                .filter(ProviderField::required)
                .allMatch(field -> field.type() == FieldType.PASSWORD
                        ? stored.containsKey(field.key())
                        : StringUtils.isNotBlank(stored.get(field.key())) || defaultValue(field) != null);
        return new IntegrationStatus(providerName, descriptor.displayName(), true, configured);
    }

    /** The current provider's settings, secrets present but blanked. */
    Map<String, String> storedSettings(Store store) {
        return shippingProviderFactory.loadConfigurationForUI(store);
    }

    /** Secrets are loaded for the store's current provider only; another provider starts without any. */
    Set<String> storedSecretKeys(Store store, String providerName) {
        if (providerName == null || !providerName.equals(current(store))) {
            return Set.of();
        }
        Map<String, String> stored = storedSettings(store);
        List<ProviderField> fields = fieldsOf(providerName);
        return fields == null ? Set.of() : fields.stream()
                .filter(field -> field.type() == FieldType.PASSWORD && stored.containsKey(field.key()))
                .map(ProviderField::key)
                .collect(Collectors.toSet());
    }

    /** The adapter settings of a provider, or null when it is not installed (or none is given). */
    List<ProviderField> fieldsOf(String providerName) {
        if (providerName == null) {
            return null;
        }
        ShippingProviderDescriptor descriptor = shippingProviderFactory.getDescriptor(providerName);
        return descriptor == null ? null : descriptor.configurationFields();
    }

    /** As InvoicingSystems.save: a new courier starts from a clean secret, the previous one's secret goes after. */
    void save(Store store, String providerName, Map<String, String> configuration) {
        String previous = current(store);
        boolean switching = !providerName.equals(previous);
        if (switching) {
            shippingProviderFactory.deleteConfiguration(store, providerName);
        }
        shippingProviderFactory.saveConfiguration(store, providerName, configuration);
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, providerName);
        if (switching && previous != null) {
            shippingProviderFactory.deleteConfiguration(store, previous);
        }
    }

    void disconnect(Store store, String providerName) {
        shippingProviderFactory.deleteConfiguration(store, providerName);
        store.removeIntegration(IntegrationType.SHIPPING_PROVIDER);
    }

    /** True for a provider that reports deliveries by webhook, i.e. has a webhook token setting. */
    boolean usesWebhook(String providerName) {
        List<ProviderField> fields = fieldsOf(providerName);
        return fields != null && fields.stream().anyMatch(field -> WEBHOOK_TOKEN.equals(field.key()));
    }

    /** The address the provider posts delivery updates to, or null for a provider without webhooks. */
    String webhookUrl(String storeId, String providerName) {
        if (StringUtils.isBlank(providerName) || !usesWebhook(providerName)) {
            return null;
        }
        return StringUtils.removeEnd(apiDomain, "/") + "/Store/" + storeId + "/Webhooks/Shipping/" + providerName;
    }

    /**
     * True when the current provider takes webhooks but no token is stored, so the updates are not verified. Secrets
     * are blanked in the UI configuration, so the stored configuration tells "empty" from "hidden".
     */
    boolean webhookTokenMissing(Store store) {
        String providerName = current(store);
        if (!usesWebhook(providerName)) {
            return false;
        }
        Map<String, String> configuration = shippingProviderFactory.loadConfiguration(store, providerName);
        return configuration == null || StringUtils.isBlank(configuration.get(WEBHOOK_TOKEN));
    }

    /**
     * The carriers the store's account offers, asked from the provider. Empty when no provider is configured; a
     * failed call (wrong credentials, provider down) is reported, not thrown, so the page can say what happened.
     */
    CarrierLookup carriers(Store store) {
        if (!status(store).configured()) {
            return new CarrierLookup(List.of(), null);
        }
        try {
            ShippingProvider provider = shippingProviderFactory.get(store);
            if (provider == null) {
                return new CarrierLookup(List.of(), null);
            }
            List<Carrier> carriers = Optional.ofNullable(provider.getAvailableCarriers()).orElse(List.of());
            return new CarrierLookup(carriers, null);
        } catch (RuntimeException ex) {
            // The provider's message says what to fix; a long response body is cut so the page stays readable.
            String message = StringUtils.defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName());
            return new CarrierLookup(List.of(), StringUtils.abbreviate(message, MAX_ERROR_LENGTH));
        }
    }

    /** @param error the provider's failure, or null when the carriers were fetched */
    record CarrierLookup(List<Carrier> carriers, String error) {

        boolean failed() {
            return error != null;
        }
    }
}
