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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.PromotionsRepository;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PromotionForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class StoreNewPromotionController {

    private static final String VIEW = "store-promotion-new";
    private static final String FORM_FRAGMENT = VIEW + " :: promotionForm";

    private final StoresRepository storesRepository;
    private final PromotionsRepository promotionsRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/promotions/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newPromotion(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/promotions/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewPromotion(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/promotions/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@ModelAttribute PromotionForm form,
                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                         HttpServletRequest request, HttpServletResponse response) {
        return create(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/promotions/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreate(@PathVariable String storeId, @ModelAttribute PromotionForm form,
                                   @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                   Model model, Locale locale, RedirectAttributes redirectAttributes,
                                   HttpServletRequest request, HttpServletResponse response) {
        return create(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request,
                response);
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        render(store, PromotionForm.empty(), Map.of(), model, locale);
        return VIEW;
    }

    private String create(String storeId, PromotionForm form, boolean async, Model model, Locale locale,
                          RedirectAttributes redirectAttributes, HttpServletRequest request,
                          HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            render(store, form, errors, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return VIEW;
        }
        Promotion promotion = form.toPromotion(storeId, LocalDateTime.now(DiscountCode.ZONE));
        promotionsRepository.save(promotion);
        String message = messageSource.getMessage("store.promotions.created", new Object[]{promotion.getName()}, locale);
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

    private void render(Store store, PromotionForm form, Map<String, String> errors, Model model, Locale locale) {
        String storeId = store.getStoreId();
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("formAction", listPath(storeId) + "/new");
        model.addAttribute("backHref", listPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.promotions", null, locale));
        model.addAttribute("catalogs", productCatalogRepository.findAll(storeId));
        model.addAttribute("pricelistUrl", SettingsPaths.store(storeId, "/discounts/pricelist"));
    }

    private static String listPath(String storeId) {
        return SettingsPaths.store(storeId, "/promotions");
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
