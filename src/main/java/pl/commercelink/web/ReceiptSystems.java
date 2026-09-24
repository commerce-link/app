package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.receipts.ReceiptAttemptStore;
import pl.commercelink.receipts.ReceiptProviderFactory;
import pl.commercelink.receipts.ReceiptWebhookRegistry;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the e-receipt settings pages need to know about the store's e-receipt system: the installed systems, the
 * status shown on the receipts page and the adapter settings edited on the system subpage.
 */
@Component
class ReceiptSystems {

    private final ReceiptProviderFactory receiptProviderFactory;
    private final ReceiptAttemptStore attempts;
    private final String apiDomain;

    ReceiptSystems(ReceiptProviderFactory receiptProviderFactory, ReceiptAttemptStore attempts,
                   @Value("${api.domain}") String apiDomain) {
        this.receiptProviderFactory = receiptProviderFactory;
        this.attempts = attempts;
        this.apiDomain = apiDomain;
    }

    List<ReceiptProviderDescriptor> installed() {
        return receiptProviderFactory.availableProviders().stream()
                .sorted(Comparator.comparing(ReceiptProviderDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    String current(Store store) {
        return store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER);
    }

    /** Configured means every required setting is stored; nothing tests the connection. */
    IntegrationStatus status(Store store) {
        String providerName = current(store);
        if (providerName == null) {
            return IntegrationStatus.none();
        }
        ReceiptProviderDescriptor descriptor = receiptProviderFactory.getDescriptor(providerName);
        if (descriptor == null) {
            return new IntegrationStatus(providerName, providerName, false, false);
        }
        Map<String, String> stored = storedSettings(store);
        boolean configured = descriptor.configurationFields().stream()
                .filter(ProviderField::required)
                .allMatch(field -> field.type() == FieldType.PASSWORD
                        ? stored.containsKey(field.key())
                        : stored.get(field.key()) != null && !stored.get(field.key()).isBlank());
        return new IntegrationStatus(providerName, descriptor.displayName(), true, configured);
    }

    /** The current system's settings, secrets present but blanked. */
    Map<String, String> storedSettings(Store store) {
        return receiptProviderFactory.loadConfigurationForUI(store);
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
        ReceiptProviderDescriptor descriptor = receiptProviderFactory.getDescriptor(providerName);
        return descriptor == null ? null : descriptor.configurationFields();
    }

    /** The address the provider's panel calls with status updates, or null when its adapter declares no webhook. */
    String webhookUrl(String storeId, ReceiptProviderDescriptor descriptor) {
        return descriptor.bindings().stream()
                .filter(EventBinding.WebhookBinding.class::isInstance)
                .map(binding -> ((EventBinding.WebhookBinding<?>) binding).path())
                .findFirst()
                .map(path -> apiDomain + ReceiptWebhookRegistry.WEBHOOK_PATH_PREFIX.replace("{storeId}", storeId) + path)
                .orElse(null);
    }

    /** Attempts in flight need the stored access details until they are resolved. */
    boolean hasLiveAttempts(Store store) {
        return attempts.hasLiveAttempts(store.getStoreId());
    }

    /**
     * A system the store does not use yet starts from a clean secret (saving merges into an existing one, which would
     * bring back keys of an earlier connection), and the previous system's secret goes once the new one is saved.
     *
     * @throws ReceiptSystemBusyException when switching away from a provider that still has live attempts: they need
     *         its secret until they are resolved, and deleting it here would strand them
     */
    void save(Store store, String providerName, Map<String, String> configuration) {
        String previous = current(store);
        boolean switching = !providerName.equals(previous);
        if (switching && previous != null && hasLiveAttempts(store)) {
            throw new ReceiptSystemBusyException("Cannot switch away from " + previous + ": live attempts still need it");
        }
        if (switching) {
            receiptProviderFactory.deleteConfiguration(store, providerName);
        }
        receiptProviderFactory.saveConfiguration(store, providerName, configuration);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, providerName);
        if (switching && previous != null) {
            receiptProviderFactory.deleteConfiguration(store, previous);
        }
    }

    void disconnect(Store store, String providerName) {
        receiptProviderFactory.deleteConfiguration(store, providerName);
        store.removeIntegration(IntegrationType.RECEIPT_PROVIDER);
    }
}
