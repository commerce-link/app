package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.PromotionsRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.PromotionDetailsView;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class StorePromotionController {

    private static final String VIEW = "store-promotion";

    private final StoresRepository storesRepository;
    private final PromotionsRepository promotionsRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/promotions/{promotionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String details(@PathVariable String promotionId, Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), promotionId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/promotions/{promotionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDetails(@PathVariable String storeId, @PathVariable String promotionId, Model model,
                                    Locale locale) {
        return show(storeId, promotionId, model, locale);
    }

    @PostMapping("/dashboard/store/promotions/{promotionId}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public String toggle(@PathVariable String promotionId, @RequestParam boolean active, Locale locale,
                         RedirectAttributes redirectAttributes) {
        return toggle(CustomSecurityContext.getStoreId(), promotionId, active, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/promotions/{promotionId}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminToggle(@PathVariable String storeId, @PathVariable String promotionId,
                                   @RequestParam boolean active, Locale locale, RedirectAttributes redirectAttributes) {
        return toggle(storeId, promotionId, active, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/promotions/{promotionId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String promotionId, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), promotionId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/promotions/{promotionId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String promotionId, Model model,
                                          Locale locale) {
        return confirmDelete(storeId, promotionId, model, locale);
    }

    @PostMapping("/dashboard/store/promotions/{promotionId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable String promotionId, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), promotionId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/promotions/{promotionId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDelete(@PathVariable String storeId, @PathVariable String promotionId, Locale locale,
                                   RedirectAttributes redirectAttributes) {
        return delete(storeId, promotionId, locale, redirectAttributes);
    }

    private String show(String storeId, String promotionId, Model model, Locale locale) {
        requireStore(storeId);
        Promotion promotion = requirePromotion(storeId, promotionId);
        model.addAttribute("details", PromotionDetailsView.of(promotion, LocalDateTime.now(DiscountCode.ZONE),
                listPath(storeId)));
        model.addAttribute("backHref", listPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.promotions", null, locale));
        return VIEW;
    }

    private String toggle(String storeId, String promotionId, boolean active, Locale locale,
                          RedirectAttributes redirectAttributes) {
        requireStore(storeId);
        Promotion promotion = requirePromotion(storeId, promotionId);
        promotionsRepository.setActive(storeId, promotion.getPromotionId(), active);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage(
                active ? "store.promotions.toggled.on" : "store.promotions.toggled.off",
                new Object[]{promotion.getName()}, locale));
        return "redirect:" + detailsPath(storeId, promotion);
    }

    private String confirmDelete(String storeId, String promotionId, Model model, Locale locale) {
        requireStore(storeId);
        Promotion promotion = requirePromotion(storeId, promotionId);
        String detailsPath = detailsPath(storeId, promotion);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.promotions.delete.title", new Object[]{promotion.getName()}, locale),
                messageSource.getMessage("store.promotions.delete.message", null, locale),
                messageSource.getMessage("store.promotions.delete", null, locale),
                detailsPath + "/delete",
                detailsPath));
        model.addAttribute("backLabel", promotion.getName());
        return "settings-confirm";
    }

    private String delete(String storeId, String promotionId, Locale locale, RedirectAttributes redirectAttributes) {
        requireStore(storeId);
        Optional<Promotion> found = promotionsRepository.find(storeId, promotionId);
        if (found.isPresent()) {
            promotionsRepository.delete(found.get());
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.promotions.deleted",
                    new Object[]{found.get().getName()}, locale));
        }
        return "redirect:" + listPath(storeId);
    }

    private static String listPath(String storeId) {
        return SettingsPaths.store(storeId, "/promotions");
    }

    private static String detailsPath(String storeId, Promotion promotion) {
        return listPath(storeId) + "/" + promotion.getPromotionId();
    }

    private void requireStore(String storeId) {
        if (storesRepository.findById(storeId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private Promotion requirePromotion(String storeId, String promotionId) {
        return promotionsRepository.find(storeId, promotionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
