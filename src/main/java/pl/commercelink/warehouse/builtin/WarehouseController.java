package pl.commercelink.warehouse.builtin;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.ManualWarehouseFulfilment;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockPriceCategory;
import pl.commercelink.warehouse.RestockScope;
import pl.commercelink.warehouse.RestockSuggestion;
import pl.commercelink.warehouse.RestockSuggestionService;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationItem;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
class WarehouseController {

    @Autowired
    private WarehouseListService warehouseListService;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ManualWarehouseFulfilment manualWarehouseFulfilment;

    @Autowired
    private RestockSuggestionService restockSuggestionService;

    @Autowired
    private WarehouseGoodsOutService warehouseGoodsOutService;

    @Autowired
    private DeliveredPredicate deliveredPredicate;

    @Autowired
    private WarehouseGoodsInService warehouseGoodsInService;

    @Autowired
    private WarehouseInternalIssueService warehouseInternalIssueService;

    @Autowired
    private WarehouseInternalReservationService warehouseInternalReservationService;

    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    @Autowired
    private SupplierLabels supplierLabels;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @GetMapping("/dashboard/warehouse")
    String warehouseItems(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addListPage(model, params, locale);
        if (isAdmin()) {
            model.addAttribute("restock", restockForm(null, null));
        }
        return "warehouse";
    }

    @GetMapping("/dashboard/warehouse/list")
    String warehouseList(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addListPage(model, params, locale);
        return "warehouse :: results";
    }

    private void addListPage(Model model, MultiValueMap<String, String> params, Locale locale) {
        boolean wms = wms();
        model.addAttribute("page", warehouseListService.page(getStoreId(), wms, isAdmin(), WarehouseListQuery.parse(params, wms), locale));
    }

    @PostMapping("/dashboard/warehouse/markAsReserved")
    String markAsReserved(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                          @RequestParam(name = "quantities", required = false) List<Integer> quantities,
                          @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        List<WarehouseItem> items = new ArrayList<>();
        String refused = guard(WarehouseBulkAction.RESERVE, itemIds, quantities, items, view, locale, ra);
        if (refused != null) return refused;
        warehouseInternalReservationService.create(Reservation.internalUse(getStoreId(), toReservationItems(itemIds, quantities)));
        return done(WarehouseBulkAction.RESERVE, items.size(), sum(quantities), view, locale, ra);
    }

    @PostMapping("/dashboard/warehouse/markAsAvailable")
    String markAsAvailable(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                           @RequestParam(name = "quantities", required = false) List<Integer> quantities,
                           @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        List<WarehouseItem> items = new ArrayList<>();
        String refused = guard(WarehouseBulkAction.RELEASE, itemIds, quantities, items, view, locale, ra);
        if (refused != null) return refused;
        warehouseInternalReservationService.remove(Reservation.internalUse(getStoreId(), toReservationItems(itemIds, quantities)));
        return done(WarehouseBulkAction.RELEASE, items.size(), sum(quantities), view, locale, ra);
    }

    @PostMapping("/dashboard/warehouse/markAsInRMA")
    String markAsInRMA(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                       @RequestParam(name = "quantities", required = false) List<Integer> quantities,
                       @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        List<WarehouseItem> items = new ArrayList<>();
        String refused = guard(WarehouseBulkAction.RMA, itemIds, quantities, items, view, locale, ra);
        if (refused != null) return refused;
        warehouseInternalReservationService.create(Reservation.internalRMA(getStoreId(), toReservationItems(itemIds, quantities)));
        return done(WarehouseBulkAction.RMA, items.size(), sum(quantities), view, locale, ra);
    }

    @PostMapping("/dashboard/warehouse/markAsInAllocation")
    String markAsInAllocation(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                              @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        List<WarehouseItem> items = new ArrayList<>();
        String refused = guard(WarehouseBulkAction.ALLOCATE, itemIds, null, items, view, locale, ra);
        if (refused != null) return refused;
        warehouseAllocationsManager.schedule(getStoreId(), itemIds);
        return done(WarehouseBulkAction.ALLOCATE, items.size(), 0, view, locale, ra);
    }

    @PostMapping("/dashboard/warehouse/markAsDestroyed")
    String markAsDestroyed(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                           @RequestParam(name = "quantities", required = false) List<Integer> quantities,
                           @RequestParam(name = "reason", required = false) String reason,
                           @RequestParam(name = "note", required = false) String note,
                           @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        List<WarehouseItem> items = new ArrayList<>();
        String refused = guard(WarehouseBulkAction.DESTROY, itemIds, quantities, items, view, locale, ra);
        if (refused != null) return refused;
        String back = back(view);
        Optional<DocumentReason> destroyReason = WarehouseListService.DESTROY_REASONS.stream()
                .filter(r -> r.name().equals(reason)).findFirst();
        if (destroyReason.isEmpty()) {
            return refuse(ra, locale, back, "warehouse.error.destroy.reason");
        }
        if (note == null || note.isBlank()) {
            return refuse(ra, locale, back, "warehouse.error.destroy.note");
        }
        OperationResult<?> result = warehouseInternalIssueService.destroyItems(getStoreId(), toReservationItems(itemIds, quantities),
                destroyReason.get(), note.trim(), CustomSecurityContext.getLoggedInUserName());
        if (!result.isSuccess()) {
            ra.addFlashAttribute("settingsErrorMessage", result.getMessage());
            return "redirect:" + back;
        }
        return done(WarehouseBulkAction.DESTROY, items.size(), sum(quantities), view, locale, ra);
    }

    @PostMapping("/dashboard/warehouse/markAsInExternalService")
    String markAsInExternalService(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                                   @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        List<WarehouseItem> items = new ArrayList<>();
        String refused = guard(WarehouseBulkAction.EXTERNAL_SERVICE, itemIds, null, items, view, locale, ra);
        if (refused != null) return refused;
        OperationResult<?> result = warehouseGoodsOutService.issueGoodsOutForExternalService(getStoreId(), itemIds,
                CustomSecurityContext.getLoggedInUserName());
        if (!result.isSuccess()) {
            ra.addFlashAttribute("settingsErrorMessage", result.getMessage());
            return "redirect:" + back(view);
        }
        return done(WarehouseBulkAction.EXTERNAL_SERVICE, items.size(), 0, view, locale, ra);
    }

    @PostMapping("/dashboard/warehouse/markAsReceivedFromExternalService")
    String markAsReceivedFromExternalService(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                                             @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        List<WarehouseItem> items = new ArrayList<>();
        String refused = guard(WarehouseBulkAction.RECEIVE, itemIds, null, items, view, locale, ra);
        if (refused != null) return refused;
        OperationResult<?> result = warehouseGoodsInService.receiveFromExternalService(getStoreId(), itemIds,
                CustomSecurityContext.getLoggedInUserName());
        if (!result.isSuccess()) {
            ra.addFlashAttribute("settingsErrorMessage", result.getMessage());
            return "redirect:" + back(view);
        }
        return done(WarehouseBulkAction.RECEIVE, items.size(), 0, view, locale, ra);
    }

    /** Loads the posted items of this store and refuses the whole action on the first problem; null means "go on". */
    private String guard(WarehouseBulkAction action, List<String> itemIds, List<Integer> quantities, List<WarehouseItem> into,
                         MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra) {
        String back = back(view);
        if (itemIds == null || itemIds.isEmpty()) {
            return refuse(ra, locale, back, "warehouse.error.select.at.least.one");
        }
        // a forged post naming one item twice would merge an item into itself or issue it twice
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            return refuse(ra, locale, back, "warehouse.error.duplicate");
        }
        if (action.needsQuantity() && (quantities == null || quantities.size() != itemIds.size())) {
            return refuse(ra, locale, back, "warehouse.error.quantities.mismatch");
        }
        for (String id : itemIds) {
            WarehouseItem item = warehouseRepository.findById(getStoreId(), id);
            if (item == null) {
                return refuse(ra, locale, back, "warehouse.error.not.found");
            }
            into.add(item);
        }
        Optional<WarehouseItem> refused = action.firstRefused(into);
        if (refused.isPresent()) {
            WarehouseItem item = refused.get();
            String allowed = action.allowed().stream().map(s -> msg(locale, WarehouseStatuses.labelKey(s))).collect(Collectors.joining(", "));
            return refuse(ra, locale, back, "warehouse.error.status",
                    item.getName(), msg(locale, WarehouseStatuses.labelKey(item.getStatus())), msg(locale, "warehouse.bulk." + action.key() + ".label"), allowed);
        }
        if (action.needsQuantity()) {
            for (int i = 0; i < into.size(); i++) {
                Integer qty = quantities.get(i);
                if (qty == null || qty < 1 || qty > into.get(i).getQty()) {
                    return refuse(ra, locale, back, "warehouse.error.quantity",
                            into.get(i).getName(), into.get(i).getQty());
                }
            }
        }
        if (action.sameSource() && !deliveredPredicate.isFromSameSource(getStoreId(), into)) {
            return refuse(ra, locale, back, "warehouse.error.same.source");
        }
        return null;
    }

    private String refuse(RedirectAttributes ra, Locale locale, String target, String key, Object... args) {
        ra.addFlashAttribute("settingsErrorMessage", msg(locale, key, args));
        return "redirect:" + target;
    }

    private String done(WarehouseBulkAction action, int items, int units, MultiValueMap<String, String> view, Locale locale,
                        RedirectAttributes ra) {
        ra.addFlashAttribute("settingsSavedMessage", msg(locale, "warehouse.bulk." + action.key() + ".done", items, units));
        return "redirect:" + postedView(view).afterAction(action.after()).href();
    }

    /**
     * The list view the operator acted from (statuses, categories, q, sort, dir, page), which selection-actions.js posts
     * from the address; a refusal returns there and a done action keeps all of it but the status.
     */
    private String back(MultiValueMap<String, String> view) {
        return postedView(view).href();
    }

    /** Read with the store's WMS flag, as the list reads its address, so a WMS view returns to the same canonical address. */
    private WarehouseListQuery postedView(MultiValueMap<String, String> view) {
        return WarehouseListQuery.parse(view, wms());
    }

    private boolean wms() {
        Store store = storesRepository.findById(getStoreId());
        return store != null && store.hasIntegration(IntegrationType.WMS_PROVIDER);
    }

    private String msg(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    private static int sum(List<Integer> quantities) {
        return quantities.stream().mapToInt(Integer::intValue).sum();
    }

    private List<ReservationItem> toReservationItems(List<String> itemIds, List<Integer> quantities) {
        return IntStream.range(0, itemIds.size())
                .mapToObj(i -> new ReservationItem(itemIds.get(i), quantities.get(i)))
                .collect(Collectors.toList());
    }

    @GetMapping("/dashboard/warehouse/restock")
    @PreAuthorize("hasRole('ADMIN')")
    String restockPage(Model model) {
        model.addAttribute("restock", restockForm(null, null));
        return "warehouse-restock";
    }

    @PostMapping("/dashboard/warehouse/restock")
    @PreAuthorize("hasRole('ADMIN')")
    String restock(@RequestParam(required = false) String catalogId,
                   @RequestParam(required = false) String categoryId,
                   @RequestParam RestockScope scope,
                   @RequestParam(required = false) RestockPriceCategory restockPrice,
                   @RequestParam(required = false) boolean onlyMissingItems,
                   Model model, Locale locale, HttpServletResponse response) {
        if (catalogId == null || catalogId.isBlank()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            model.addAttribute("restock", restockForm(null, msg(locale, "warehouse.restock.error.catalog")));
            return "warehouse-restock";
        }
        List<RestockSuggestion> suggestions = restockSuggestionService.suggestForRestock(
                getStoreId(), catalogId, categoryId, scope, onlyMissingItems, restockPrice);

        List<OrderItem> orderItems = suggestions.stream()
                .map(suggestion -> new OrderItem(
                        null,
                        suggestion.getCategory(),
                        suggestion.getName(),
                        scope == RestockScope.ExpectedStockQty ? suggestion.getMissingQuantity() : 1,
                        getRestockPrice(suggestion, restockPrice),
                        suggestion.getManufacturerCode(),
                        false
                ))
                .collect(Collectors.toList());

        FulfilmentForm fulfilmentForm = manualWarehouseFulfilment.init(getStoreId(), orderItems);

        model.addAttribute("form", fulfilmentForm);
        model.addAttribute("supplierLabels", supplierLabels.forStoreId(getStoreId()));

        return "fulfilment";
    }

    private RestockForm restockForm(String selectedCatalogId, String error) {
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(getStoreId()).stream()
                .filter(catalog -> catalog.getCatalogId() != null)
                .sorted(Comparator.comparing(ProductCatalog::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        Map<String, List<Map<String, String>>> categoriesByCatalog = catalogs.stream()
                .collect(Collectors.toMap(
                        ProductCatalog::getCatalogId,
                        catalog -> catalog.getCategories().stream()
                                .filter(category -> category.getCategoryId() != null && category.getName() != null)
                                .map(category -> Map.of("id", category.getCategoryId(), "name", category.getName()))
                                .collect(Collectors.toList())));
        return new RestockForm(catalogs, categoriesByCatalog, selectedCatalogId, error);
    }

    private int getRestockPrice(RestockSuggestion suggestion, RestockPriceCategory budget) {
        if (budget == null) {
            return 100000;
        }
        return suggestion.restockPriceFor(budget);
    }

    @PostMapping("/dashboard/warehouse/fulfilment/commit")
    @PreAuthorize("hasRole('ADMIN')")
    String handleFulfilment(@ModelAttribute FulfilmentForm form) {
        manualWarehouseFulfilment.accept(getStoreId(), form);
        return form.getRedirectUrl();
    }

    @GetMapping("/dashboard/warehouse/items/destroyed")
    String fetchDestroyedWarehouseItems(Model model) {
        List<WarehouseItem> destroyedItems = warehouseRepository.findAll(getStoreId(), FulfilmentStatus.Destroyed);
        model.addAttribute("warehouseItems", destroyedItems);
        model.addAttribute("DestroyedStatus", FulfilmentStatus.Destroyed);
        return "destroyedWarehouseItems";
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    private boolean isAdmin() { return CustomSecurityContext.hasRole("ADMIN"); }

}
