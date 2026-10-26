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
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PaymentOptionView;
import pl.commercelink.web.settings.DiscountCodeListView;
import pl.commercelink.web.settings.SettingsPaths;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StoreDiscountCodesController {

    private static final String VIEW = "store-discount-codes";

    private final StoresRepository storesRepository;
    private final DiscountCodesRepository discountCodesRepository;
    private final PaymentGateways paymentGateways;

    @GetMapping("/dashboard/store/discounts")
    @PreAuthorize("hasRole('ADMIN')")
    public String codes(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                        @RequestParam(required = false) String target, Model model) {
        return show(CustomSecurityContext.getStoreId(), q, status, target, model);
    }

    @GetMapping("/dashboard/store/{storeId}/discounts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCodes(@PathVariable String storeId, @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String target, Model model) {
        return show(storeId, q, status, target, model);
    }

    private String show(String storeId, String q, String status, String target, Model model) {
        Store store = requireStore(storeId);
        List<PaymentOptionView> paymentOptions = paymentGateways.paymentOptions(store);
        Map<String, String> gatewayNames = paymentOptions.stream()
                .collect(Collectors.toMap(PaymentOptionView::name, PaymentOptionView::displayName, (first, second) -> first));
        String listPath = SettingsPaths.store(storeId, "/discounts");
        model.addAttribute("codes", DiscountCodeListView.of(discountCodesRepository.findAll(storeId), q, status, target,
                LocalDateTime.now(DiscountCode.ZONE), gatewayNames, listPath));
        model.addAttribute("listPath", listPath);
        model.addAttribute("newCodeHref", paymentOptions.isEmpty() ? null : listPath + "/new");
        model.addAttribute("paymentsHref", SettingsPaths.store(storeId, "/payments"));
        return VIEW;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
