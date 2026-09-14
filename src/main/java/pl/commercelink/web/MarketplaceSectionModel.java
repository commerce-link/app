package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.stores.Store;

public final class MarketplaceSectionModel {

    private MarketplaceSectionModel() {
    }

    public static String render(MarketplaceConnectionService marketplaceConnectionService, Store store,
                                String successMessage, Model model) {
        model.addAttribute("sectionRows", marketplaceConnectionService.views(store));
        model.addAttribute("sectionAddDisabled", marketplaceConnectionService.availableMarketplaces(store).isEmpty());
        model.addAttribute("sectionSuccessMessage", successMessage);
        model.addAttribute("sectionMarketplacesWithStoredConfig",
                String.join(";", marketplaceConnectionService.marketplacesWithStoredConfiguration(store)));
        model.addAttribute("sectionBasePath", SupplierSectionModel.basePath(store.getStoreId()));
        return "fragments/marketplace-section :: marketplaceSection";
    }

    public static String renderErrorFragment(String message, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("errorMessage", message);
        return "fragments/marketplace-section :: sectionError";
    }
}
