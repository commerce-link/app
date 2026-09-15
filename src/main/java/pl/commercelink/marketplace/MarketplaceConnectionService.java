package pl.commercelink.marketplace;

import lombok.extern.slf4j.Slf4j;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@Slf4j
public class MarketplaceConnectionService {

    private static final List<ErrorMessage> UPDATE_FAILED =
            List.of(ErrorMessage.of("store.marketplaces.error.update.failed"));

    private final StoresRepository storesRepository;
    private final MarketplaceProviderFactory providerFactory;
    private final ProviderConfigurationManager configurationManager;
    private final MarketplaceOrdersImportScheduler ordersImportScheduler;
    private final MarketplaceReturnsImportScheduler returnsImportScheduler;
    private final int minIntervalMinutes;

    public MarketplaceConnectionService(StoresRepository storesRepository,
                                        MarketplaceProviderFactory providerFactory,
                                        ProviderConfigurationManager configurationManager,
                                        MarketplaceOrdersImportScheduler ordersImportScheduler,
                                        MarketplaceReturnsImportScheduler returnsImportScheduler,
                                        @Value("${scheduling.min-interval-minutes}") int minIntervalMinutes) {
        this.storesRepository = storesRepository;
        this.providerFactory = providerFactory;
        this.configurationManager = configurationManager;
        this.ordersImportScheduler = ordersImportScheduler;
        this.returnsImportScheduler = returnsImportScheduler;
        this.minIntervalMinutes = minIntervalMinutes;
    }

    public int minIntervalMinutes() {
        return minIntervalMinutes;
    }

    public int defaultIntervalMinutes() {
        return ordersImportScheduler.defaultIntervalMinutes();
    }

    public int returnsDefaultIntervalMinutes() {
        return returnsImportScheduler.defaultIntervalMinutes();
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
                        integration.getOrdersImportSchedule(),
                        integration.getReturnsImportSchedule()))
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
                                                  Map<String, String> configuration, String schedule,
                                                  String returnsSchedule) {
        MarketplaceProviderDescriptor descriptor = isBlank(marketplace) ? null : providerFactory.getDescriptor(marketplace);
        if (descriptor == null) {
            return ConnectionUpdateResult.errors(List.of(ErrorMessage.of("store.marketplaces.error.unknown", marketplace)));
        }
        Map<String, String> submitted = configuration == null ? Map.of() : configuration;
        String normalizedSchedule = PollingSchedule.normalizeOrNull(schedule);
        String normalizedReturnsSchedule = PollingSchedule.normalizeOrNull(returnsSchedule);
        List<ErrorMessage> errors = new ArrayList<>();
        validateRequiredFields(store, descriptor, submitted, errors);
        validateSchedule(marketplace, normalizedSchedule, "store.marketplaces.import.schedule.error", errors);
        validateSchedule(marketplace, normalizedReturnsSchedule, "store.marketplaces.returns.schedule.error", errors);
        if (!errors.isEmpty()) {
            return ConnectionUpdateResult.errors(errors);
        }

        Deque<Runnable> compensations = new ArrayDeque<>();
        try {
            rememberSecret(store, descriptor, compensations);
            providerFactory.saveConfiguration(store, marketplace, submitted);
            boolean created = store.getMarketplaceIntegration(marketplace) == null;
            MarketplaceIntegration integration = store.connectMarketplace(
                    marketplace, providerFactory.deviceAuthProviders().contains(marketplace));
            if (created || !Objects.equals(normalizedSchedule, integration.getOrdersImportSchedule())) {
                rememberOrdersSchedule(store.getStoreId(), marketplace, compensations);
                ordersImportScheduler.apply(store.getStoreId(), marketplace, normalizedSchedule);
                integration.setOrdersImportSchedule(normalizedSchedule);
            }
            if (created || !Objects.equals(normalizedReturnsSchedule, integration.getReturnsImportSchedule())) {
                rememberReturnsSchedule(store.getStoreId(), marketplace, compensations);
                returnsImportScheduler.apply(store.getStoreId(), marketplace, normalizedReturnsSchedule);
                integration.setReturnsImportSchedule(normalizedReturnsSchedule);
            }
            storesRepository.save(store);
        } catch (RuntimeException e) {
            log.error("Saving marketplace {} for store {} failed, restoring the previous state",
                    marketplace, store.getStoreId(), e);
            compensate(compensations);
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        return ConnectionUpdateResult.ok();
    }

    public ConnectionUpdateResult disconnect(Store store, String marketplace) {
        if (store.getMarketplaceIntegration(marketplace) == null) {
            return ConnectionUpdateResult.errors(
                    List.of(ErrorMessage.of("store.marketplaces.import.schedule.error.missing", marketplace)));
        }
        Deque<Runnable> compensations = new ArrayDeque<>();
        try {
            MarketplaceProviderDescriptor descriptor = providerFactory.getDescriptor(marketplace);
            if (descriptor != null) {
                rememberSecret(store, descriptor, compensations);
            }
            providerFactory.deleteConfiguration(store, marketplace);
            rememberOrdersSchedule(store.getStoreId(), marketplace, compensations);
            ordersImportScheduler.delete(store.getStoreId(), marketplace);
            rememberReturnsSchedule(store.getStoreId(), marketplace, compensations);
            returnsImportScheduler.delete(store.getStoreId(), marketplace);
            store.removeMarketplaceIntegration(marketplace);
            storesRepository.save(store);
        } catch (RuntimeException e) {
            log.error("Disconnecting marketplace {} from store {} failed, restoring the previous state",
                    marketplace, store.getStoreId(), e);
            compensate(compensations);
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        return ConnectionUpdateResult.ok();
    }

    private void rememberSecret(Store store, MarketplaceProviderDescriptor descriptor, Deque<Runnable> compensations) {
        String credentialName = providerFactory.resolveCredentialName(descriptor);
        ProviderConfigurationManager.SecretSnapshot snapshot = configurationManager.snapshot(store, credentialName);
        compensations.push(() -> configurationManager.restore(store, credentialName, snapshot));
    }

    private void rememberOrdersSchedule(String storeId, String marketplace, Deque<Runnable> compensations) {
        Optional<String> before = ordersImportScheduler.snapshot(storeId, marketplace);
        compensations.push(() -> ordersImportScheduler.restore(storeId, marketplace, before));
    }

    private void rememberReturnsSchedule(String storeId, String marketplace, Deque<Runnable> compensations) {
        Optional<String> before = returnsImportScheduler.snapshot(storeId, marketplace);
        compensations.push(() -> returnsImportScheduler.restore(storeId, marketplace, before));
    }

    private void compensate(Deque<Runnable> compensations) {
        while (!compensations.isEmpty()) {
            try {
                compensations.pop().run();
            } catch (RuntimeException e) {
                log.error("Compensation step failed", e);
            }
        }
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

    private void validateSchedule(String marketplace, String normalizedSchedule, String errorPrefix,
                                  List<ErrorMessage> errors) {
        if (normalizedSchedule == null) {
            return;
        }
        try {
            PollingSchedule.parse(normalizedSchedule, minIntervalMinutes);
        } catch (InvalidScheduleException e) {
            if (e.getReason() == InvalidScheduleException.Reason.TOO_FREQUENT) {
                errors.add(ErrorMessage.of(errorPrefix + ".too.frequent", marketplace, normalizedSchedule, minIntervalMinutes));
            } else {
                errors.add(ErrorMessage.of(errorPrefix + ".invalid", marketplace, normalizedSchedule));
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
