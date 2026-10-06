package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.catalog.CatalogPaths;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The supplier assortment page: browse by category and add to the catalog. The eans of the dialog's form travel on to the catalog's review untouched:
 * a forward keeps the request parameters, so "Uzupełnij dane" sees {@code eans} and {@code returnTo} as if posted to it.
 */
@Controller
@RequiredArgsConstructor
public class InventoryBrowseController {

    static final String DIALOG_PATH = "/dashboard/inventory/browse/add-dialog";

    private final BrowsePageFactory pageFactory;
    private final AddToCatalogDialogFactory dialogFactory;
    private final CatalogPlacement catalogPlacement;
    private final MessageSource messageSource;

    @GetMapping(BrowseQuery.PATH)
    public String page(@RequestParam MultiValueMap<String, String> params, Model model) {
        // Before the split this path was the code search; its bookmarks carry q.
        if (params.containsKey("q")) {
            return "redirect:" + InventoryPageController.pricesHref(params.getFirst("q"), params.getFirst("from"));
        }
        BrowseQuery query = addBrowseAttributes(params, model);
        List<String> eans = params.getOrDefault("ean", List.of());
        if ("add".equals(params.getFirst("open")) && isAdmin() && storeId() != null && !eans.isEmpty()) {
            model.addAttribute("addDialog", dialogFactory.build(storeId(), eans, query.href()));
            // Without JavaScript the dialog is drawn open in the page; fetched by the script it is opened with showModal().
            model.addAttribute("addDialogOpen", true);
        }
        return "inventory";
    }

    @GetMapping(BrowseQuery.FRAGMENT_PATH)
    public String results(@RequestParam MultiValueMap<String, String> params, Model model) {
        addBrowseAttributes(params, model);
        return "fragments/inventory-browse :: results";
    }

    @GetMapping(DIALOG_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String addDialog(@RequestParam(value = "ean", required = false) List<String> eans,
                            @RequestParam(value = "returnTo", required = false) String returnTo, Model model) {
        model.addAttribute("addDialog", dialogFactory.build(storeId(), eans == null ? List.of() : eans, returnTo));
        return "fragments/inventory-browse :: addDialog";
    }

    @PostMapping(AddToCatalogDialog.ACTION)
    @PreAuthorize("hasRole('ADMIN')")
    public String add(@RequestParam(value = "target", required = false) String target,
                      @RequestParam(value = "otherTarget", required = false) String otherTarget,
                      @RequestParam(value = "returnTo", required = false) String returnTo,
                      RedirectAttributes redirectAttributes, Locale locale) {
        String chosen = AddToCatalogDialog.OTHER.equals(target) ? otherTarget : target;
        Optional<CatalogPlacement.Target> resolved = chosen == null
                ? Optional.empty()
                : catalogPlacement.forStore(storeId()).target(chosen);
        if (resolved.isEmpty()) {
            redirectAttributes.addFlashAttribute("inventoryError",
                    messageSource.getMessage("inventory.browse.add.noTarget", null, locale));
            return "redirect:" + InventoryReturnTo.safe(returnTo).orElse(BrowseQuery.start().href());
        }
        return "forward:" + CatalogPaths.productsAddReview(resolved.get().catalogId(), resolved.get().categoryId());
    }

    private BrowseQuery addBrowseAttributes(MultiValueMap<String, String> params, Model model) {
        InventoryPageController.addCommonAttributes(model);
        model.addAttribute("query", "");
        BrowseQuery query = BrowseQuery.parse(params);
        // A super admin account may carry a store id; its browse is the global one, like its code search.
        String scope = isSuperAdmin() ? null : storeId();
        model.addAttribute("browse", pageFactory.build(scope, query, isAdmin(), isSuperAdmin()));
        return query;
    }

    private static String storeId() {
        return CustomSecurityContext.getStoreId();
    }

    private static boolean isAdmin() {
        return CustomSecurityContext.hasRole("ADMIN");
    }

    private static boolean isSuperAdmin() {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }
}
