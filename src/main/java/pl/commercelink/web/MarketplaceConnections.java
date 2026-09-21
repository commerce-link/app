package pl.commercelink.web;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.scheduling.PollingScheduleDescription;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.settings.MarketplaceView;
import pl.commercelink.web.settings.MarketplaceView.State;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * What the marketplaces settings pages need to know about the store's marketplaces: the installed ones, the connected
 * ones with their state and schedules, the catalogs that send offers to each, and their adapter settings. Saving and
 * disconnecting go through {@link MarketplaceConnectionService}, which also keeps the import schedules in step.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class MarketplaceConnections {

    private static final DateTimeFormatter FETCHED_AT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final MarketplaceProviderFactory providerFactory;
    private final MarketplaceConnectionService connectionService;
    private final MarketplaceAuthorization authorization;
    private final ProductCatalogRepository catalogRepository;
    private final MessageSource messageSource;

    List<MarketplaceProviderDescriptor> installed() {
        return providerFactory.availableProviders().stream()
                .sorted(Comparator.comparing(MarketplaceProviderDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Installed marketplaces the store is not connected to yet, the choice when adding one. */
    List<MarketplaceProviderDescriptor> addable(Store store) {
        return installed().stream()
                .filter(descriptor -> store.getMarketplaceIntegration(descriptor.name()) == null)
                .toList();
    }

    MarketplaceProviderDescriptor descriptor(String name) {
        return providerFactory.getDescriptor(name);
    }

    /** The adapter's name for the marketplace, or the stored name when the adapter is not installed. */
    String displayName(String name) {
        MarketplaceProviderDescriptor descriptor = descriptor(name);
        return descriptor == null ? name : descriptor.displayName();
    }

    /** Whether the account is connected on the marketplace's own page (device flow) rather than with keys only. */
    boolean authorizedOnMarketplace(String name) {
        return authorization.supports(name);
    }

    List<MarketplaceView> views(Store store, String marketplacesPath, Locale locale) {
        if (store.getMarketplaces().isEmpty()) {
            return List.of();
        }
        List<ProductCatalog> catalogs = catalogRepository.findAll(store.getStoreId());
        return store.getMarketplaces().stream()
                .map(integration -> view(integration, catalogs, marketplacesPath, locale))
                .toList();
    }

    private MarketplaceView view(MarketplaceIntegration integration, List<ProductCatalog> catalogs, String marketplacesPath,
                                 Locale locale) {
        String name = integration.getName();
        MarketplaceProviderDescriptor descriptor = descriptor(name);
        boolean deviceFlow = descriptor != null && authorizedOnMarketplace(name);
        State state = descriptor == null ? State.MISSING
                : integration.isLoggedIn() ? State.ACTIVE
                : deviceFlow && integration.getLastFetchedAt() == null ? State.NOT_AUTHORIZED
                : State.EXPIRED;
        String base = marketplacesPath + "/" + name;
        return new MarketplaceView(name, displayName(name), state,
                ordersText(integration, locale),
                descriptor != null && descriptor.supportsReturns() ? returnsText(integration, locale) : null,
                catalogs.stream()
                        .filter(catalog -> catalog.isMarketplaceExportEnabled(name))
                        .map(catalog -> isBlank(catalog.getName()) ? catalog.getCatalogId() : catalog.getName())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList(),
                base, deviceFlow ? base + "/authorize" : null, base + "/disconnect",
                marketplacesPath + "/exports/" + name);
    }

    private String ordersText(MarketplaceIntegration integration, Locale locale) {
        String schedule = scheduleText(integration.getOrdersImportSchedule(),
                "store.marketplaces.schedule.summary.default", connectionService.defaultIntervalMinutes(), locale);
        String fetched = integration.getLastFetchedAt() == null
                ? messageSource.getMessage("store.marketplaces.list.neverFetched", null, locale)
                : messageSource.getMessage("store.marketplaces.list.lastFetched",
                        new Object[]{FETCHED_AT.format(integration.getLastFetchedAt())}, locale);
        return messageSource.getMessage("store.marketplaces.list.orders", new Object[]{schedule, fetched}, locale);
    }

    private String returnsText(MarketplaceIntegration integration, Locale locale) {
        String schedule = scheduleText(integration.getReturnsImportSchedule(),
                "store.marketplaces.returns.schedule.summary.default", connectionService.returnsDefaultIntervalMinutes(), locale);
        return messageSource.getMessage("store.marketplaces.list.returns", new Object[]{schedule}, locale);
    }

    private String scheduleText(String expression, String defaultKey, int defaultMinutes, Locale locale) {
        if (isBlank(expression)) {
            return messageSource.getMessage(defaultKey, new Object[]{defaultMinutes}, locale);
        }
        PollingScheduleDescription description = PollingScheduleDescription.of(expression);
        String text = messageSource.getMessage(description.code(), description.messageArgs(), locale);
        // A hand-written expression is named, not paraphrased (PollingScheduleDescription.CUSTOM)
        return description.code().endsWith(".custom") ? text + " (" + expression + ")" : text;
    }

    /** What disconnecting does; the list's dialog and the confirmation page say the same. */
    String disconnectMessage(String name, Locale locale) {
        MarketplaceProviderDescriptor descriptor = descriptor(name);
        String key = descriptor != null && descriptor.supportsReturns()
                ? "store.marketplaces.disconnect.message.returns" : "store.marketplaces.disconnect.message";
        return messageSource.getMessage(key, new Object[]{displayName(name)}, locale);
    }

    Map<String, String> disconnectMessages(Store store, Locale locale) {
        return store.getMarketplaces().stream()
                .collect(Collectors.toMap(MarketplaceIntegration::getName, i -> disconnectMessage(i.getName(), locale),
                        (a, b) -> a));
    }

    /** The marketplace's settings, secrets present but blanked. */
    Map<String, String> storedSettings(Store store, String name) {
        return providerFactory.loadConfigurationForUI(store, name);
    }

    /** Secrets are only stored for a marketplace the store is connected to. */
    Set<String> storedSecretKeys(Store store, String name) {
        if (name == null || store.getMarketplaceIntegration(name) == null) {
            return Set.of();
        }
        List<ProviderField> fields = fieldsOf(name);
        Map<String, String> stored = storedSettings(store, name);
        return fields == null ? Set.of() : fields.stream()
                .filter(field -> field.type() == FieldType.PASSWORD && stored.containsKey(field.key()))
                .map(ProviderField::key)
                .collect(Collectors.toSet());
    }

    /** The adapter settings of a marketplace, or null when it is not installed (or none is given). */
    List<ProviderField> fieldsOf(String name) {
        MarketplaceProviderDescriptor descriptor = descriptor(name);
        return descriptor == null ? null : descriptor.configurationFields();
    }

    MarketplaceConnectionService.ConnectionUpdateResult save(Store store, String name, Map<String, String> configuration,
                                                             String ordersSchedule, String returnsSchedule) {
        return connectionService.connectOrUpdate(store, name, configuration, ordersSchedule, returnsSchedule);
    }

    MarketplaceConnectionService.ConnectionUpdateResult disconnect(Store store, String name) {
        return connectionService.disconnect(store, name);
    }

    int minIntervalMinutes() {
        return connectionService.minIntervalMinutes();
    }

    int ordersDefaultIntervalMinutes() {
        return connectionService.defaultIntervalMinutes();
    }

    int returnsDefaultIntervalMinutes() {
        return connectionService.returnsDefaultIntervalMinutes();
    }
}
