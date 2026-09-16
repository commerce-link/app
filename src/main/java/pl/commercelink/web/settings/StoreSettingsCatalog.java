package pl.commercelink.web.settings;

import pl.commercelink.starter.security.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static pl.commercelink.starter.security.UserRole.ADMIN;
import static pl.commercelink.starter.security.UserRole.SUPER_ADMIN;

public final class StoreSettingsCatalog {

    public static final String HOME_PATH = "/dashboard/store";

    private static final Set<UserRole> STORE_ADMINS = Set.of(ADMIN, SUPER_ADMIN);

    private static final List<SettingsSection> SECTIONS = List.of(
            new SettingsSection("store.settings.group.company", List.of(
                    new SettingsTile("companyDetails", "store.company.details",
                            "store.company.details.configure.information", "fa-building", "/company-details",
                            STORE_ADMINS),
                    new SettingsTile("branding", "store.branding", "store.branding.description",
                            "fa-paint-brush", "/branding", STORE_ADMINS))),
            new SettingsSection("store.settings.group.finance", List.of(
                    new SettingsTile("invoicing", "store.invoicing", "store.invoicing.description",
                            "fa-calculator", "/invoicing", STORE_ADMINS),
                    new SettingsTile("payments", "store.payments", "store.payments.description",
                            "fa-credit-card", "/payments", STORE_ADMINS))),
            new SettingsSection("store.settings.group.sales", List.of(
                    new SettingsTile("marketplaces", "store.marketplaces", "store.marketplaces.description",
                            "fa-globe", "/marketplaces", STORE_ADMINS),
                    new SettingsTile("categories", "store.categories", "store.categories.description",
                            "fa-sitemap", "/categories", STORE_ADMINS),
                    new SettingsTile("reporting", "store.reporting", "store.reporting.description",
                            "fa-chart-line", "/report", STORE_ADMINS))),
            new SettingsSection("store.settings.group.fulfilment", List.of(
                    new SettingsTile("fulfilment", "store.fulfilment.settings",
                            "store.fulfilment.settings.description", "fa-box", "/fulfilment", STORE_ADMINS),
                    new SettingsTile("warehouse", "store.warehouse", "store.warehouse.description",
                            "fa-warehouse", "/warehouse", STORE_ADMINS),
                    new SettingsTile("shipping", "store.shipping", "store.shipping.description",
                            "fa-shipping-fast", "/shipping", STORE_ADMINS))),
            new SettingsSection("store.settings.group.returns", List.of(
                    new SettingsTile("rma", "store.rma", "store.rma.description",
                            "fa-undo-alt", "/rma", STORE_ADMINS),
                    // the super admin manages the shared RMA centres from the Administration menu instead
                    new SettingsTile("rmaCenters", "nav.rma.centers", "rma.center.description",
                            "fa-map-marker-alt", "/rma-centers", Set.of(ADMIN)))),
            new SettingsSection("store.settings.group.communication", List.of(
                    new SettingsTile("notification", "store.notification",
                            "store.configure.notification.description", "fa-bell", "/notification", STORE_ADMINS),
                    new SettingsTile("emailTemplates", "nav.emailTemplates", "store.template.description",
                            "fa-envelope", "/email-templates", STORE_ADMINS))));

    private StoreSettingsCatalog() {
    }

    public static List<SettingsSection> sections() {
        return SECTIONS;
    }

    public static Optional<SettingsTile> tileAt(String relativePath) {
        return SECTIONS.stream()
                .flatMap(section -> section.tiles().stream())
                .filter(tile -> tile.relativePath().equals(relativePath))
                .findFirst();
    }

    public static String homeHref(UserRole role, String storeId) {
        return role == SUPER_ADMIN ? HOME_PATH + "/" + storeId : HOME_PATH;
    }

    /** Sections and tiles visible to the given role, each tile linked to its own settings page under homeHref. */
    public static List<SettingsSectionView> sectionsFor(UserRole role, String homeHref) {
        return SECTIONS.stream()
                .map(section -> new SettingsSectionView(section.messageKey(), section.tiles().stream()
                        .filter(tile -> tile.visibleFor(role))
                        .map(tile -> new SettingsTileView(tile, homeHref + tile.relativePath()))
                        .toList()))
                .filter(section -> !section.tiles().isEmpty())
                .toList();
    }
}
