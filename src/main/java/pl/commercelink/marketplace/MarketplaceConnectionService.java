package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
public class MarketplaceConnectionService {

    private final StoresRepository storesRepository;
    private final MarketplaceProviderFactory providerFactory;
    private final ProviderConfigurationManager configurationManager;
    private final MarketplaceOrdersImportScheduler ordersImportScheduler;
    private final int minIntervalMinutes;

    public MarketplaceConnectionService(StoresRepository storesRepository,
                                        MarketplaceProviderFactory providerFactory,
                                        ProviderConfigurationManager configurationManager,
                                        MarketplaceOrdersImportScheduler ordersImportScheduler,
                                        @Value("${scheduling.min-interval-minutes}") int minIntervalMinutes) {
        this.storesRepository = storesRepository;
        this.providerFactory = providerFactory;
        this.configurationManager = configurationManager;
        this.ordersImportScheduler = ordersImportScheduler;
        this.minIntervalMinutes = minIntervalMinutes;
    }

    public int minIntervalMinutes() {
        return minIntervalMinutes;
    }

    public List<MarketplaceIntegrationView> views(Store store) {
        List<String> deviceAuthProviders = providerFactory.deviceAuthProviders();
        return store.getMarketplaces().stream()
                .map(integration -> new MarketplaceIntegrationView(
                        integration.getName(),
                        displayNameOf(integration.getName()),
                        integration.isLoggedIn(),
                        deviceAuthProviders.contains(integration.getName()),
                        integration.getLastFetchedAt(),
                        integration.getOrdersImportSchedule()))
                .toList();
    }

    public List<MarketplaceProviderDescriptor> availableMarketplaces(Store store) {
        return providerFactory.availableProviders().stream()
                .filter(descriptor -> store.getMarketplaceIntegration(descriptor.name()) == null)
                .toList();
    }

    public Map<String, Map<String, String>> configurationsForUI(Store store) {
        Map<String, Map<String, String>> configurations = new LinkedHashMap<>();
        for (MarketplaceProviderDescriptor descriptor : providerFactory.availableProviders()) {
            configurations.put(descriptor.name(), configurationManager.getConfigurationForUI(
                    store, providerFactory.resolveCredentialName(descriptor), descriptor));
        }
        return configurations;
    }

    public Set<String> marketplacesWithStoredConfiguration(Store store) {
        Set<String> stored = new LinkedHashSet<>();
        for (MarketplaceProviderDescriptor descriptor : providerFactory.availableProviders()) {
            if (hasStoredConfiguration(store, descriptor.name())) {
                stored.add(descriptor.name());
            }
        }
        return stored;
    }

    public ConnectionUpdateResult connectOrUpdate(Store store, String marketplace,
                                                  Map<String, String> configuration, String schedule) {
        MarketplaceProviderDescriptor descriptor = isBlank(marketplace) ? null : providerFactory.getDescriptor(marketplace);
        if (descriptor == null) {
            return ConnectionUpdateResult.errors(List.of(ErrorMessage.of("store.marketplaces.error.unknown", marketplace)));
        }
        Map<String, String> submitted = configuration == null ? Map.of() : configuration;
        String normalizedSchedule = PollingSchedule.normalizeOrNull(schedule);
        List<ErrorMessage> errors = new ArrayList<>();
        validateRequiredFields(store, descriptor, submitted, errors);
        validateSchedule(marketplace, normalizedSchedule, errors);
        if (!errors.isEmpty()) {
            return ConnectionUpdateResult.errors(errors);
        }

        providerFactory.saveConfiguration(store, marketplace, submitted);
        MarketplaceIntegration integration = store.connectMarketplace(
                marketplace, providerFactory.deviceAuthProviders().contains(marketplace));
        if (!Objects.equals(normalizedSchedule, integration.getOrdersImportSchedule())) {
            ordersImportScheduler.apply(store.getStoreId(), marketplace, normalizedSchedule);
            integration.setOrdersImportSchedule(normalizedSchedule);
        }
        storesRepository.save(store);
        return ConnectionUpdateResult.ok();
    }

    public ConnectionUpdateResult disconnect(Store store, String marketplace) {
        if (store.getMarketplaceIntegration(marketplace) == null) {
            return ConnectionUpdateResult.errors(
                    List.of(ErrorMessage.of("store.marketplaces.import.schedule.error.missing", marketplace)));
        }
        providerFactory.deleteConfiguration(store, marketplace);
        store.removeMarketplaceIntegration(marketplace);
        ordersImportScheduler.delete(store.getStoreId(), marketplace);
        storesRepository.save(store);
        return ConnectionUpdateResult.ok();
    }

    private void validateRequiredFields(Store store, MarketplaceProviderDescriptor descriptor,
                                        Map<String, String> submitted, List<ErrorMessage> errors) {
        boolean hasStored = hasStoredConfiguration(store, descriptor.name());
        for (ProviderField field : descriptor.configurationFields()) {
            if (!field.required()) {
                continue;
            }
            boolean preservedPassword = field.type() == ProviderField.FieldType.PASSWORD && hasStored;
            if (isBlank(submitted.get(field.key())) && !preservedPassword) {
                errors.add(ErrorMessage.of("store.marketplaces.error.requires.field", descriptor.name(), field.label()));
            }
        }
    }

    private void validateSchedule(String marketplace, String normalizedSchedule, List<ErrorMessage> errors) {
        if (normalizedSchedule == null) {
            return;
        }
        try {
            PollingSchedule.parse(normalizedSchedule, minIntervalMinutes);
        } catch (InvalidScheduleException e) {
            if (e.getReason() == InvalidScheduleException.Reason.TOO_FREQUENT) {
                errors.add(ErrorMessage.of("store.marketplaces.import.schedule.error.too.frequent",
                        marketplace, normalizedSchedule, minIntervalMinutes));
            } else {
                errors.add(ErrorMessage.of("store.marketplaces.import.schedule.error.invalid",
                        marketplace, normalizedSchedule));
            }
        }
    }

    private boolean hasStoredConfiguration(Store store, String marketplace) {
        return !providerFactory.loadConfiguration(store, marketplace).isEmpty();
    }

    private String displayNameOf(String marketplace) {
        MarketplaceProviderDescriptor descriptor = providerFactory.getDescriptor(marketplace);
        return descriptor != null ? descriptor.displayName() : marketplace;
    }

    public record ConnectionUpdateResult(List<ErrorMessage> errors) {

        static ConnectionUpdateResult errors(List<ErrorMessage> errors) {
            return new ConnectionUpdateResult(errors);
        }

        static ConnectionUpdateResult ok() {
            return new ConnectionUpdateResult(List.of());
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
