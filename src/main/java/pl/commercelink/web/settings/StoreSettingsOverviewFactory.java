package pl.commercelink.web.settings;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ReportingConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Describes the settings of a store for the settings home page. Every status is derived from the loaded
 * {@link Store} alone: no secrets, repositories or remote calls, so the page costs a single store read.
 */
@Component
@RequiredArgsConstructor
public class StoreSettingsOverviewFactory {

    private final InvoicingProviderFactory invoicingProviderFactory;
    private final PaymentProviderFactory paymentProviderFactory;
    private final ShippingProviderFactory shippingProviderFactory;

    public StoreSettingsOverview build(Store store, UserRole role) {
        String homeHref = StoreSettingsCatalog.homeHref(role, store.getStoreId());
        List<SettingsSectionView> sections = StoreSettingsCatalog.sections().stream()
                .map(section -> new SettingsSectionView(section.messageKey(), section.tiles().stream()
                        .filter(tile -> tile.visibleFor(role))
                        .map(tile -> new SettingsTileView(tile, homeHref + tile.relativePath(), statusOf(tile, store)))
                        .toList()))
                .filter(section -> !section.tiles().isEmpty())
                .toList();
        return new StoreSettingsOverview(sections);
    }

    private TileStatus statusOf(SettingsTile tile, Store store) {
        return switch (tile.key()) {
            case "companyDetails" -> companyDetailsStatus(store);
            case "branding" -> brandingStatus(store);
            case "invoicing" -> invoicingStatus(store);
            case "payments" -> paymentsStatus(store);
            case "marketplaces" -> marketplacesStatus(store);
            case "categories" -> categoriesStatus(store);
            case "reporting" -> reportingStatus(store);
            case "fulfilment" -> fulfilmentStatus(store);
            case "warehouse" -> warehouseStatus(store);
            case "shipping" -> shippingStatus(store);
            case "rma" -> rmaStatus(store);
            case "notification" -> notificationStatus(store);
            case "emailTemplates" -> emailTemplatesStatus(store);
            // RMA centres live in their own table; counting them would add a query to every page view
            case "rmaCenters" -> null;
            default -> throw new IllegalStateException("No status rule for settings tile " + tile.key());
        };
    }

    private TileStatus companyDetailsStatus(Store store) {
        BillingDetails details = store.getBillingDetails();
        return details != null && details.isProperlyFilled()
                ? TileStatus.ok("store.settings.status.companyDetails.complete")
                : TileStatus.warning("store.settings.status.companyDetails.incomplete");
    }

    private TileStatus brandingStatus(Store store) {
        Branding branding = store.getBranding();
        return branding != null && StringUtils.isNotBlank(branding.getLogo())
                ? TileStatus.ok("store.settings.status.branding.logo")
                : TileStatus.neutral("store.settings.status.branding.default");
    }

    private TileStatus invoicingStatus(Store store) {
        String provider = store.getConfigurationValue(IntegrationType.INVOICING_PROVIDER);
        if (StringUtils.isBlank(provider)) {
            return TileStatus.neutral("store.settings.status.notConnected");
        }
        return TileStatus.ok("store.settings.status.connected",
                displayName(invoicingProviderFactory.getDescriptor(provider), provider));
    }

    private TileStatus paymentsStatus(Store store) {
        List<PaymentIntegration> payments = orEmpty(store.getPayments());
        if (payments.isEmpty()) {
            return TileStatus.neutral("store.settings.status.payments.none");
        }
        return payments.stream()
                .filter(PaymentIntegration::is_default)
                .findFirst()
                .map(defaultPayment -> TileStatus.ok("store.settings.status.payments.activeWithDefault", payments.size(),
                        displayName(paymentProviderFactory.getDescriptor(defaultPayment.getName()), defaultPayment.getName())))
                .orElseGet(() -> TileStatus.ok("store.settings.status.payments.active", payments.size()));
    }

    private TileStatus marketplacesStatus(Store store) {
        List<MarketplaceIntegration> marketplaces = orEmpty(store.getMarketplaces());
        if (marketplaces.isEmpty()) {
            return TileStatus.neutral("store.settings.status.marketplaces.none");
        }
        long disconnected = marketplaces.stream().filter(marketplace -> !marketplace.isLoggedIn()).count();
        return disconnected > 0
                ? TileStatus.warning("store.settings.status.marketplaces.disconnected", (int) disconnected, marketplaces.size())
                : TileStatus.ok("store.settings.status.marketplaces.connected", marketplaces.size());
    }

    private TileStatus categoriesStatus(Store store) {
        List<String> categories = orEmpty(store.getEnabledCategories());
        return categories.isEmpty()
                ? TileStatus.neutral("store.settings.status.categories.none")
                : TileStatus.ok("store.settings.status.categories.enabled", categories.size());
    }

    private TileStatus reportingStatus(Store store) {
        ReportingConfiguration reporting = store.getReportingConfiguration();
        return reporting != null && reporting.isGoogleAdsEnabled()
                ? TileStatus.ok("store.settings.status.reporting.googleAds")
                : TileStatus.neutral("store.settings.status.reporting.disabled");
    }

    private TileStatus fulfilmentStatus(Store store) {
        int suppliers = orEmpty(store.getSupplierConnections()).size();
        return suppliers == 0
                ? TileStatus.neutral("store.settings.status.suppliers.none")
                : TileStatus.ok("store.settings.status.suppliers.count", suppliers);
    }

    private TileStatus warehouseStatus(Store store) {
        WarehouseConfiguration warehouse = store.getWarehouseConfiguration();
        if (warehouse == null || !warehouse.isComplete()) {
            return TileStatus.neutral("store.settings.status.warehouse.incomplete");
        }
        int printers = orEmpty(warehouse.getPrinters()).size();
        return printers == 0
                ? TileStatus.ok("store.settings.status.warehouse.complete")
                : TileStatus.ok("store.settings.status.warehouse.completeWithPrinters", printers);
    }

    private TileStatus shippingStatus(Store store) {
        String provider = store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER);
        if (StringUtils.isBlank(provider)) {
            return TileStatus.neutral("store.settings.status.notConnected");
        }
        String name = displayName(shippingProviderFactory.getDescriptor(provider), provider);
        ShippingConfiguration shipping = store.getShippingConfiguration();
        int carriers = shipping == null ? 0 : orEmpty(shipping.getAuthorizedCarriers()).size();
        return carriers == 0
                ? TileStatus.ok("store.settings.status.connected", name)
                : TileStatus.ok("store.settings.status.connectedWithCarriers", name, carriers);
    }

    private TileStatus rmaStatus(Store store) {
        RMAConfiguration rma = store.getRmaConfiguration();
        AuthorizedCarrier carrier = rma == null ? null : rma.getCarrier();
        if (carrier == null) {
            return TileStatus.neutral("store.settings.status.rma.noCarrier");
        }
        String name = StringUtils.defaultString(
                StringUtils.firstNonBlank(carrier.getDisplayName(), carrier.getName(), carrier.getId()));
        return TileStatus.ok("store.settings.status.rma.carrier", name);
    }

    private TileStatus notificationStatus(Store store) {
        ClientNotificationsConfiguration notifications = store.getClientNotificationsConfiguration();
        return notifications != null && StringUtils.isNotBlank(notifications.getSenderName())
                ? TileStatus.ok("store.settings.status.notification.sender", notifications.getSenderName())
                : TileStatus.neutral("store.settings.status.notification.none");
    }

    private TileStatus emailTemplatesStatus(Store store) {
        ClientNotificationsConfiguration notifications = store.getClientNotificationsConfiguration();
        Map<String, String> templates = notifications == null ? null : notifications.getSupportedTemplates();
        // count known types only: the map may still hold a template type that has since been removed
        long enabled = templates == null ? 0 : Arrays.stream(EmailNotificationType.values())
                .filter(type -> templates.containsKey(type.name()))
                .count();
        return TileStatus.neutral("store.settings.status.emailTemplates.enabled",
                (int) enabled, EmailNotificationType.values().length);
    }

    private static String displayName(ProviderDescriptor<?> descriptor, String providerName) {
        return descriptor != null && StringUtils.isNotBlank(descriptor.displayName())
                ? descriptor.displayName()
                : providerName;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
