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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DiscountCodeForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class StoreNewDiscountCodeController {

    private static final String VIEW = "store-discount-code-new";
    private static final String FORM_FRAGMENT = VIEW + " :: codeForm";

    private final StoresRepository storesRepository;
    private final DiscountCodesRepository discountCodesRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final PaymentGateways paymentGateways;
    private final PricelistFinder pricelistFinder;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/discounts/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newCode(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/discounts/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewCode(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/discounts/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@ModelAttribute DiscountCodeForm form,
                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                         HttpServletRequest request, HttpServletResponse response) {
        return create(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/discounts/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreate(@PathVariable String storeId, @ModelAttribute DiscountCodeForm form,
                                   @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                   Model model, Locale locale, RedirectAttributes redirectAttributes,
                                   HttpServletRequest request, HttpServletResponse response) {
        return create(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request,
                response);
    }

    @GetMapping("/dashboard/store/discounts/pricelist")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public List<AvailabilityAndPrice> pricelist(@RequestParam String catalogId) {
        return pricelist(CustomSecurityContext.getStoreId(), catalogId);
    }

    @GetMapping("/dashboard/store/{storeId}/discounts/pricelist")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public List<AvailabilityAndPrice> superAdminPricelist(@PathVariable String storeId, @RequestParam String catalogId) {
        return pricelist(storeId, catalogId);
    }

    private List<AvailabilityAndPrice> pricelist(String storeId, String catalogId) {
        requireStore(storeId);
        if (productCatalogRepository.findById(storeId, catalogId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return pricelistFinder.availabilityAndPrices(storeId, catalogId).stream()
                .filter(row -> !row.isService())
                .toList();
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        render(store, DiscountCodeForm.empty(), Map.of(), model, locale);
        return VIEW;
    }

    private String create(String storeId, DiscountCodeForm form, boolean async, Model model, Locale locale,
                          RedirectAttributes redirectAttributes, HttpServletRequest request,
                          HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = new LinkedHashMap<>(form.validate(store));
        DiscountCode code = errors.isEmpty() ? form.toDiscountCode(storeId) : null;
        if (code != null && !discountCodesRepository.create(code)) {
            errors.put("code", "store.discounts.code.taken");
        }
        if (!errors.isEmpty()) {
            render(store, form, errors, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return VIEW;
        }
        String message = messageSource.getMessage("store.discounts.created", new Object[]{code.getCode()}, locale);
        String listPath = listPath(storeId);
        if (async) {
            SettingsFlash.forNextPage(request, response, listPath, message);
            render(store, form, Map.of(), model, locale);
            model.addAttribute("redirectTo", listPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + listPath;
    }

    private void render(Store store, DiscountCodeForm form, Map<String, String> errors, Model model, Locale locale) {
        String storeId = store.getStoreId();
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("formAction", listPath(storeId) + "/new");
        model.addAttribute("backHref", listPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.discounts", null, locale));
        model.addAttribute("paymentOptions", paymentGateways.paymentOptions(store));
        model.addAttribute("catalogs", productCatalogRepository.findAll(storeId));
        model.addAttribute("pricelistUrl", listPath(storeId) + "/pricelist");
        model.addAttribute("paymentsHref", SettingsPaths.store(storeId, "/payments"));
    }

    private static String listPath(String storeId) {
        return SettingsPaths.store(storeId, "/discounts");
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
