package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.security.CustomSecurityContext;

/** The supplier assortment page: browse by category; "Dodaj do katalogu" leads to {@link InventoryAddController}. */
@Controller
@RequiredArgsConstructor
public class InventoryBrowseController {

    /** The flash "Uzupełnij dane" leaves when it sends the operator back to this page. */
    public static final String NOTICE_FLASH = "inventoryNotice";

    private final BrowsePageFactory pageFactory;

    @GetMapping(BrowseQuery.PATH)
    public String page(@RequestParam MultiValueMap<String, String> params, Model model) {
        // Before the split this path was the code search; its bookmarks carry q. An empty one searched nothing: start here.
        String legacyQuery = params.getFirst("q");
        if (legacyQuery != null && !legacyQuery.isBlank()) {
            return "redirect:" + InventoryPageController.pricesHref(legacyQuery, params.getFirst("from"));
        }
        // Back from "Uzupełnij dane", which may have saved on another instance: its "W katalogu" is read again.
        boolean afterSave = model.containsAttribute(NOTICE_FLASH);
        addBrowseAttributes(params, model, afterSave);
        return "inventory";
    }

    @GetMapping(BrowseQuery.FRAGMENT_PATH)
    public String results(@RequestParam MultiValueMap<String, String> params, Model model) {
        addBrowseAttributes(params, model, false);
        return "fragments/inventory-browse :: results";
    }

    private void addBrowseAttributes(MultiValueMap<String, String> params, Model model, boolean freshPlacement) {
        InventoryPageController.addCommonAttributes(model);
        model.addAttribute("query", "");
        BrowseQuery query = BrowseQuery.parse(params);
        // A super admin account may carry a store id; its browse is the global one, like its code search.
        String scope = isSuperAdmin() ? null : storeId();
        model.addAttribute("browse", pageFactory.build(scope, query, isAdmin(), isSuperAdmin(), freshPlacement));
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
