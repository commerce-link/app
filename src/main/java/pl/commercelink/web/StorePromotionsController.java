package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.PromotionsRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.PromotionListView;
import pl.commercelink.web.settings.SettingsPaths;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class StorePromotionsController {

    private static final String VIEW = "store-promotions";

    private final StoresRepository storesRepository;
    private final PromotionsRepository promotionsRepository;

    @GetMapping("/dashboard/store/promotions")
    @PreAuthorize("hasRole('ADMIN')")
    public String promotions(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                             Model model) {
        return show(CustomSecurityContext.getStoreId(), q, status, model);
    }

    @GetMapping("/dashboard/store/{storeId}/promotions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminPromotions(@PathVariable String storeId, @RequestParam(required = false) String q,
                                       @RequestParam(required = false) String status, Model model) {
        return show(storeId, q, status, model);
    }

    private String show(String storeId, String q, String status, Model model) {
        if (storesRepository.findById(storeId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String listPath = SettingsPaths.store(storeId, "/promotions");
        model.addAttribute("promotions", PromotionListView.of(promotionsRepository.findAll(storeId), q, status,
                LocalDateTime.now(DiscountCode.ZONE), listPath));
        model.addAttribute("listPath", listPath);
        model.addAttribute("newPromotionHref", listPath + "/new");
        return VIEW;
    }
}
