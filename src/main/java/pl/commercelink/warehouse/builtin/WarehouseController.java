package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.ManualWarehouseFulfilment;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.DealHunter;
import pl.commercelink.warehouse.StockLevels;
import pl.commercelink.warehouse.StockProductLevel;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationItem;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
class WarehouseController {

    @Autowired
    private Warehouse warehouse;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ManualWarehouseFulfilment manualWarehouseFulfilment;

    @Autowired
    private StockLevels stockLevels;

    @Autowired
    private DealHunter dealHunter;

    @Autowired
    private WarehouseGoodsOutService warehouseGoodsOutService;

    @Autowired
    private WarehouseGoodsInService warehouseGoodsInService;

    @Autowired
    private WarehouseInternalIssueService warehouseInternalIssueService;

    @Autowired
    private WarehouseInternalReservationService warehouseInternalReservationService;

    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    @GetMapping("/dashboard/warehouse")
    String warehouseItems(@RequestParam(required = false) List<String> categories,
                                  @RequestParam(required = false) List<String> statuses,
                                  @RequestParam(required = false, defaultValue = "false") boolean showAll,
                                  Model model) {
        Store store = storesRepository.findById(getStoreId());
        boolean hasExternalWarehouse = store.hasIntegration(IntegrationType.WMS_PROVIDER);

        List<WarehouseItem> warehouseItems;

        // Convert status strings to FulfilmentStatus enums, excluding Destroyed
        List<FulfilmentStatus> statusEnums = null;
        if (showAll) {
            statusEnums = Arrays.stream(FulfilmentStatus.values())
                    .filter(s -> s != FulfilmentStatus.Destroyed)
                    .collect(Collectors.toList());
        } else if (statuses != null && !statuses.isEmpty()) {
            statusEnums = statuses.stream()
                    .map(FulfilmentStatus::valueOf)
                    .filter(status -> status != FulfilmentStatus.Destroyed)
                    .collect(Collectors.toList());
        } else {
            // Default statuses based on warehouse type
            statusEnums = Collections.singletonList(hasExternalWarehouse ? FulfilmentStatus.Ordered : FulfilmentStatus.Delivered);
        }

        statusEnums = statusEnums.stream()
                .filter(status -> status != FulfilmentStatus.Destroyed)
                .filter(status -> status != FulfilmentStatus.Returned)
                .filter(status -> status != FulfilmentStatus.Replaced)
                .collect(Collectors.toList());

        warehouseItems =  warehouseRepository.findAllFiltered(getStoreId(), categories, statusEnums)
                .stream()
                .filter(item -> item.getStatus() != FulfilmentStatus.Destroyed)
                .sorted(Comparator.comparing(WarehouseItem::getCategory))
                .collect(Collectors.toList());

        double warehouseNetValue = warehouseItems.stream()
                .mapToDouble(item -> item.totalUnitCost().netValue())
                .sum();
        double warehouseGrossValue = warehouseItems.stream()
                .mapToDouble(item -> item.totalUnitCost().grossValue())
                .sum();

        // Split items by status
        Map<FulfilmentStatus, List<WarehouseItem>> itemsByStatus = warehouseItems.stream()
                .collect(Collectors.groupingBy(WarehouseItem::getStatus));

        List<String> allCategories = warehouseRepository.findAllCategories(getStoreId()).stream()
                .sorted()
                .collect(Collectors.toList());

        Arrays.stream(FulfilmentStatus.values()).forEach(s -> model.addAttribute(s.name() + "Status", s));

        // Add items grouped by status
        model.addAttribute("deliveredItems", itemsByStatus.getOrDefault(FulfilmentStatus.Delivered, Collections.emptyList()));
        model.addAttribute("orderedItems", itemsByStatus.getOrDefault(FulfilmentStatus.Ordered, Collections.emptyList()));
        model.addAttribute("allocationItems", itemsByStatus.getOrDefault(FulfilmentStatus.Allocation, Collections.emptyList()));
        model.addAttribute("newItems", itemsByStatus.getOrDefault(FulfilmentStatus.New, Collections.emptyList()));
        model.addAttribute("reservedItems", itemsByStatus.getOrDefault(FulfilmentStatus.Reserved, Collections.emptyList()));
        model.addAttribute("inRMAItems", itemsByStatus.getOrDefault(FulfilmentStatus.InRMA, Collections.emptyList()));
        model.addAttribute("inExternalServiceItems", itemsByStatus.getOrDefault(FulfilmentStatus.InExternalService, Collections.emptyList()));

        model.addAttribute("warehouseNetValue", warehouseNetValue);
        model.addAttribute("warehouseGrossValue", warehouseGrossValue);
        model.addAttribute("categories", allCategories);
        model.addAttribute("statuses", getFulfilmentStatuses(hasExternalWarehouse));
        model.addAttribute("selectedCategories", categories != null ? categories : Collections.emptyList());
        model.addAttribute("selectedStatuses", statusEnums.stream().map(FulfilmentStatus::name).collect(Collectors.toList()));
        model.addAttribute("isAdmin", isAdmin());
        model.addAttribute("hasExternalWarehouse", hasExternalWarehouse);

        return "warehouse";
    }

    private static List<FulfilmentStatus> getFulfilmentStatuses(boolean hasExternalWarehouse) {
        List<FulfilmentStatus> allStatuses;
        if (hasExternalWarehouse) {
            allStatuses = Arrays.asList(
                    FulfilmentStatus.New,
                    FulfilmentStatus.Allocation,
                    FulfilmentStatus.Ordered
            );
        } else {
            allStatuses = Arrays.asList(
                    FulfilmentStatus.New,
                    FulfilmentStatus.Allocation,
                    FulfilmentStatus.Reserved,
                    FulfilmentStatus.Ordered,
                    FulfilmentStatus.Delivered,
                    FulfilmentStatus.InRMA,
                    FulfilmentStatus.InExternalService
            );
        }
        return allStatuses;
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
    String restock(@RequestParam String restockPrice, @RequestParam(required = false) boolean onlyMissingItems, Model model) {
        List<StockProductLevel> stockProductLevels = stockLevels.calculate(getStoreId(), onlyMissingItems);

        List<OrderItem> orderItems = stockProductLevels.stream()
                .filter(StockProductLevel::qualifiesForRestock)
                .map(sl -> new OrderItem(
                        null,
                        sl.getCategory(),
                        sl.getName(),
                        sl.getMissingQuantity(),
                        getRestockPrice(sl, restockPrice),
                        sl.getManufacturerCode(),
                        false
                ))
                .collect(Collectors.toList());

        FulfilmentForm fulfilmentForm = manualWarehouseFulfilment.init(getStoreId(), orderItems);

        model.addAttribute("form", fulfilmentForm);

        return "fulfilment";
    }

    @PostMapping("/dashboard/warehouse/deal-hunter")
    @PreAuthorize("hasRole('ADMIN')")
    String dealHunter(Model model) {
        List<OrderItem> orderItems = dealHunter.find(getStoreId());

        FulfilmentForm fulfilmentForm = manualWarehouseFulfilment.init(getStoreId(), orderItems);

        model.addAttribute("form", fulfilmentForm);

        return "fulfilment";
    }

    private int getRestockPrice(StockProductLevel sl, String restockPrice) {
        if ("Promo".equalsIgnoreCase(restockPrice)) {
            return sl.getRestockPricePromo();
        } else if ("Standard".equalsIgnoreCase(restockPrice)) {
            return sl.getRestockPriceStandard();
        } else if ("Lowest".equalsIgnoreCase(restockPrice)) {
            return sl.getRestockPriceLowest();
        } else {
            // basically unlimited budget
            return 100000;
        }
    }

    @PostMapping("/dashboard/warehouse/fulfilment/commit")
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
