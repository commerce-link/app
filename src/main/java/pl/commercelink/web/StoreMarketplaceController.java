package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.MarketplaceConnectionForm;

import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StoreMarketplaceController {

    private final StoresRepository storesRepository;
    private final MarketplaceConnectionService marketplaceConnectionService;
    private final MessageSource messageSource;

    @PostMapping("/dashboard/store/marketplaces/connection")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@ModelAttribute MarketplaceConnectionForm form, Locale locale, Model model,
                       HttpServletResponse response) {
        return doSave(CustomSecurityContext.getStoreId(), form, locale, model, response);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces/connection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveForStore(@PathVariable String storeId, @ModelAttribute MarketplaceConnectionForm form,
                               Locale locale, Model model, HttpServletResponse response) {
        return doSave(storeId, form, locale, model, response);
    }

    @PostMapping("/dashboard/store/marketplaces/{marketplace}/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(@PathVariable String marketplace, Locale locale, Model model,
                             HttpServletResponse response) {
        return doDisconnect(CustomSecurityContext.getStoreId(), marketplace, locale, model, response);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces/{marketplace}/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String disconnectForStore(@PathVariable String storeId, @PathVariable String marketplace,
                                     Locale locale, Model model, HttpServletResponse response) {
        return doDisconnect(storeId, marketplace, locale, model, response);
    }

    private String doSave(String storeId, MarketplaceConnectionForm form, Locale locale, Model model,
                          HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return MarketplaceSectionModel.renderErrorFragment(
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale), model, response);
        }
        MarketplaceConnectionService.ConnectionUpdateResult result = marketplaceConnectionService.connectOrUpdate(
                store, form.getMarketplace(), form.getConfiguration(), form.getSchedule());
        if (result.hasErrors()) {
            return MarketplaceSectionModel.renderErrorFragment(join(result, locale), model, response);
        }
        String successMessage = messageSource.getMessage(
                "store.marketplaces.saved", new Object[]{form.getMarketplace()}, locale);
        return MarketplaceSectionModel.render(marketplaceConnectionService, store, successMessage, model);
    }

    private String doDisconnect(String storeId, String marketplace, Locale locale, Model model,
                                HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return MarketplaceSectionModel.renderErrorFragment(
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale), model, response);
        }
        MarketplaceConnectionService.ConnectionUpdateResult result = marketplaceConnectionService.disconnect(store, marketplace);
        if (result.hasErrors()) {
            return MarketplaceSectionModel.renderErrorFragment(join(result, locale), model, response);
        }
        String successMessage = messageSource.getMessage(
                "store.marketplaces.disconnected", new Object[]{marketplace}, locale);
        return MarketplaceSectionModel.render(marketplaceConnectionService, store, successMessage, model);
    }

    private String join(MarketplaceConnectionService.ConnectionUpdateResult result, Locale locale) {
        return result.errors().stream()
                .map(error -> messageSource.getMessage(error.code(), error.args(), locale))
                .collect(Collectors.joining(" "));
    }
}
