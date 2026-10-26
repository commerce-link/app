package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
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
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PaymentOptionView;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.DiscountCodeDetailsView;
import pl.commercelink.web.settings.DiscountCodeDetailsView.Orders;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StoreDiscountCodeController {

    private static final String VIEW = "store-discount-code";

    private final StoresRepository storesRepository;
    private final DiscountCodesRepository discountCodesRepository;
    private final PaymentGateways paymentGateways;
    private final MessageSource messageSource;
    private final OrdersRepository ordersRepository;

    @GetMapping("/dashboard/store/discounts/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public String details(@PathVariable String code,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), code, from, to, OrderListQuery.PATH, model, locale);
    }

    // an order's page knows only the store of the session, so a super admin gets the orders without links
    @GetMapping("/dashboard/store/{storeId}/discounts/{code}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDetails(@PathVariable String storeId, @PathVariable String code,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    Model model, Locale locale) {
        return show(storeId, code, from, to, null, model, locale);
    }

    @PostMapping("/dashboard/store/discounts/{code}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public String toggle(@PathVariable String code, @RequestParam boolean active, Locale locale,
                         RedirectAttributes redirectAttributes) {
        return toggle(CustomSecurityContext.getStoreId(), code, active, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/discounts/{code}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminToggle(@PathVariable String storeId, @PathVariable String code,
                                   @RequestParam boolean active, Locale locale, RedirectAttributes redirectAttributes) {
        return toggle(storeId, code, active, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/discounts/{code}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String code, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), code, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/discounts/{code}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String code, Model model,
                                          Locale locale) {
        return confirmDelete(storeId, code, model, locale);
    }

    @PostMapping("/dashboard/store/discounts/{code}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable String code, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), code, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/discounts/{code}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDelete(@PathVariable String storeId, @PathVariable String code, Locale locale,
                                   RedirectAttributes redirectAttributes) {
        return delete(storeId, code, locale, redirectAttributes);
    }

    private String show(String storeId, String code, LocalDate from, LocalDate to, String ordersPath, Model model,
                        Locale locale) {
        Store store = requireStore(storeId);
        DiscountCode discountCode = requireCode(storeId, code);
        Map<String, String> gatewayNames = paymentGateways.paymentOptions(store).stream()
                .collect(Collectors.toMap(PaymentOptionView::name, PaymentOptionView::displayName, (first, second) -> first));
        boolean reversed = from != null && to != null && from.isAfter(to);
        LocalDate first = reversed ? to : from;
        LocalDate last = reversed ? from : to;
        Orders orders = Orders.of(ordersRepository.findAllByDiscountCode(storeId, discountCode.getCode(), first, last),
                first, last, ordersPath);
        model.addAttribute("details", DiscountCodeDetailsView.of(discountCode, LocalDateTime.now(DiscountCode.ZONE),
                gatewayNames, listPath(storeId), orders));
        model.addAttribute("backHref", listPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.discounts", null, locale));
        return VIEW;
    }

    private String toggle(String storeId, String code, boolean active, Locale locale,
                          RedirectAttributes redirectAttributes) {
        requireStore(storeId);
        DiscountCode discountCode = requireCode(storeId, code);
        discountCodesRepository.setActive(storeId, discountCode.getCode(), active);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage(
                active ? "store.discounts.toggled.on" : "store.discounts.toggled.off",
                new Object[]{discountCode.getCode()}, locale));
        return "redirect:" + detailsPath(storeId, discountCode);
    }

    private String confirmDelete(String storeId, String code, Model model, Locale locale) {
        requireStore(storeId);
        DiscountCode discountCode = requireCode(storeId, code);
        String detailsPath = detailsPath(storeId, discountCode);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.discounts.delete.title", new Object[]{discountCode.getCode()}, locale),
                messageSource.getMessage("store.discounts.delete.message", null, locale),
                messageSource.getMessage("store.discounts.delete", null, locale),
                detailsPath + "/delete",
                detailsPath));
        model.addAttribute("backLabel", discountCode.getCode());
        return "settings-confirm";
    }

    private String delete(String storeId, String code, Locale locale, RedirectAttributes redirectAttributes) {
        requireStore(storeId);
        Optional<DiscountCode> found = discountCodesRepository.find(storeId, code);
        if (found.isEmpty()) {
            return "redirect:" + listPath(storeId);
        }
        DiscountCode discountCode = found.get();
        Object[] arguments = {discountCode.getCode()};
        if (!discountCodesRepository.delete(storeId, discountCode.getCode())) {
            SettingsFlash.errorOnRedirect(redirectAttributes,
                    messageSource.getMessage("store.discounts.delete.refused", arguments, locale));
            return "redirect:" + detailsPath(storeId, discountCode);
        }
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.discounts.deleted", arguments, locale));
        return "redirect:" + listPath(storeId);
    }

    private static String listPath(String storeId) {
        return SettingsPaths.store(storeId, "/discounts");
    }

    private static String detailsPath(String storeId, DiscountCode discountCode) {
        return listPath(storeId) + "/" + discountCode.getCode();
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private DiscountCode requireCode(String storeId, String code) {
        return discountCodesRepository.find(storeId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
