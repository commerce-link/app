package pl.commercelink.warehouse.builtin;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.taxonomy.Categorized;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
class WarehouseItemController {

    static final List<FulfilmentStatus> NEW_ITEM_STATUSES = List.of(
            FulfilmentStatus.New,
            FulfilmentStatus.Allocation
    );

    @Autowired
    private Warehouse warehouse;

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StoreCategories storeCategories;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private WarehouseInternalReceiptService warehouseInternalReceiptService;

    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    @GetMapping("/dashboard/warehouse/items/add")
    String addWarehouseItem(Model model) {
        return showWarehouseItemDetails(model, WarehouseItem.empty(getStoreId()));
    }

    @GetMapping("/dashboard/warehouse/items/available")
    @ResponseBody
    List<AvailableWarehouseItemDto> availableWarehouseItems(@RequestParam(defaultValue = "") String category) {
        List<String> categories = category.isBlank() ? List.of() : List.of(category);
        return warehouseRepository.findAllFiltered(getStoreId(), categories, List.of(FulfilmentStatus.Ordered, FulfilmentStatus.Delivered))
                .stream()
                .map(AvailableWarehouseItemDto::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/dashboard/warehouse/items/{itemId}")
    String showWarehouseItem(@PathVariable("itemId") String itemId, Model model) {
        return showWarehouseItemDetails(model, warehouseRepository.findById(getStoreId(), itemId));
    }

    private String showWarehouseItemDetails(Model model, WarehouseItem item) {
        boolean isEdit = !item.isNew();

        model.addAttribute("productCategories", storeCategories.namesFor(getStoreId()));
        model.addAttribute("productCategoryGroups", storeCategories.groupsFor(getStoreId()));
        model.addAttribute("fulfilmentStatuses", getAvailableStatuses(isEdit));
        model.addAttribute("warehouseItem", item);
        model.addAttribute("isEdit", isEdit);

        return "warehouseItem";
    }

    private List<FulfilmentStatus> getAvailableStatuses(boolean isEdit) {
        if (isEdit) {
            return Arrays.stream(FulfilmentStatus.values())
                    .filter(s -> s != FulfilmentStatus.Returned)
                    .filter(s -> s != FulfilmentStatus.Replaced)
                    .collect(Collectors.toList());
        }
        return NEW_ITEM_STATUSES;
    }

    @PostMapping("/dashboard/warehouse/items/quick-add")
    String quickAddWarehouseItem(@RequestParam String manufacturerCode, @RequestParam double cost,
                                 @RequestParam int qty, @RequestParam FulfilmentStatus status,
                                 @RequestParam String supplier, Model model, Locale locale,
                                 RedirectAttributes redirectAttributes) {
        String mfn = UnifiedProductIdentifiers.unifyMfn(manufacturerCode);
        Taxonomy taxonomy = taxonomyCache.findByMfn(mfn);

        String name = taxonomy != null ? taxonomy.name() : null;
        String ean = taxonomy != null ? taxonomy.ean() : null;
        String category = taxonomy != null && Strings.isNotBlank(taxonomy.category())
                ? taxonomy.category()
                : Categorized.OTHER;

        WarehouseItem item = new WarehouseItem(getStoreId(), supplier, category, name, ean, mfn, cost, qty);
        item.setStatus(status);

        if (Strings.isBlank(name) || Strings.isBlank(ean)) {
            model.addAttribute("errorMessage", messageSource.getMessage("warehouse.item.mfn.not.found", null, locale));
            return showWarehouseItemDetails(model, item);
        }

        OperationResult<?> result = warehouseInternalReceiptService.addItem(
                getStoreId(), item, CustomSecurityContext.getLoggedInUserName()
        );
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
        } else {
            redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("warehouse.item.quick.add.success", null, locale));
        }
        return "redirect:/dashboard/warehouse";
    }

    @PostMapping("/dashboard/warehouse/items/{itemId}/save")
    String saveWarehouseItem(@PathVariable("itemId") String itemId,
                                    @ModelAttribute WarehouseItem updatedItem,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        WarehouseItem existingItem = warehouseRepository.findById(getStoreId(), itemId);
        boolean isNewItem = existingItem == null;

        if (isNewItem) {
            if (updatedItem.getStatus() == FulfilmentStatus.Delivered) {
                updatedItem.setDeliveryId("Unknown");
            }
            OperationResult<?> result = warehouseInternalReceiptService.addItem(
                    getStoreId(),
                    updatedItem,
                    CustomSecurityContext.getLoggedInUserName()
            );
            if (!result.isSuccess()) {
                redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
            }
            return showWarehouseItemDetails(model, updatedItem);
        }

        existingItem.update(updatedItem);
        return save(model, existingItem);
    }

    @PostMapping("/dashboard/warehouse/items/{itemId}/delete")
    String deleteWarehouseItem(@PathVariable("itemId") String itemId) {
        warehouseAllocationsManager.remove(getStoreId(), itemId);
        return "redirect:/dashboard/warehouse";
    }

    private String save(Model model, WarehouseItem item) {
        warehouseRepository.save(item);
        return showWarehouseItemDetails(model, item);
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
