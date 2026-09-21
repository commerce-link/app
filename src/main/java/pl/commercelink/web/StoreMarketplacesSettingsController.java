package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Locale;

/**
 * Settings › Marketplaces: the list of connected marketplaces with what each one does for the store (orders and returns
 * fetched, catalogs sending offers) and what stopped working. Connecting, editing and disconnecting happen on the
 * marketplace's own subpage ({@link StoreMarketplaceController}), the account connection on the authorization page
 * ({@link StoreMarketplaceAuthorizationController}).
 */
@Controller
@RequiredArgsConstructor
public class StoreMarketplacesSettingsController {

    private final StoresRepository storesRepository;
    private final MarketplaceConnections marketplaces;

    @GetMapping("/dashboard/store/marketplaces")
    @PreAuthorize("hasRole('ADMIN')")
    public String marketplaces(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminMarketplaces(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String marketplacesPath = SettingsPaths.store(storeId, "/marketplaces");
        model.addAttribute("marketplaces", marketplaces.views(store, marketplacesPath, locale));
        model.addAttribute("disconnectMessages", marketplaces.disconnectMessages(store, locale));
        model.addAttribute("marketplacesInstalled", !marketplaces.installed().isEmpty());
        model.addAttribute("newMarketplaceHref", marketplaces.addable(store).isEmpty() ? null : marketplacesPath + "/new");
        return "store-marketplaces";
    }
}
