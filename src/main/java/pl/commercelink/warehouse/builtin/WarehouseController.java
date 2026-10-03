package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
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
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.ManualWarehouseFulfilment;
import pl.commercelink.stores.IntegrationType;
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

    @GetMapping("/dashboard/warehouse")
    String warehouseItems(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addListPage(model, params, locale);
        return "warehouse";
    }

    @GetMapping("/dashboard/warehouse/list")
    String warehouseList(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addListPage(model, params, locale);
        return "warehouse :: results";
    }

    private void addListPage(Model model, MultiValueMap<String, String> params, Locale locale) {
        boolean wms = storesRepository.findById(getStoreId()).hasIntegration(IntegrationType.WMS_PROVIDER);
        model.addAttribute("page", warehouseListService.page(getStoreId(), wms, isAdmin(), WarehouseListQuery.parse(params, wms), locale));
    }

    @PostMapping("/dashboard/warehouse/markAsAvailable")
    String markAsAvailable(@RequestParam("selectedItemIds") List<String> itemIds,
                                  @RequestParam("quantities") List<Integer> quantities) {
        warehouseInternalReservationService
                .remove(
                        Reservation.internalUse(getStoreId(), toReservationItems(itemIds, quantities))
                );
        return "redirect:/dashboard/warehouse";
    }

    @PostMapping("/dashboard/warehouse/markAsReserved")
    String markAsReserved(@RequestParam("selectedItemIds") List<String> itemIds,
                          @RequestParam("quantities") List<Integer> quantities) {
        warehouseInternalReservationService
                .create(
                        Reservation.internalUse(getStoreId(), toReservationItems(itemIds, quantities))
                );
        return "redirect:/dashboard/warehouse?statuses=Reserved";
    }

    @PostMapping("/dashboard/warehouse/markAsInRMA")
    String markAsInRMA(@RequestParam("selectedItemIds") List<String> itemIds,
                       @RequestParam("quantities") List<Integer> quantities) {
        warehouseInternalReservationService
                .create(
                        Reservation.internalRMA(getStoreId(), toReservationItems(itemIds, quantities))
                );
        return "redirect:/dashboard/warehouse?statuses=InRMA";
    }

    @PostMapping("/dashboard/warehouse/markAsInAllocation")
    String markAsInAllocation(@RequestParam("selectedItemIds") List<String> itemIds) {
        warehouseAllocationsManager.schedule(getStoreId(), itemIds);
        return "redirect:/dashboard/warehouse?statuses=Allocation";
    }

    @PostMapping("/dashboard/warehouse/markAsDestroyed")
    String markAsDestroyed(@RequestParam("selectedItemIds") List<String> itemIds,
                                  @RequestParam("quantities") List<Integer> quantities,
                                  @RequestParam("reason") DocumentReason reason,
                                  @RequestParam("note") String note,
                                  RedirectAttributes redirectAttributes) {
        OperationResult<?> result = warehouseInternalIssueService.destroyItems(
                getStoreId(),
                toReservationItems(itemIds, quantities),
                reason,
                note,
                CustomSecurityContext.getLoggedInUserName()
        );
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
        }
        return "redirect:/dashboard/warehouse";
    }

    @PostMapping("/dashboard/warehouse/markAsInExternalService")
    String markAsInExternalService(@RequestParam("selectedItemIds") List<String> itemIds,
                                          RedirectAttributes redirectAttributes) {
        List<WarehouseItem> warehouseItems = itemIds.stream()
                .map(id -> warehouseRepository.findById(getStoreId(), id))
                .toList();

        if (!deliveredPredicate.isFromSameSource(getStoreId(), warehouseItems)) {
            redirectAttributes.addFlashAttribute("errorMessage", "All selected items must have the same provider.");
            return "redirect:/dashboard/warehouse?statuses=InRMA";
        }

        OperationResult<?> result = warehouseGoodsOutService.issueGoodsOutForExternalService(
                getStoreId(),
                itemIds,
                CustomSecurityContext.getLoggedInUserName()
        );
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
            return "redirect:/dashboard/warehouse?statuses=InRMA";
        }
        return "redirect:/dashboard/warehouse?statuses=InExternalService";
    }

    @PostMapping("/dashboard/warehouse/markAsReceivedFromExternalService")
    String markAsReceivedFromExternalService(@RequestParam("selectedItemIds") List<String> itemIds,
                                                    RedirectAttributes redirectAttributes) {
        OperationResult<?> result = warehouseGoodsInService.receiveFromExternalService(
                getStoreId(),
                itemIds,
                CustomSecurityContext.getLoggedInUserName()
        );
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
            return "redirect:/dashboard/warehouse?statuses=InExternalService";
        }
        return "redirect:/dashboard/warehouse?statuses=Delivered";
    }

    private List<ReservationItem> toReservationItems(List<String> itemIds, List<Integer> quantities) {
        return IntStream.range(0, itemIds.size())
                .mapToObj(i -> new ReservationItem(itemIds.get(i), quantities.get(i)))
                .collect(Collectors.toList());
    }

    @PostMapping("/dashboard/warehouse/restock")
    @PreAuthorize("hasRole('ADMIN')")
    String restock(@RequestParam String catalogId,
                   @RequestParam(required = false) String categoryId,
                   @RequestParam RestockScope scope,
                   @RequestParam(required = false) RestockPriceCategory restockPrice,
                   @RequestParam(required = false) boolean onlyMissingItems,
                   Model model) {
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
