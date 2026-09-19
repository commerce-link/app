package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.IntegrationStatus;
import pl.commercelink.web.settings.PackageTemplateView;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.WarehouseAddressView;

import java.util.List;
import java.util.Locale;

/**
 * Settings › Shipping: a stack of read-only cards (courier account, carriers, pickup addresses, label sender, package
 * templates); everything is edited on its own subpage. The store comes from the session (ADMIN) or the path (SUPER_ADMIN).
 */
@Controller
@RequiredArgsConstructor
public class StoreShippingSettingsController {

    private final StoresRepository storesRepository;
    private final ShippingAccounts shippingAccounts;

    @GetMapping("/dashboard/store/shipping")
    @PreAuthorize("hasRole('ADMIN')")
    public String shipping(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminShipping(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ShippingConfiguration configuration = configurationOf(store);
        if (configuration.assignMissingIds()) {
            storesRepository.save(store);
        }

        IntegrationStatus account = shippingAccounts.status(store);
        model.addAttribute("account", account);
        model.addAttribute("webhookTokenMissing", account.configured() && shippingAccounts.webhookTokenMissing(store));
        model.addAttribute("accountHref", SettingsPaths.store(storeId, "/shipping/account"));
        model.addAttribute("disconnectHref", SettingsPaths.store(storeId, "/shipping/account/disconnect"));

        model.addAttribute("carriers", configuration.getAuthorizedCarriers().stream().map(AuthorizedCarrier::getDisplayName).toList());
        model.addAttribute("carriersHref", SettingsPaths.store(storeId, "/shipping/carriers"));

        String addressesPath = SettingsPaths.store(storeId, "/shipping/addresses");
        model.addAttribute("addresses", configuration.getPickUpAddresses().stream()
                .map(address -> WarehouseAddressView.of(address, locale, addressesPath, true))
                .toList());
        model.addAttribute("newAddressHref", addressesPath + "/new");

        ShippingDetails sender = configuration.getLabelSender();
        model.addAttribute("sender", sender == null ? null : WarehouseAddressView.of(sender, locale, "", true));
        model.addAttribute("senderHref", SettingsPaths.store(storeId, "/shipping/sender"));

        String templatesPath = SettingsPaths.store(storeId, "/shipping/templates");
        List<PackageTemplateView> templates = configuration.getPackageTemplates().stream()
                .map(template -> PackageTemplateView.of(template, templatesPath))
                .toList();
        model.addAttribute("templates", templates);
        model.addAttribute("newTemplateHref", templatesPath + "/new");
        return "store-shipping";
    }

    static ShippingConfiguration configurationOf(Store store) {
        if (store.getShippingConfiguration() == null) {
            store.setShippingConfiguration(new ShippingConfiguration());
        }
        return store.getShippingConfiguration();
    }
}
