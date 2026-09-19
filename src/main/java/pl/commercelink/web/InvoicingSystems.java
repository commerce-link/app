package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the invoicing settings pages need to know about the store's invoicing system: the installed systems, the status
 * shown on the invoicing page and the adapter settings edited on the system subpage.
 */
@Component
@RequiredArgsConstructor
class InvoicingSystems {

    private final InvoicingProviderFactory invoicingProviderFactory;

    List<InvoicingProviderDescriptor> installed() {
        return invoicingProviderFactory.availableProviders().stream()
                .sorted(Comparator.comparing(InvoicingProviderDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    String current(Store store) {
        return store.getConfigurationValue(IntegrationType.INVOICING_PROVIDER);
    }

    /** Configured means every required setting is stored; nothing tests the connection. */
    IntegrationStatus status(Store store) {
        String providerName = current(store);
        if (providerName == null) {
            return IntegrationStatus.none();
        }
        InvoicingProviderDescriptor descriptor = invoicingProviderFactory.getDescriptor(providerName);
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
        return invoicingProviderFactory.loadConfigurationForUI(store);
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
        InvoicingProviderDescriptor descriptor = invoicingProviderFactory.getDescriptor(providerName);
        return descriptor == null ? null : descriptor.configurationFields();
    }

    /**
     * A system the store does not use yet starts from a clean secret (saving merges into an existing one, which would
     * bring back keys of an earlier connection), and the previous system's secret goes once the new one is saved.
     */
    void save(Store store, String providerName, Map<String, String> configuration) {
        String previous = current(store);
        boolean switching = !providerName.equals(previous);
        if (switching) {
            invoicingProviderFactory.deleteConfiguration(store, providerName);
        }
        invoicingProviderFactory.saveConfiguration(store, providerName, configuration);
        store.setConfigurationValue(IntegrationType.INVOICING_PROVIDER, providerName);
        if (switching && previous != null) {
            invoicingProviderFactory.deleteConfiguration(store, previous);
        }
    }

    void disconnect(Store store, String providerName) {
        invoicingProviderFactory.deleteConfiguration(store, providerName);
        store.removeIntegration(IntegrationType.INVOICING_PROVIDER);
    }
}
