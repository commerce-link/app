package pl.commercelink.web.inventory;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryStatistics;
import pl.commercelink.inventory.search.InventorySearch;
import pl.commercelink.inventory.search.InventorySearchResult;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.StockSummary;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.stream.Stream;

@Controller
@RequiredArgsConstructor
public class InventoryPageController {

    static final int MIN_QUERY_LENGTH = 3;
    static final String MANAGE_SUPPLIERS_URL = "/dashboard/store/suppliers";
    static final String CONNECTION_URL_PREFIX = MANAGE_SUPPLIERS_URL + "/";
    static final String WAREHOUSE_URL = "/dashboard/warehouse";
    private static final String PAGE_PATH = "/dashboard/inventory";
    static final String PRICES_PATH = PAGE_PATH + "/prices";

    private final Inventory inventory;
    private final InventorySearch inventorySearch;
    private final StoresRepository storesRepository;
    private final InventorySourcesViewFactory sourcesViewFactory;
    private final WarehouseSummaryService warehouseSummaryService;
    private final TechnicalInventoryViewFactory technicalViewFactory;
    private final ProductCategoryViewFactory productCategoryViews;

    @GetMapping(PRICES_PATH)
    public String page(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "from", required = false) String from, Model model) {
        addCommonAttributes(model);
        // Only the supplier assortment list is a place to go back to; anything else is dropped.
        InventoryReturnTo.safe(from).ifPresent(target -> model.addAttribute("backToBrowse", target));
        String query = normalize(q);
        model.addAttribute("query", query);
        if (!query.isEmpty()) {
            addSearchResult(query, model);
        }
        return "inventory-prices";
    }

    /** The address of the prices and availability page for a code, keeping the way back to a browse list when there is one. */
    static String pricesHref(String query, String from) {
        String href = PRICES_PATH + "?q=" + encode(normalize(query));
        return from == null || from.isBlank() ? href : href + "&from=" + encode(from.strip());
    }

    @GetMapping(PAGE_PATH + "/summary")
    public String summary(Model model) {
        addCommonAttributes(model);
        LocalDateTime now = LocalDateTime.now();
        if (isSuperAdmin()) {
            model.addAttribute("technical", technicalViewFactory.build(now));
            return "fragments/inventory-technical :: technical";
        }
        String storeId = CustomSecurityContext.getStoreId();
        Store store = storesRepository.findById(storeId);
        InventoryStatistics statistics = inventory.storeStatistics(storeId);
        model.addAttribute("statistics", statistics);
        model.addAttribute("sources", sourcesViewFactory.build(store, statistics, now));
        return "fragments/inventory-summary :: summary";
    }

    @GetMapping(PAGE_PATH + "/warehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String warehouse(Model model) {
        addCommonAttributes(model);
        String storeId = CustomSecurityContext.getStoreId();
        Store store = storesRepository.findById(storeId);
        boolean externalWarehouse = store != null && store.hasIntegration(IntegrationType.WMS_PROVIDER);
        model.addAttribute("externalWarehouse", externalWarehouse);
        model.addAttribute("warehouseSummary", store == null || externalWarehouse
                ? StockSummary.EMPTY
                : warehouseSummaryService.summaryFor(storeId));
        return "fragments/inventory-summary :: warehouseTile";
    }

    @GetMapping(PAGE_PATH + "/search")
    public String search(@RequestParam(value = "q", required = false) String q, Model model, HttpServletResponse response) {
        addCommonAttributes(model);
        String query = normalize(q);
        model.addAttribute("query", query);
        if (!addSearchResult(query, model)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        return "fragments/inventory-results :: results";
    }

    // bookmarks of the former three-field form keep working
    @GetMapping(PAGE_PATH + "/check-price")
    public String legacyCheckPrice(@RequestParam(value = "mfn", required = false) String mfn,
                                   @RequestParam(value = "ean", required = false) String ean,
                                   @RequestParam(value = "pimId", required = false) String pimId) {
        return Stream.of(pimId, mfn, ean)
                .map(InventoryPageController::normalize)
                .filter(value -> !value.isEmpty())
                .findFirst()
                // UriComponentsBuilder#encode() leaves '+' unencoded (decoded as a space) and turns "{x}" into
                // a URI template variable that RedirectView then fails to resolve
                .map(query -> "redirect:" + pricesHref(query, null))
                .orElse("redirect:" + PRICES_PATH);
    }

    private boolean addSearchResult(String query, Model model) {
        if (query.length() < MIN_QUERY_LENGTH) {
            model.addAttribute("validationError", true);
            return false;
        }
        InventorySearchResult result = isSuperAdmin()
                ? inventorySearch.searchGlobal(query)
                : inventorySearch.search(CustomSecurityContext.getStoreId(), query);
        model.addAttribute("result", result);
        ProductHeader header = switch (result) {
            case InventorySearchResult.Found found -> found.product();
            case InventorySearchResult.KnownWithoutOffers known -> known.product();
            case null, default -> null;
        };
        if (header != null) {
            productCategoryViews.build(header).ifPresent(line -> model.addAttribute("productCategory", line));
        }
        return true;
    }

    static void addCommonAttributes(Model model) {
        model.addAttribute("superAdmin", isSuperAdmin());
        model.addAttribute("canManageSuppliers", CustomSecurityContext.hasRole("ADMIN"));
        model.addAttribute("manageSuppliersUrl", MANAGE_SUPPLIERS_URL);
        model.addAttribute("warehouseUrl", WAREHOUSE_URL);
        model.addAttribute("connectionUrlPrefix", CONNECTION_URL_PREFIX);
        model.addAttribute("browseDialogUrl", InventoryBrowseController.DIALOG_PATH);
    }

    private static boolean isSuperAdmin() {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
