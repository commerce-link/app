package pl.commercelink.web.nav;

import pl.commercelink.starter.security.UserRole;

import java.util.List;
import java.util.Set;

import static pl.commercelink.starter.security.UserRole.ADMIN;
import static pl.commercelink.starter.security.UserRole.SUPER_ADMIN;
import static pl.commercelink.starter.security.UserRole.USER;

public final class NavigationCatalog {

    private static final Set<UserRole> STORE_ROLES = Set.of(USER, ADMIN);

    private static final List<NavSection> SECTIONS = List.of(
            new NavSection("nav.group.sales", List.of(
                    new NavItem("offers", "nav.offer", "/dashboard/offers", "fa-tags", STORE_ROLES),
                    new NavItem("orders", "nav.orders", "/dashboard/orders", "fa-shopping-cart", STORE_ROLES),
                    new NavItem("clients", "nav.clients", "/dashboard/clients", "fa-users", STORE_ROLES))),
            new NavSection("nav.group.fulfilment", List.of(
                    new NavItem("fulfilment", "nav.fulfilment", "/dashboard/fulfilment/queue", "fa-tasks",
                            Set.of(ADMIN)),
                    new NavItem("fulfilmentQueue", "nav.fulfilment.queue", "/dashboard/fulfilment/queue", "fa-tasks",
                            Set.of(SUPER_ADMIN)),
                    new NavItem("deliveries", "nav.deliveries", "/dashboard/deliveries", "fa-truck", STORE_ROLES),
                    new NavItem("deliveriesQueue", "nav.deliveries.queue", "/dashboard/deliveries", "fa-truck",
                            Set.of(SUPER_ADMIN)),
                    new NavItem("rma", "nav.rma", "/dashboard/rma", "fa-undo-alt", STORE_ROLES))),
            new NavSection("nav.group.warehouse", List.of(
                    new NavItem("warehouse", "nav.warehouse", "/dashboard/warehouse", "fa-warehouse", STORE_ROLES),
                    new NavItem("warehouseDocuments", "nav.warehouse.documents", "/dashboard/warehouse-documents",
                            "fa-file-alt", STORE_ROLES))),
            new NavSection("nav.group.catalog", List.of(
                    new NavItem("catalogs", "nav.product.catalog", "/dashboard/catalogs", "fa-book", Set.of(ADMIN)),
                    new NavItem("inventory", "nav.inventory", "/dashboard/inventory", "fa-boxes",
                            Set.of(USER, ADMIN, SUPER_ADMIN)))),
            new NavSection("nav.group.finance", List.of(
                    new NavItem("payments", "nav.payments", "/dashboard/payments", "fa-credit-card", Set.of(ADMIN)),
                    new NavItem("reports", "nav.reports", "/dashboard/reports", "fa-chart-bar", Set.of(ADMIN)))),
            new NavSection("nav.group.administration", List.of(
                    new NavItem("stores", "nav.stores", "/dashboard/stores", "fa-shopping-bag", Set.of(SUPER_ADMIN)),
                    new NavItem("rmaCenters", "nav.rma.centers", "/dashboard/store/rma-centers", "fa-building",
                            Set.of(SUPER_ADMIN)))));

    private static final List<NavItem> FOOTER = List.of(
            new NavItem("settings", "nav.settings", "/dashboard/store", "fa-cog", Set.of(ADMIN)));

    private NavigationCatalog() {
    }

    public static List<NavSection> sections() {
        return SECTIONS;
    }

    public static List<NavItem> footer() {
        return FOOTER;
    }
}
