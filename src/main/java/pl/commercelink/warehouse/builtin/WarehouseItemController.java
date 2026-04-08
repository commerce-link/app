package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
class WarehouseItemController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private Warehouse warehouse;

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

    @GetMapping("/dashboard/warehouse/items/{itemId}")
    String showWarehouseItem(@PathVariable("itemId") String itemId, Model model) {
        return showWarehouseItemDetails(model, warehouseRepository.findById(getStoreId(), itemId));
    }

    private String showWarehouseItemDetails(Model model, WarehouseItem item) {
        Store store = storesRepository.findById(getStoreId());
        boolean isEdit = !item.isNew();

        model.addAttribute("productCategories", store.getEnabledProductCategories());
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
        return Arrays.asList(
                FulfilmentStatus.New,
                FulfilmentStatus.Allocation,
                FulfilmentStatus.Delivered
        );
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
