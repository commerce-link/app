package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private WarehouseItemPageFactory warehouseItemPageFactory;

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
    String showWarehouseItem(@PathVariable("itemId") String itemId, @RequestParam(required = false) String back, Model model, Locale locale) {
        model.addAttribute("page", warehouseItemPageFactory.page(found(itemId), back, isAdmin(), null, Map.of(), null, locale));
        return "warehouse-item";
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

    /** The id of the field a supplier error belongs to: as in {@link SupplierChoice#resolve}, the typed name only for "Other supplier…". */
    private static String supplierField(WarehouseItemAddForm form) {
        return SupplierChoice.CUSTOM.equals(form.getSupplier()) ? NEW_SUPPLIER_FIELD + "-custom" : NEW_SUPPLIER_FIELD;
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

    private static String redirectToWarehouseFilteredBy(FulfilmentStatus status) {
        return "redirect:/dashboard/warehouse?statuses=" + status.name();
    }

    // only the page before the redesign posts here (a page still open during a rolling deploy); it sends the VAT as the multiplier
    @PostMapping("/dashboard/warehouse/items/{itemId}/save")
    String saveFromPageBeforeRedesign(@PathVariable("itemId") String itemId, @ModelAttribute("form") WarehouseItemEditForm form, Model model,
                                      Locale locale, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        form.setTax(WarehouseItemEditForm.percentFromMultiplier(form.getTax()));
        return saveWarehouseItem(itemId, form, model, locale, response, redirectAttributes);
    }

    @PostMapping("/dashboard/warehouse/items/{itemId}")
    String saveWarehouseItem(@PathVariable("itemId") String itemId, @ModelAttribute("form") WarehouseItemEditForm form, Model model,
                             Locale locale, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        WarehouseItem stored = found(itemId);
        // a page left open while someone else saved must not overwrite their change
        if (form.getVersion() != null && !form.getVersion().equals(WarehouseItemEditForm.versionOf(stored))) {
            return conflict(itemId, form, model, locale, response);
        }
        Map<String, String> errors = form.validate(stored.getStatus(), stored.getComment());
        if (!errors.isEmpty()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            model.addAttribute("page", warehouseItemPageFactory.page(stored, form.getBack(), isAdmin(), form, errors, null, locale));
            return "warehouse-item";
        }
        String holder;
        try {
            holder = warehouseItemUpdateService.update(getStoreId(), stored, form.toUpdate(stored));
        } catch (ConditionalCheckFailedException e) {
            return conflict(itemId, form, model, locale, response);
        }
        if (!holder.equals(itemId)) {
            SettingsFlash.onRedirect(redirectAttributes, text(locale, "warehouse.item.saved.merged", stored.getName()));
            return "redirect:" + ItemBack.itemHref(holder, form.getBack());
        }
        SettingsFlash.onRedirect(redirectAttributes, text(locale, "warehouse.item.saved", stored.getName()));
        return "redirect:" + ItemBack.resolve(form.getBack(), stored.getStatus()).href();
    }

    private String conflict(String itemId, WarehouseItemEditForm form, Model model, Locale locale, HttpServletResponse response) {
        response.setStatus(HttpStatus.CONFLICT.value());
        // the form keeps the stale version on purpose: every resubmit is refused until the page is reloaded
        model.addAttribute("page", warehouseItemPageFactory.page(found(itemId), form.getBack(), isAdmin(), form, Map.of(),
                text(locale, "warehouse.item.error.conflict"), locale));
        return "warehouse-item";
    }

    /** Without JavaScript the delete button opens this page; with it, confirm-dialog.js posts to the same address. */
    @GetMapping("/dashboard/warehouse/items/{itemId}/delete")
    String confirmDeleteWarehouseItem(@PathVariable("itemId") String itemId, @RequestParam(required = false) String back, Model model,
                                      Locale locale, RedirectAttributes redirectAttributes) {
        WarehouseItem item = found(itemId);
        if (!item.canBeDeleted()) {
            SettingsFlash.errorOnRedirect(redirectAttributes, text(locale, "warehouse.item.delete.refused"));
            return "redirect:" + ItemBack.itemHref(itemId, back);
        }
        String self = ItemBack.itemHref(itemId, back);
        String safe = ItemBack.safe(back);
        model.addAttribute("confirm", new ConfirmAction(text(locale, "warehouse.item.delete.title", item.getName()),
                text(locale, "warehouse.item.delete.message"), text(locale, "warehouse.item.delete.action"),
                "/dashboard/warehouse/items/" + itemId + "/delete" + (safe == null ? "" : "?back=" + URLEncoder.encode(safe, StandardCharsets.UTF_8)),
                self));
        model.addAttribute("backLabel", item.getName());
        return "settings-confirm";
    }

    @PostMapping("/dashboard/warehouse/items/{itemId}/delete")
    String deleteWarehouseItem(@PathVariable("itemId") String itemId, @RequestParam(required = false) String back, Locale locale,
                               RedirectAttributes redirectAttributes) {
        WarehouseItem item = found(itemId);
        // the button shows only for New, but a posted form can come from anywhere
        if (!item.canBeDeleted()) {
            SettingsFlash.errorOnRedirect(redirectAttributes, text(locale, "warehouse.item.delete.refused"));
            return "redirect:" + ItemBack.itemHref(itemId, back);
        }
        warehouseAllocationsManager.remove(getStoreId(), itemId);
        SettingsFlash.onRedirect(redirectAttributes, text(locale, "warehouse.item.deleted", item.getName()));
        return "redirect:" + ItemBack.resolve(back, FulfilmentStatus.New).href();
    }

    private WarehouseItem found(String itemId) {
        WarehouseItem item = warehouseRepository.findById(getStoreId(), itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return item;
    }

    private boolean isAdmin() {
        return CustomSecurityContext.hasRole("ADMIN");
    }

    private String text(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
