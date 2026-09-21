package pl.commercelink.marketplace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationType;
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
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@Slf4j
public class MarketplaceConnectionService {

    private static final List<ErrorMessage> UPDATE_FAILED =
            List.of(ErrorMessage.of("store.marketplaces.error.update.failed"));

    private final StoresRepository storesRepository;
    private final MarketplaceProviderFactory providerFactory;
    private final ProviderConfigurationManager configurationManager;
    private final ImportSchedule ordersSchedule;
    private final ImportSchedule returnsSchedule;
    private final StoreNotificationService notificationService;
    private final int minIntervalMinutes;

    public MarketplaceConnectionService(StoresRepository storesRepository,
                                        MarketplaceProviderFactory providerFactory,
                                        ProviderConfigurationManager configurationManager,
                                        MarketplaceOrdersImportScheduler ordersImportScheduler,
                                        MarketplaceReturnsImportScheduler returnsImportScheduler,
                                        StoreNotificationService notificationService,
                                        @Value("${scheduling.min-interval-minutes}") int minIntervalMinutes) {
        this.storesRepository = storesRepository;
        this.providerFactory = providerFactory;
        this.configurationManager = configurationManager;
        this.ordersSchedule = new ImportSchedule(ordersImportScheduler,
                MarketplaceIntegration::getOrdersImportSchedule, MarketplaceIntegration::setOrdersImportSchedule,
                "store.marketplaces.import.schedule.error");
        this.returnsSchedule = new ImportSchedule(returnsImportScheduler,
                MarketplaceIntegration::getReturnsImportSchedule, MarketplaceIntegration::setReturnsImportSchedule,
                "store.marketplaces.returns.schedule.error");
        this.notificationService = notificationService;
        this.minIntervalMinutes = minIntervalMinutes;
    }

    public int minIntervalMinutes() {
        return minIntervalMinutes;
    }

    public int defaultIntervalMinutes() {
        return ordersSchedule.scheduler().defaultIntervalMinutes();
    }

    public int returnsDefaultIntervalMinutes() {
        return returnsSchedule.scheduler().defaultIntervalMinutes();
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
        String normalizedReturnsSchedule = descriptor.supportsReturns() ? PollingSchedule.normalizeOrNull(returnsSchedule) : null;
        List<ErrorMessage> errors = new ArrayList<>();
        validateRequiredFields(store, descriptor, submitted, errors);
        validateSchedule(marketplace, normalizedSchedule, ordersSchedule, errors);
        validateSchedule(marketplace, normalizedReturnsSchedule, this.returnsSchedule, errors);
        if (!errors.isEmpty()) {
            return ConnectionUpdateResult.errors(errors);
        }

        boolean requiresDeviceAuth = providerFactory.deviceAuthProviders().contains(marketplace);
        Deque<Runnable> compensations = new ArrayDeque<>();
        try {
            rememberSecret(store, marketplace, compensations);
            providerFactory.saveConfiguration(store, marketplace, submitted);
            boolean created = store.getMarketplaceIntegration(marketplace) == null;
            MarketplaceIntegration integration = store.connectMarketplace(marketplace, requiresDeviceAuth);
            applyIfChanged(store, integration, ordersSchedule, normalizedSchedule, created, compensations);
            if (descriptor.supportsReturns()) {
                applyIfChanged(store, integration, this.returnsSchedule, normalizedReturnsSchedule, created, compensations);
            }
            storesRepository.save(store);
        } catch (RuntimeException e) {
            log.error("Saving marketplace {} for store {} failed, restoring the previous state",
                    marketplace, store.getStoreId(), e);
            compensate(compensations);
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        if (!requiresDeviceAuth) {
            clearExpiredConnectionNotification(store.getStoreId(), marketplace);
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
            // The secret is deleted whether or not the adapter is still installed, so it is always remembered first.
            rememberSecret(store, marketplace, compensations);
            providerFactory.deleteConfiguration(store, marketplace);
            deleteSchedule(store, marketplace, ordersSchedule, compensations);
            if (descriptor == null || descriptor.supportsReturns()) {
                deleteSchedule(store, marketplace, returnsSchedule, compensations);
            }
            store.removeMarketplaceIntegration(marketplace);
            storesRepository.save(store);
        } catch (RuntimeException e) {
            log.error("Disconnecting marketplace {} from store {} failed, restoring the previous state",
                    marketplace, store.getStoreId(), e);
            compensate(compensations);
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        clearExpiredConnectionNotification(store.getStoreId(), marketplace);
        return ConnectionUpdateResult.ok();
    }

    // runs after the store is saved and outside the compensations: a stale warning is not worth undoing a saved connection
    private void clearExpiredConnectionNotification(String storeId, String marketplace) {
        try {
            notificationService.resolve(storeId, StoreNotificationType.UNAUTHENTICATED,
                    StoreNotification.marketplaceConnectionObject(marketplace));
        } catch (RuntimeException e) {
            log.warn("Clearing the expired connection notification of marketplace {} in store {} failed",
                    marketplace, storeId, e);
        }
    }

    private void rememberSecret(Store store, String marketplace, Deque<Runnable> compensations) {
        String credentialName = providerFactory.resolveCredentialName(marketplace);
        ProviderConfigurationManager.SecretSnapshot snapshot = configurationManager.snapshot(store, credentialName);
        compensations.push(() -> configurationManager.restore(store, credentialName, snapshot));
    }

    private void applyIfChanged(Store store, MarketplaceIntegration integration, ImportSchedule schedule,
                                String normalized, boolean created, Deque<Runnable> compensations) {
        if (!created && Objects.equals(normalized, schedule.stored().apply(integration))) {
            return;
        }
        rememberSchedule(store, integration.getName(), schedule, compensations);
        schedule.scheduler().apply(store.getStoreId(), integration.getName(), normalized);
        schedule.store().accept(integration, normalized);
    }

    private void deleteSchedule(Store store, String marketplace, ImportSchedule schedule, Deque<Runnable> compensations) {
        rememberSchedule(store, marketplace, schedule, compensations);
        schedule.scheduler().delete(store.getStoreId(), marketplace);
    }

    private void rememberSchedule(Store store, String marketplace, ImportSchedule schedule, Deque<Runnable> compensations) {
        Optional<String> before = schedule.scheduler().snapshot(store.getStoreId(), marketplace);
        compensations.push(() -> schedule.scheduler().restore(store.getStoreId(), marketplace, before));
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

    private void validateSchedule(String marketplace, String normalizedSchedule, ImportSchedule schedule,
                                  List<ErrorMessage> errors) {
        if (normalizedSchedule == null) {
            return;
        }
        try {
            PollingSchedule.parse(normalizedSchedule, minIntervalMinutes);
        } catch (InvalidScheduleException e) {
            if (e.getReason() == InvalidScheduleException.Reason.TOO_FREQUENT) {
                errors.add(ErrorMessage.of(schedule.errorPrefix() + ".too.frequent", marketplace, normalizedSchedule, minIntervalMinutes));
            } else {
                errors.add(ErrorMessage.of(schedule.errorPrefix() + ".invalid", marketplace, normalizedSchedule));
            }
        }
    }

    private record ImportSchedule(MarketplaceImportScheduler scheduler,
                                  Function<MarketplaceIntegration, String> stored,
                                  BiConsumer<MarketplaceIntegration, String> store,
                                  String errorPrefix) {
    }

    private boolean hasStoredConfiguration(Store store, String marketplace) {
        return !providerFactory.loadConfiguration(store, marketplace).isEmpty();
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
