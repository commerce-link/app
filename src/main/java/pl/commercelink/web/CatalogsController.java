package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogDetailsService;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingScheduleDescription;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.catalog.CatalogRow;
import pl.commercelink.web.catalog.CategoryRow;
import pl.commercelink.web.dtos.CatalogSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Product catalogs of the store: the list, one catalog (its categories) and the catalog settings. */
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CatalogsController {

    private static final String SETTINGS_VIEW = "catalog/catalog-settings";
    private static final String SETTINGS_FRAGMENT = SETTINGS_VIEW + " :: settingsForm";

    /**
     * Outcome of a refused catalog action, shown by the catalog page itself. Catalog pages never use the layout's
     * errorMessage banner, which is Bulma markup outside the redesigned page body.
     */
    static final String ERROR_FLASH = "catalogError";

    private final ProductCatalogRepository catalogRepository;
    private final MessageSource messageSource;
    private final ProductCatalogDetailsService detailsService;
    private final CatalogAccess access;
    private final ProductRepository productRepository;
    private final PimCategoryOptions pimCategoryOptions;
    private final MarketplaceConnections marketplaces;

    @GetMapping("/dashboard/catalogs")
    public String catalogs(Model model, Locale locale) {
        List<CatalogRow> rows = catalogRepository.findAll(CustomSecurityContext.getStoreId()).stream()
                .sorted(Comparator.comparing(ProductCatalog::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(catalog -> CatalogRow.of(catalog, scheduleText(catalog.getPricelistSchedule(), locale)))
                .toList();
        model.addAttribute("catalogs", rows);
        return "catalog/catalogs";
    }

    @GetMapping("/dashboard/catalogs/{catalogId}")
    public String catalog(@PathVariable String catalogId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(CustomSecurityContext.getStoreId(), catalogId);
        List<CategoryRow> rows = catalog.getCategories().stream()
                .sorted(Comparator.comparingInt(CategoryDefinition::getSequenceNumber))
                .map(category -> CategoryRow.of(catalog, category, pimCategoryOptions.namesOf(category.getPimCategoryIds()),
                        // An automatic category has no rows in the products table: its list is computed from the inventory.
                        category.hasType(CategoryDefinitionType.Dynamic) ? List.of() : productRepository.findAll(category.getCategoryId()),
                        marketplaces::displayName))
                .toList();
        model.addAttribute("catalog", catalog);
        model.addAttribute("categories", rows);
        model.addAttribute("productsTotal", rows.stream().mapToInt(row -> row.productsCount() == null ? 0 : row.productsCount()).sum());
        model.addAttribute("scheduleText", scheduleText(catalog.getPricelistSchedule(), locale));
        model.addAttribute("settingsHref", CatalogPaths.catalogSettings(catalogId));
        model.addAttribute("addCategoryHref", CatalogPaths.newCategory(catalogId));
        return "catalog/catalog";
    }

    @GetMapping("/dashboard/catalogs/new")
    public String newCatalog(Model model, Locale locale) {
        return renderSettings(null, CatalogSettingsForm.forNewCatalog(), Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/new")
    public String createCatalog(@ModelAttribute CatalogSettingsForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        boolean async = SettingsPaths.isAsync(requestedWith);
        Map<String, String> errors = form.validate(detailsService.minIntervalMinutes());
        if (!errors.isEmpty()) {
            return rejected(renderSettings(null, form, errors, model, locale), SETTINGS_FRAGMENT, async, response);
        }
        String storeId = CustomSecurityContext.getStoreId();
        // The id is generated here rather than by the service: the page the operator lands on next needs it.
        String catalogId = UniqueIdentifierGenerator.generate();
        ProductCatalogDetailsService.UpdateResult result =
                detailsService.save(storeId, catalogId, form.toCatalog(storeId, catalogId));
        if (result.hasErrors()) {
            return rejected(renderSettings(null, form, saveErrors(result), model, locale), SETTINGS_FRAGMENT, async, response);
        }
        String message = messageSource.getMessage("catalog.created", new Object[]{form.getName().trim()}, locale);
        return saved(CatalogPaths.catalog(catalogId), message, async, model, redirectAttributes, request, response,
                SETTINGS_FRAGMENT, () -> renderSettings(null, form, Map.of(), model, locale));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/settings")
    public String catalogSettings(@PathVariable String catalogId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(CustomSecurityContext.getStoreId(), catalogId);
        return renderSettings(catalog, CatalogSettingsForm.from(catalog), Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/settings")
    public String saveCatalogSettings(@PathVariable String catalogId, @ModelAttribute CatalogSettingsForm form,
                                      @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                      Model model, Locale locale, RedirectAttributes redirectAttributes,
                                      HttpServletRequest request, HttpServletResponse response) {
        String storeId = CustomSecurityContext.getStoreId();
        ProductCatalog catalog = access.requireCatalog(storeId, catalogId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        Map<String, String> errors = form.validate(detailsService.minIntervalMinutes());
        if (!errors.isEmpty()) {
            return rejected(renderSettings(catalog, form, errors, model, locale), SETTINGS_FRAGMENT, async, response);
        }
        ProductCatalogDetailsService.UpdateResult result = detailsService.save(storeId, catalogId, form.toCatalog(storeId, catalogId));
        if (result.hasErrors()) {
            return rejected(renderSettings(catalog, form, saveErrors(result), model, locale), SETTINGS_FRAGMENT, async, response);
        }
        String message = messageSource.getMessage("catalog.saved", new Object[]{form.getName().trim()}, locale);
        return saved(CatalogPaths.catalog(catalogId), message, async, model, redirectAttributes, request, response,
                SETTINGS_FRAGMENT, () -> renderSettings(catalog, form, Map.of(), model, locale));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/delete")
    public String confirmDeleteCatalog(@PathVariable String catalogId, Model model, Locale locale,
                                       RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(CustomSecurityContext.getStoreId(), catalogId);
        // Confirming something the POST would refuse anyway only wastes the operator's click.
        if (catalog.isDeletionProtection()) {
            return refuseDeletion(catalog, catalogId, locale, redirectAttributes);
        }
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("catalog.delete.title", new Object[]{catalog.getName()}, locale),
                messageSource.getMessage("catalog.delete.message",
                        new Object[]{catalog.getCategories().size(), productRepository.findAll(catalog).size()}, locale),
                messageSource.getMessage("catalog.delete", null, locale),
                CatalogPaths.catalogDelete(catalogId), CatalogPaths.catalogSettings(catalogId)));
        model.addAttribute("backLabel", messageSource.getMessage("catalog.settings.title", null, locale));
        return "settings-confirm";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/delete")
    public String deleteCatalog(@PathVariable String catalogId, Locale locale, RedirectAttributes redirectAttributes) {
        String storeId = CustomSecurityContext.getStoreId();
        ProductCatalog catalog = access.requireCatalog(storeId, catalogId);
        if (catalog.isDeletionProtection()) {
            return refuseDeletion(catalog, catalogId, locale, redirectAttributes);
        }
        ProductCatalogDetailsService.UpdateResult result = detailsService.delete(storeId, catalogId);
        if (result.hasErrors()) {
            ErrorMessage error = result.errors().get(0);
            redirectAttributes.addFlashAttribute(ERROR_FLASH, messageSource.getMessage(error.code(), error.args(), locale));
            return "redirect:" + CatalogPaths.catalogSettings(catalogId);
        }
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("catalog.deleted", new Object[]{catalog.getName()}, locale));
        return "redirect:" + CatalogPaths.catalogs();
    }

    private String refuseDeletion(ProductCatalog catalog, String catalogId, Locale locale, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(ERROR_FLASH,
                messageSource.getMessage("catalog.delete.protected", new Object[]{catalog.getName()}, locale));
        return "redirect:" + CatalogPaths.catalogSettings(catalogId);
    }

    /** "co 30 min" / "codziennie o 06:00" from the saved expression; the default schedule has its own sentence. */
    String scheduleText(String expression, Locale locale) {
        if (expression == null || expression.isBlank()) {
            return messageSource.getMessage("catalog.schedule.default", null, locale);
        }
        PollingScheduleDescription description = PollingScheduleDescription.of(expression);
        return messageSource.getMessage(description.code(), description.messageArgs(), locale);
    }

    /**
     * The service rechecks the schedule and reports it with arguments; the summary above the form renders a key
     * without them, so a schedule complaint is swapped for the placeholder-free variant shown under the field.
     */
    private Map<String, String> saveErrors(ProductCatalogDetailsService.UpdateResult result) {
        String code = result.errors().get(0).code();
        if (code.equals("catalog.pricelist.schedule.error.too.frequent")) {
            code = CatalogSettingsForm.scheduleErrorKey(InvalidScheduleException.Reason.TOO_FREQUENT);
        } else if (code.equals("catalog.pricelist.schedule.error.invalid")) {
            code = CatalogSettingsForm.scheduleErrorKey(InvalidScheduleException.Reason.SYNTAX);
        }
        return Map.of("pricelistSchedule", code);
    }

    private String renderSettings(ProductCatalog existing, CatalogSettingsForm form, Map<String, String> errors, Model model, Locale locale) {
        boolean edit = existing != null;
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("existing", edit);
        model.addAttribute("scheduleMinIntervalMinutes", detailsService.minIntervalMinutes());
        model.addAttribute("formAction", edit ? CatalogPaths.catalogSettings(existing.getCatalogId()) : CatalogPaths.newCatalog());
        model.addAttribute("backHref", edit ? CatalogPaths.catalog(existing.getCatalogId()) : CatalogPaths.catalogs());
        model.addAttribute("backLabel", edit ? existing.getName() : messageSource.getMessage("catalog.title", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage(edit ? "catalog.settings.title" : "catalog.new.title", null, locale));
        boolean deletable = edit && !existing.isDeletionProtection();
        model.addAttribute("deleteHref", deletable ? CatalogPaths.catalogDelete(existing.getCatalogId()) : null);
        // Counting the products reads every product of the catalog, so it happens only for the sentence that shows them.
        model.addAttribute("categoriesCount", deletable ? existing.getCategories().size() : 0);
        model.addAttribute("productsCount", deletable ? productRepository.findAll(existing).size() : 0);
        return SETTINGS_VIEW;
    }

    private String rejected(String view, String fragment, boolean async, HttpServletResponse response) {
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return fragment;
        }
        return view;
    }

    /** Success: with JavaScript the form answers 200 + data-cl-redirect and the script navigates; otherwise a PRG redirect. */
    private String saved(String nextPath, String message, boolean async, Model model, RedirectAttributes redirectAttributes,
                         HttpServletRequest request, HttpServletResponse response, String fragment, Supplier<String> rerender) {
        if (async) {
            SettingsFlash.forNextPage(request, response, nextPath, message);
            rerender.get();
            model.addAttribute("redirectTo", nextPath);
            return fragment;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + nextPath;
    }
}
