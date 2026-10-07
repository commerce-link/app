package pl.commercelink.web.inventory;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.ProductsAddReview;
import pl.commercelink.web.dtos.ComboboxGroup;
import pl.commercelink.web.dtos.ProductsBulkAddForm;
import pl.commercelink.web.settings.SettingsPaths;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * "Dodaj do katalogu" on the inventory page: straight to "Uzupełnij dane", where the catalog category is the first field.
 * The row menu opens it with a GET (a link that can be reloaded and bookmarked), the checked rows and a change of the
 * category with a POST (many EANs and the typed values do not fit an address). Changing the category renders the
 * review again for that category, keeping what was typed; the save goes back to the exact list it started from.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class InventoryAddController {

    public static final String PATH = "/dashboard/inventory/add";
    public static final String SAVE_PATH = PATH + "/save";
    /** The id of the category field: the key of its error, so the summary links to it. */
    static final String TARGET_FIELD = "review-target";

    private final CatalogTargetOptionsFactory optionsFactory;
    private final CatalogPlacement catalogPlacement;
    private final CatalogAccess access;
    private final ProductsAddReview review;
    private final MessageSource messageSource;

    /** The review lists one row per product, past Spring's default 256; the inventory sends at most this many. */
    @InitBinder
    void allowLargeSelections(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(CatalogTargetOptionsFactory.MAX_PRODUCTS);
    }

    /** The row menu's "Dodaj do katalogu" for one product, coming back to {@code returnTo} (the list as it was). */
    public static String href(String ean, String returnTo) {
        return PATH + "?ean=" + encode(ean) + "&returnTo=" + encode(returnTo);
    }

    @GetMapping(PATH)
    public String open(@RequestParam(name = "ean", required = false) List<String> eans,
                       @RequestParam(required = false) String target,
                       @RequestParam(required = false) String returnTo,
                       Model model, Locale locale, RedirectAttributes redirectAttributes) {
        return show(eans, target, null, returnTo, null, model, locale, redirectAttributes);
    }

    /** The checked rows of the list (no {@code target}: the matching category is chosen) and "Zmień kategorię". */
    @PostMapping(PATH)
    public String change(@RequestParam(name = "ean", required = false) List<String> eans,
                         @RequestParam(required = false) String target,
                         @RequestParam(required = false) String returnTo,
                         @ModelAttribute("edited") ProductsBulkAddForm edited,
                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                         Model model, Locale locale, RedirectAttributes redirectAttributes) {
        String view = show(eans, target, edited, returnTo, null, model, locale, redirectAttributes);
        // A pick in the combobox redraws only the rows; the field it came from stays as the operator left it.
        if (SettingsPaths.isAsync(requestedWith) && ProductsAddReview.VIEW.equals(view)) {
            model.addAttribute("partial", true);
            return ProductsAddReview.REDRAWN_PART;
        }
        return view;
    }

    @GetMapping(SAVE_PATH)
    public String saveReloaded(@RequestParam(required = false) String returnTo) {
        return "redirect:" + back(returnTo);
    }

    @PostMapping(SAVE_PATH)
    public String save(@ModelAttribute("form") ProductsBulkAddForm form,
                       @RequestParam(name = "ean", required = false) List<String> eans,
                       @RequestParam(required = false) String target,
                       @RequestParam(required = false) String reviewedTarget,
                       @RequestParam(required = false) String returnTo,
                       @RequestParam(defaultValue = "0") int skippedBefore,
                       Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        String storeId = storeId();
        if (storeId == null) {
            return noStore(returnTo, locale, redirectAttributes);
        }
        Optional<Chosen> chosen = resolve(storeId, target);
        if (chosen.isEmpty()) {
            List<String> unique = unique(eans);
            if (unique.isEmpty()) {
                return "redirect:" + back(returnTo);
            }
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            renderWithoutCategory(optionsFactory.build(storeId, unique), unique, "", true, back(returnTo), model, locale);
            return ProductsAddReview.VIEW;
        }
        // The rows were checked against the category the review was drawn for; another one may skip or reset them.
        if (!Objects.equals(target, reviewedTarget)) {
            return show(eans, target, form, returnTo, "catalog.products.review.target.changed", model, locale, redirectAttributes);
        }
        Chosen to = chosen.get();
        Map<String, String> errors = form.validate(to.category().getGroupingOrder(), ProductsAddReview.pricingGroups(to.category()));
        if (!errors.isEmpty()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            List<String> unique = unique(eans);
            review.render(to.catalog(), to.category(), form, List.of(), List.of(), errors, model, locale);
            inventoryAttributes(optionsFactory.build(storeId, unique), unique, to.value(), back(returnTo),
                    clampSkipped(skippedBefore), 0, null, model, locale);
            return ProductsAddReview.VIEW;
        }
        int added = review.save(storeId, to.category(), form);
        // The review already dropped what the category had then; only the notice reports that number.
        int skipped = form.getProducts().size() - added + clampSkipped(skippedBefore);
        review.noticeForInventory(redirectAttributes, to.catalog().getCatalogId(), to.category(), added, skipped, locale);
        return "redirect:" + back(returnTo);
    }

    /**
     * @param target  the category asked for; null on the way in from the list, where the first matching category that
     *                lacks the products is chosen
     * @param edited  the review as it was before another category was chosen, or null
     * @param notice  the key of a sentence shown above the review, or null
     */
    private String show(List<String> eans, @Nullable String target, @Nullable ProductsBulkAddForm edited, String returnTo,
                        @Nullable String notice, Model model, Locale locale, RedirectAttributes redirectAttributes) {
        String storeId = storeId();
        if (storeId == null) {
            return noStore(returnTo, locale, redirectAttributes);
        }
        List<String> unique = unique(eans);
        if (unique.isEmpty()) {
            return "redirect:" + back(returnTo);
        }
        CatalogTargetOptions options = optionsFactory.build(storeId, unique);
        String value = target == null ? options.preselectedValue() : target.strip();
        Optional<Chosen> chosen = options.noManualCategories() ? Optional.empty() : resolve(storeId, value);
        if (chosen.isEmpty()) {
            renderWithoutCategory(options, unique, StringUtils.defaultString(value), StringUtils.isNotBlank(value), back(returnTo),
                    model, locale);
            return ProductsAddReview.VIEW;
        }
        Chosen to = chosen.get();
        ProductsAddReview.Prepared prepared = review.prepare(storeId, to.category(), unique, edited);
        review.render(to.catalog(), to.category(), prepared.form(), prepared.skipped(), prepared.skippedExisting(), Map.of(),
                model, locale);
        inventoryAttributes(options, unique, to.value(), back(returnTo), prepared.skippedExisting().size(),
                prepared.resetRows(), notice, model, locale);
        return ProductsAddReview.VIEW;
    }

    /** Only a manual category of this store's own catalogs; anything else is no choice at all. */
    private Optional<Chosen> resolve(String storeId, String value) {
        if (StringUtils.isBlank(value)) {
            return Optional.empty();
        }
        Optional<CatalogPlacement.Target> target = catalogPlacement.forStore(storeId).target(value);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        try {
            ProductCatalog catalog = access.requireCatalog(storeId, target.get().catalogId());
            CategoryDefinition category = access.requireCategory(catalog, target.get().categoryId());
            // The placement is cached for a while; the category may have been deleted or made automatic since.
            if (category.hasType(CategoryDefinitionType.Dynamic)) {
                return Optional.empty();
            }
            return Optional.of(new Chosen(target.get().value(), catalog, category));
        } catch (ResponseStatusException e) {
            return Optional.empty();
        }
    }

    /** The field alone: nothing matched the products, or the category asked for is not one they can go to. */
    private void renderWithoutCategory(CatalogTargetOptions options, List<String> eans, String value, boolean invalid,
                                       String back, Model model, Locale locale) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (invalid) {
            errors.put(TARGET_FIELD, messageSource.getMessage("inventory.browse.add.noTarget", null, locale));
        }
        ProductsBulkAddForm empty = ProductsBulkAddForm.of(List.of());
        model.addAttribute("form", empty);
        model.addAttribute("errors", errors);
        model.addAttribute("errorSummary", errors);
        model.addAttribute("labels", List.of());
        model.addAttribute("pricingGroups", List.of());
        model.addAttribute("skipped", List.of());
        model.addAttribute("skippedExisting", List.of());
        inventoryAttributes(options, eans, invalid ? "" : value, back, 0, 0, null, model, locale);
    }

    private void inventoryAttributes(CatalogTargetOptions options, List<String> eans, String selected, String back,
                                     int skippedBefore, int resetRows, @Nullable String notice, Model model, Locale locale) {
        model.addAttribute("targetOptions", options);
        model.addAttribute("targetGroups", targetGroups(options, messageSource, locale));
        model.addAttribute("selectedTarget", selected);
        model.addAttribute("eans", eans);
        model.addAttribute("reviewCount", options.count());
        model.addAttribute("returnTo", back);
        model.addAttribute("backHref", back);
        model.addAttribute("skippedBefore", skippedBefore);
        model.addAttribute("resetRows", resetRows);
        model.addAttribute("saveAction", SAVE_PATH);
        model.addAttribute("changeAction", PATH);
        model.addAttribute("targetNotice", notice == null ? null : messageSource.getMessage(notice, null, locale));
    }

    /**
     * The options of the category combobox: the categories matching the products' PIM category first, then the other
     * manual ones by catalog. Every option reads "Katalog › Kategoria", so the search finds it by either name.
     */
    public static List<ComboboxGroup> targetGroups(CatalogTargetOptions options, MessageSource messages, Locale locale) {
        List<ComboboxGroup> groups = new ArrayList<>();
        if (!options.unmatched()) {
            String heading = options.pimCategoryName() == null
                    ? messages.getMessage("catalog.products.review.target.matching", null, locale)
                    : messages.getMessage("catalog.products.review.target.matching.named",
                    new Object[]{options.pimCategoryName()}, locale);
            groups.add(new ComboboxGroup(heading, options.matching().stream()
                    .map(option -> new ComboboxGroup.Option(option.value(), option.label(),
                            alreadyIn(option.alreadyIn(), options.count(), messages, locale)))
                    .toList()));
        }
        for (CatalogTargetOptions.Group group : options.others()) {
            groups.add(new ComboboxGroup(group.catalogName(), group.options().stream()
                    .map(option -> new ComboboxGroup.Option(option.value(), group.catalogName() + " › " + option.label(),
                            alreadyIn(option.alreadyIn(), options.count(), messages, locale)))
                    .toList()));
        }
        return groups;
    }

    /** "już tu: x z n" -- only when the category holds some of the products already. */
    private static String alreadyIn(int alreadyIn, int count, MessageSource messages, Locale locale) {
        if (alreadyIn == 0) {
            return null;
        }
        return count > 1
                ? messages.getMessage("catalog.products.review.target.in.many", new Object[]{alreadyIn, count}, locale)
                : messages.getMessage("catalog.products.review.target.in.one", null, locale);
    }

    private String noStore(String returnTo, Locale locale, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("inventoryError",
                messageSource.getMessage("inventory.browse.add.noStore", null, locale));
        return "redirect:" + back(returnTo);
    }

    private static List<String> unique(List<String> eans) {
        if (eans == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String ean : eans) {
            if (StringUtils.isNotBlank(ean) && unique.size() < CatalogTargetOptionsFactory.MAX_PRODUCTS) {
                unique.add(ean.strip());
            }
        }
        return List.copyOf(unique);
    }

    /** Sent back by the form, so a forged value only changes the text of the notice; kept within the review limit. */
    private static int clampSkipped(int skippedBefore) {
        return Math.clamp(skippedBefore, 0, CatalogTargetOptionsFactory.MAX_PRODUCTS);
    }

    private static String back(String returnTo) {
        return InventoryReturnTo.safe(returnTo).orElse(BrowseQuery.start().href());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String storeId() {
        return CustomSecurityContext.getStoreId();
    }

    private record Chosen(String value, ProductCatalog catalog, CategoryDefinition category) {
    }
}
