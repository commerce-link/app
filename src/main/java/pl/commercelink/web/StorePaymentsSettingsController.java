package pl.commercelink.web;

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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.CheckoutSettingsForm;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.settings.BankAccountView;
import pl.commercelink.web.settings.DeliveryOptionView;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings › Payments: how customers pay (online gateways, bank transfer accounts), what delivery costs at checkout and
 * where the gateway sends them back. The page carries one form (checkout); gateways, accounts and delivery options are
 * lists edited on their own subpages. The store comes from the session (ADMIN) or the path (SUPER_ADMIN): the old page
 * took it from a hidden field and its gateway panel posted as ADMIN only, so the super admin got a 403.
 */
@Controller
@RequiredArgsConstructor
public class StorePaymentsSettingsController {

    private static final String VIEW = "store-payments";
    private static final String CHECKOUT_FRAGMENT = VIEW + " :: checkoutForm";

    private final StoresRepository storesRepository;
    private final PaymentGateways paymentGateways;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public String payments(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminPayments(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveCheckout(@ModelAttribute CheckoutSettingsForm form,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               Model model, Locale locale, RedirectAttributes redirectAttributes,
                               HttpServletResponse response) {
        return saveCheckout(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveCheckout(@PathVariable String storeId, @ModelAttribute CheckoutSettingsForm form,
                                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                                         HttpServletResponse response) {
        return saveCheckout(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/payments/gateways/{name}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public String makeDefault(@PathVariable String name, Locale locale, RedirectAttributes redirectAttributes) {
        return makeDefault(CustomSecurityContext.getStoreId(), name, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/gateways/{name}/default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminMakeDefault(@PathVariable String storeId, @PathVariable String name, Locale locale,
                                        RedirectAttributes redirectAttributes) {
        return makeDefault(storeId, name, locale, redirectAttributes);
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        render(store, CheckoutSettingsForm.from(store.getCheckoutConfiguration()), Map.of(), model, locale);
        return VIEW;
    }

    private String saveCheckout(String storeId, CheckoutSettingsForm form, boolean async, Model model, Locale locale,
                                RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate(storedCurrency(store));
        if (!errors.isEmpty()) {
            render(store, form, errors, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return CHECKOUT_FRAGMENT;
            }
            return VIEW;
        }
        form.applyTo(store);
        storesRepository.save(store);
        String message = messageSource.getMessage("store.payments.checkout.saved", null, locale);
        if (async) {
            render(store, CheckoutSettingsForm.from(store.getCheckoutConfiguration()), Map.of(), model, locale);
            model.addAttribute("savedMessage", message);
            return CHECKOUT_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + SettingsPaths.store(storeId, "/payments");
    }

    private String makeDefault(String storeId, String name, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (store.getPaymentIntegration(name) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        store.setDefaultPaymentIntegration(name);
        storesRepository.save(store);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.payments.gateway.madeDefault", null, locale));
        return "redirect:" + SettingsPaths.store(storeId, "/payments");
    }

    private void render(Store store, CheckoutSettingsForm checkout, Map<String, String> checkoutErrors, Model model,
                        Locale locale) {
        String storeId = store.getStoreId();
        String gatewaysPath = SettingsPaths.store(storeId, "/payments/gateways");
        String accountsPath = SettingsPaths.store(storeId, "/payments/bank-accounts");
        String deliveryPath = SettingsPaths.store(storeId, "/payments/delivery-options");
        CheckoutConfiguration configuration = store.getCheckoutConfiguration();

        model.addAttribute("gateways", paymentGateways.views(store, gatewaysPath));
        model.addAttribute("gatewayDisconnectMessages", store.getPayments().stream()
                .collect(Collectors.toMap(PaymentIntegration::getName,
                        gateway -> paymentGateways.disconnectMessage(store, gateway.getName(), locale),
                        // The page never adds a duplicate, but a record edited by hand must not take it down.
                        (first, second) -> first)));
        model.addAttribute("gatewaysInstalled", !paymentGateways.installed().isEmpty());
        model.addAttribute("newGatewayHref", paymentGateways.addable(store).isEmpty() ? null : gatewaysPath + "/new");
        model.addAttribute("accounts", store.getBankAccounts().stream()
                .map(account -> BankAccountView.of(account, accountsPath))
                .toList());
        model.addAttribute("newAccountHref", accountsPath + "/new");
        model.addAttribute("deliveryOptions", configuration == null ? List.of() : configuration.getActiveDeliveryOptions().stream()
                .map(option -> DeliveryOptionView.of(option, deliveryPath))
                .toList());
        model.addAttribute("newDeliveryOptionHref", deliveryPath + "/new");
        model.addAttribute("checkoutForm", checkout);
        model.addAttribute("checkoutErrors", checkoutErrors);
        model.addAttribute("checkoutAction", SettingsPaths.store(storeId, "/payments"));
        model.addAttribute("currencies", currencyOptions(storedCurrency(store), locale));
        // Warns about what is saved, not what is typed: a rejected form still shows the stored addresses' problem.
        model.addAttribute("returnToLocalMachine", configuration == null
                || CheckoutSettingsForm.pointsToLocalMachine(configuration.getSuccessUrl())
                || CheckoutSettingsForm.pointsToLocalMachine(configuration.getCancelUrl()));
    }

    /** The złoty, and before it a different currency the store saved earlier, marked as such. */
    private List<PickerOption> currencyOptions(String storedCurrency, Locale locale) {
        List<PickerOption> options = new ArrayList<>();
        if (StringUtils.isNotBlank(storedCurrency) && !CheckoutSettingsForm.POLISH_ZLOTY.equalsIgnoreCase(storedCurrency)) {
            String code = storedCurrency.toLowerCase(Locale.ROOT);
            options.add(new PickerOption(code, messageSource.getMessage("store.payments.checkout.currency.legacy",
                    new Object[]{code.toUpperCase(Locale.ROOT)}, locale)));
        }
        options.add(new PickerOption(CheckoutSettingsForm.POLISH_ZLOTY,
                messageSource.getMessage("store.payments.checkout.currency.pln", null, locale)));
        return options;
    }

    private static String storedCurrency(Store store) {
        return store.getCheckoutConfiguration() == null ? null : store.getCheckoutConfiguration().getCurrency();
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
