package pl.commercelink.warehouse.builtin;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
class WarehouseItemController {

    static final List<FulfilmentStatus> NEW_ITEM_STATUSES = List.of(
            FulfilmentStatus.New,
            FulfilmentStatus.Allocation
    );

    static final String NEW_SUPPLIER_FIELD = "new-supplier";

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

    @Autowired
    private WarehouseItemUpdateService warehouseItemUpdateService;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private SupplierChoice supplierChoice;

    @Autowired
    private SupplierLabels supplierLabels;

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
        model.addAttribute("itemConditions", ItemCondition.values());
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

    @GetMapping("/dashboard/warehouse/items/new")
    String newItem(Model model) {
        return newItemPage(model, new WarehouseItemAddForm(), Map.of(), Map.of(), false, null);
    }

    @PostMapping("/dashboard/warehouse/items/new")
    String addItem(@ModelAttribute("form") WarehouseItemAddForm form, Model model, Locale locale,
                   HttpServletResponse response, RedirectAttributes redirectAttributes) {
        Taxonomy taxonomy = StringUtils.isBlank(form.getManufacturerCode()) ? null
                : taxonomyCache.findByMfn(UnifiedProductIdentifiers.unifyMfn(form.getManufacturerCode()));
        boolean known = taxonomy != null && StringUtils.isNoneBlank(taxonomy.name(), taxonomy.ean());
        boolean productDataNeeded = !known && StringUtils.isNotBlank(form.getManufacturerCode());
        // the first submit of an unknown code only reveals the product data group, so it carries no name/ean errors yet
        boolean productDataAsked = productDataNeeded && (form.isProductDataShown() || hasAnyProductData(form));
        Map<String, String> errors = new LinkedHashMap<>(form.validate(productDataAsked));
        Map<String, Object[]> errorArgs = new HashMap<>();
        SupplierChoice.Resolution supplier = supplierChoice.resolve(
                storesRepository.findById(getStoreId()), form.getSupplier(), form.getCustomSupplier());
        if (!supplier.accepted()) {
            String field = supplierField(form);
            errors.put(field, supplier.errorCode());
            if (supplier.errorArgs() != null) {
                errorArgs.put(field, supplier.errorArgs());
            }
        }
        if (!errors.isEmpty() || (productDataNeeded && !productDataAsked)) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return newItemPage(model, form, errors, errorArgs, productDataNeeded, null);
        }

        String category = known ? StringUtils.defaultIfBlank(taxonomy.category(), Categories.UNCATEGORIZED)
                : StringUtils.defaultIfBlank(form.getCategory(), Categories.UNCATEGORIZED);
        WarehouseItem item = new WarehouseItem(getStoreId(), supplier.identity(), category,
                known ? taxonomy.name() : form.getName().trim(), known ? taxonomy.ean() : form.getEan().trim(),
                UnifiedProductIdentifiers.unifyMfn(form.getManufacturerCode()), form.netCost(), form.quantity());
        item.setStatus(form.statusValue());

        OperationResult<?> result = warehouseInternalReceiptService.addItem(
                getStoreId(), item, CustomSecurityContext.getLoggedInUserName());
        if (!result.isSuccess()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return newItemPage(model, form, errors, errorArgs, productDataNeeded, result.getMessage());
        }
        redirectAttributes.addFlashAttribute("settingsSavedMessage",
                messageSource.getMessage("warehouse.item.added", new Object[]{item.getName()}, locale));
        return redirectToWarehouseFilteredBy(item.getStatus());
    }

    /** The id of the field a supplier error belongs to: the typed name when "Other supplier…" is chosen, else the select. */
    private static String supplierField(WarehouseItemAddForm form) {
        boolean typed = SupplierChoice.CUSTOM.equals(form.getSupplier())
                || (StringUtils.isBlank(form.getSupplier()) && StringUtils.isNotBlank(form.getCustomSupplier()));
        return typed ? NEW_SUPPLIER_FIELD + "-custom" : NEW_SUPPLIER_FIELD;
    }

    /** "Uncategorized" is what the empty choice of the category list already means, so it is not offered twice. */
    private static List<StoreCategories.Group> withoutUncategorized(List<StoreCategories.Group> groups) {
        return groups.stream()
                .map(group -> new StoreCategories.Group(group.catalog(), group.names().stream()
                        .filter(name -> !Categories.UNCATEGORIZED.equals(name))
                        .toList()))
                .filter(group -> !group.names().isEmpty())
                .toList();
    }

    private static boolean hasAnyProductData(WarehouseItemAddForm form) {
        return StringUtils.isNotBlank(form.getName()) || StringUtils.isNotBlank(form.getEan());
    }

    private String newItemPage(Model model, WarehouseItemAddForm form, Map<String, String> errors,
                               Map<String, Object[]> errorArgs, boolean mfnUnknown, String errorMessage) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorArgs", errorArgs);
        model.addAttribute("mfnUnknown", mfnUnknown);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("vatRate", Price.DEFAULT_VAT_RATE);
        model.addAttribute("statuses", NEW_ITEM_STATUSES);
        model.addAttribute("suppliers", supplierLabels.forStoreId(getStoreId()).options());
        model.addAttribute("categoryGroups", withoutUncategorized(storeCategories.groupsFor(getStoreId())));
        return "warehouse-item-new";
    }

    @PostMapping("/dashboard/warehouse/items/{itemId}/save")
    String saveWarehouseItem(@PathVariable("itemId") String itemId,
                             @ModelAttribute WarehouseItem updatedItem,
                             Model model) {
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
                model.addAttribute("errorMessage", result.getMessage());
                return showWarehouseItemDetails(model, updatedItem);
            }
            return redirectToWarehouseFilteredBy(updatedItem.getStatus());
        }

        warehouseItemUpdateService.update(getStoreId(), existingItem, updatedItem);
        return redirectToWarehouseFilteredBy(existingItem.getStatus());
    }

    private static String redirectToWarehouseFilteredBy(FulfilmentStatus status) {
        return "redirect:/dashboard/warehouse?statuses=" + status.name();
    }

    @PostMapping("/dashboard/warehouse/items/{itemId}/delete")
    String deleteWarehouseItem(@PathVariable("itemId") String itemId) {
        warehouseAllocationsManager.remove(getStoreId(), itemId);
        return "redirect:/dashboard/warehouse";
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
