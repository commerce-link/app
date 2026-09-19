package pl.commercelink.orders.rma;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.dtos.RmaCenterForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.RmaCenterView;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Settings › RMA centres: the addresses the store ships returned goods back to, one per supplier. A store admin works
 * on their own centres and also sees the platform-wide ones (store id {@code default}) read-only; a super admin works
 * on that platform bucket. The store is always taken from the session — this page has no store id in its path
 * ({@code StorePath.RESERVED_SEGMENTS}) and never takes one from the form.
 */
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Controller
@RequiredArgsConstructor
@RequestMapping(RMACenterController.PATH)
public class RMACenterController {

    static final String PATH = "/dashboard/store/rma-centers";
    private static final String LIST_VIEW = "rma-centers";
    private static final String FORM_VIEW = "rma-center-form";
    private static final String FORM_FRAGMENT = FORM_VIEW + " :: centerForm";

    private final StoresRepository storesRepository;
    private final RMACentersRepository rmaCentersRepository;
    private final SupplierRegistry supplierRegistry;
    private final SupplierLabels supplierLabels;
    private final MessageSource messageSource;

    @GetMapping
    public String list(Model model, Locale locale) {
        String scope = rmaCenterStoreId();
        SupplierLabelMap labels = labels();
        Collator collator = Collator.getInstance(locale);
        List<RmaCenterView> own = new ArrayList<>();
        List<RmaCenterView> shared = new ArrayList<>();
        List<RMACenter> visible = visibleCenters(scope);
        // Counted once for the whole page: asking the repository per row would scan the table as many times.
        Map<String, Long> ownPerProvider = visible.stream()
                .filter(center -> scope.equals(center.getStoreId()))
                .filter(center -> StringUtils.isNotBlank(center.getProvider()))
                .collect(Collectors.groupingBy(RMACenter::getProvider, Collectors.counting()));
        for (RMACenter center : visible) {
            boolean isShared = !scope.equals(center.getStoreId());
            (isShared ? shared : own).add(RmaCenterView.of(center, title(labels, center.getProvider(), locale), isShared,
                    StringUtils.isBlank(center.getProvider()) || SupplierIdentity.isManual(center.getProvider())
                            || supplierRegistry.exists(center.getProvider()),
                    ownPerProvider.getOrDefault(center.getProvider(), 0L) == 1L, locale, PATH));
        }
        // Sorted by what the row shows, so a supplier renamed by its label lands where the reader looks for it.
        Comparator<RmaCenterView> byTitle = Comparator.comparing(RmaCenterView::title, collator);
        own.sort(byTitle);
        shared.sort(byTitle);

        model.addAttribute("ownCenters", own);
        model.addAttribute("sharedCenters", shared);
        model.addAttribute("newHref", PATH + "/new");
        model.addAttribute("supplierLabels", labels);
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String newForm(Model model, Locale locale) {
        return render(null, RmaCenterForm.empty(), Map.of(), model, locale);
    }

    @PostMapping("/new")
    public String create(@ModelAttribute RmaCenterForm form,
                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                         HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(null, form, errors, SettingsPaths.isAsync(requestedWith), model, locale, response);
        }
        RMACenter center = new RMACenter();
        center.setStoreId(rmaCenterStoreId());
        center.setRmaCenterId(UUID.randomUUID().toString());
        form.applyTo(center);
        rmaCentersRepository.save(center);
        return saved("rma.center.added", SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping("/{rmaCenterId}")
    public String editForm(@PathVariable String rmaCenterId, Model model, Locale locale) {
        RMACenter center = requireCenter(rmaCenterId);
        return render(center, RmaCenterForm.from(center), Map.of(), model, locale);
    }

    @PostMapping("/{rmaCenterId}")
    public String update(@PathVariable String rmaCenterId, @ModelAttribute RmaCenterForm form,
                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                         HttpServletRequest request, HttpServletResponse response) {
        RMACenter center = requireCenter(rmaCenterId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(center, form, errors, SettingsPaths.isAsync(requestedWith), model, locale, response);
        }
        form.applyTo(center);
        rmaCentersRepository.save(center);
        return saved("rma.center.updated", SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping("/{rmaCenterId}/delete")
    public String confirmDelete(@PathVariable String rmaCenterId, Model model, Locale locale) {
        RMACenter center = requireCenter(rmaCenterId);
        String name = title(labels(), center.getProvider(), locale);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("rma.center.delete.title", new Object[]{name}, locale),
                messageSource.getMessage(lastCenterFor(center) ? "rma.center.delete.message.last" : "rma.center.delete.message",
                        new Object[]{name}, locale),
                messageSource.getMessage("rma.center.delete.action", null, locale),
                PATH + "/" + rmaCenterId + "/delete", PATH));
        model.addAttribute("backLabel", messageSource.getMessage("nav.rma.centers", null, locale));
        return "settings-confirm";
    }

    @PostMapping("/{rmaCenterId}/delete")
    public String delete(@PathVariable String rmaCenterId, Locale locale, RedirectAttributes redirectAttributes) {
        rmaCentersRepository.delete(requireCenter(rmaCenterId));
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("rma.center.deleted", null, locale));
        return "redirect:" + PATH;
    }

    /**
     * Own centres plus the platform-wide ones. A platform centre is keyed by supplier type while store connections
     * carry tokened identities, so a type match on any connection also reveals it.
     */
    private List<RMACenter> visibleCenters(String scope) {
        List<RMACenter> centers = rmaCentersRepository.findByStoreId(scope);
        if (!CustomSecurityContext.hasRole("ADMIN")) {
            return centers;
        }
        Store store = storesRepository.findById(CustomSecurityContext.getStoreId());
        return centers.stream()
                .filter(c -> !supplierRegistry.exists(c.getProvider())
                        || SupplierRegistry.OTHER.equalsIgnoreCase(c.getProvider())
                        || store.getEnabledProviders().contains(c.getProvider())
                        || store.getEnabledProviders().stream()
                                .anyMatch(name -> SupplierIdentity.typeOf(name).equals(c.getProvider())))
                .toList();
    }

    /** True when no other centre of this store serves the same supplier, so deleting it leaves those shipments without a destination. */
    private boolean lastCenterFor(RMACenter center) {
        if (center.getProvider() == null) {
            return false;
        }
        return rmaCentersRepository.findByStoreId(center.getStoreId()).stream()
                .filter(other -> center.getStoreId().equals(other.getStoreId()))
                .filter(other -> !center.getRmaCenterId().equals(other.getRmaCenterId()))
                .noneMatch(other -> center.getProvider().equals(other.getProvider()));
    }

    /**
     * Store admins pick one of their connections; the platform-wide (super admin) centres are keyed by supplier type.
     * A provider already saved but no longer offered stays as the first option, so editing the rest of a centre cannot
     * silently move it to another supplier; a new centre starts with an empty "choose" option.
     */
    private List<PickerOption> providerOptions(String selected, Locale locale) {
        List<PickerOption> options = new ArrayList<>();
        if (StringUtils.isBlank(selected)) {
            // Without it the browser picks the first supplier and the "choose a supplier" error can never show.
            options.add(new PickerOption("", messageSource.getMessage("rma.center.provider.placeholder", null, locale)));
        }
        if (CustomSecurityContext.hasRole("ADMIN")) {
            supplierLabels.forStoreId(CustomSecurityContext.getStoreId()).options()
                    .forEach(option -> options.add(new PickerOption(option.identity(), option.label())));
        } else {
            for (String name : supplierRegistry.getExternalSupplierNames()) {
                options.add(new PickerOption(name, name));
            }
        }
        options.add(new PickerOption(SupplierRegistry.OTHER, SupplierRegistry.OTHER));
        if (StringUtils.isNotBlank(selected) && options.stream().noneMatch(option -> option.value().equals(selected))) {
            options.add(0, new PickerOption(selected, selected));
        }
        return options;
    }

    private SupplierLabelMap labels() {
        return CustomSecurityContext.hasRole("ADMIN")
                ? supplierLabels.forStoreId(CustomSecurityContext.getStoreId())
                : supplierLabels.forStore(null);
    }

    /**
     * What the row is called: the supplier's display label, never the stored identity (a connection carries a token,
     * e.g. {@code Elko-k7f3a9c2}). A centre saved before the provider was required has no supplier to name.
     */
    private String title(SupplierLabelMap labels, String provider, Locale locale) {
        return StringUtils.isBlank(provider)
                ? messageSource.getMessage("rma.center.provider.unset", null, locale)
                : labels.of(provider);
    }

    private String rejected(RMACenter existing, RmaCenterForm form, Map<String, String> errors, boolean async,
                            Model model, Locale locale, HttpServletResponse response) {
        String view = render(existing, form, errors, model, locale);
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return FORM_FRAGMENT;
        }
        return view;
    }

    private String saved(String messageKey, boolean async, Model model, Locale locale,
                         RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        String message = messageSource.getMessage(messageKey, null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, PATH, message);
            render(null, RmaCenterForm.empty(), Map.of(), model, locale);
            model.addAttribute("redirectTo", PATH);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + PATH;
    }

    private String render(RMACenter existing, RmaCenterForm form, Map<String, String> errors, Model model, Locale locale) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("providerOptions", providerOptions(form.getProvider(), locale));
        model.addAttribute("countries", CountryOptions.forPicker(form.getCountry(), locale));
        model.addAttribute("formAction", existing == null ? PATH + "/new" : PATH + "/" + existing.getRmaCenterId());
        model.addAttribute("pageTitle", messageSource.getMessage(
                existing == null ? "rma.add.new.center" : "rma.edit.center", null, locale));
        model.addAttribute("centersHref", PATH);
        model.addAttribute("backLabel", messageSource.getMessage("nav.rma.centers", null, locale));
        return FORM_VIEW;
    }

    /** A centre of another store answers 404: the id is the only thing the request carries, so it is checked here. */
    private RMACenter requireCenter(String rmaCenterId) {
        RMACenter center = rmaCentersRepository.findById(rmaCenterStoreId(), rmaCenterId);
        if (center == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return center;
    }

    private String rmaCenterStoreId() {
        if (CustomSecurityContext.hasRole("SUPER_ADMIN")) {
            return RMACenter.MANAGED_RMA_CENTER_STORE_ID;
        }
        return CustomSecurityContext.getStoreId();
    }
}
