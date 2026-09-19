package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.IntegrationStatus;
import pl.commercelink.web.settings.PackageTemplateView;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.WarehouseAddressView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings › Shipping: a stack of read-only cards (courier account, carriers, pickup addresses, label sender, package
 * templates); everything is edited on its own subpage. The store comes from the session (ADMIN) or the path (SUPER_ADMIN).
 */
@Controller
@RequiredArgsConstructor
public class StoreShippingSettingsController {

    private final StoresRepository storesRepository;
    private final ShippingAccounts shippingAccounts;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

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
        if (storesRepository.findById(storeId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Records are edited and deleted by id; ones saved by the old forms have none yet. Another save of the store may
        // land meanwhile, so the ids are assigned on a fresh copy with a retry, and nothing is written when all have one.
        AtomicBoolean assigned = new AtomicBoolean();
        Store store = optimisticLockingExecutor.modifyAndSave(
                () -> storesRepository.findById(storeId),
                fresh -> assigned.set(configurationOf(fresh).assignMissingIds()),
                fresh -> {
                    if (assigned.get()) {
                        storesRepository.save(fresh);
                    }
                });
        ShippingConfiguration configuration = configurationOf(store);

        IntegrationStatus account = shippingAccounts.status(store);
        model.addAttribute("account", account);
        model.addAttribute("webhookTokenMissing", account.configured() && shippingAccounts.webhookTokenMissing(store));
        model.addAttribute("accountHref", SettingsPaths.store(storeId, "/shipping/account"));
        model.addAttribute("disconnectHref", SettingsPaths.store(storeId, "/shipping/account/disconnect"));

        // A carrier saved without a display name is named by its service name rather than printed as "null".
        model.addAttribute("carriers", configuration.getAuthorizedCarriers().stream()
                .map(carrier -> StringUtils.defaultIfBlank(carrier.getDisplayName(), carrier.getName()))
                .filter(StringUtils::isNotBlank)
                .toList());
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
