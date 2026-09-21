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
import pl.commercelink.stores.BankAccount;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.BankAccountForm;
import pl.commercelink.web.dtos.CurrencyOptions;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.BankAccountView;

import java.util.Locale;
import java.util.Map;

/**
 * Bank accounts of the store, added and edited on their own page below Settings › Payments. The store comes from the
 * session (ADMIN) or the path (SUPER_ADMIN), the account from the path. The old page saved every account of the table
 * at once and dropped an incomplete one without a word; here each account is validated and saved on its own.
 */
@Controller
@RequiredArgsConstructor
public class StoreBankAccountController {

    private static final String VIEW = "store-bank-account";
    private static final String FORM_FRAGMENT = VIEW + " :: accountForm";

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/payments/bank-accounts/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newAccount(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/bank-accounts/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewAccount(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/bank-accounts/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createAccount(@ModelAttribute BankAccountForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return create(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/bank-accounts/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreateAccount(@PathVariable String storeId, @ModelAttribute BankAccountForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return create(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/payments/bank-accounts/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editAccount(@PathVariable String accountId, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), accountId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/bank-accounts/{accountId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditAccount(@PathVariable String storeId, @PathVariable String accountId, Model model, Locale locale) {
        return showEdit(storeId, accountId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/bank-accounts/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateAccount(@PathVariable String accountId, @ModelAttribute BankAccountForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return update(CustomSecurityContext.getStoreId(), accountId, form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/bank-accounts/{accountId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateAccount(@PathVariable String storeId, @PathVariable String accountId,
                                          @ModelAttribute BankAccountForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return update(storeId, accountId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @PostMapping("/dashboard/store/payments/bank-accounts/{accountId}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public String makeDefault(@PathVariable String accountId, Locale locale, RedirectAttributes redirectAttributes) {
        return makeDefault(CustomSecurityContext.getStoreId(), accountId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/bank-accounts/{accountId}/default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminMakeDefault(@PathVariable String storeId, @PathVariable String accountId, Locale locale,
                                        RedirectAttributes redirectAttributes) {
        return makeDefault(storeId, accountId, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/payments/bank-accounts/{accountId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String accountId, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), accountId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/bank-accounts/{accountId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String accountId, Model model, Locale locale) {
        return confirmDelete(storeId, accountId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/bank-accounts/{accountId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteAccount(@PathVariable String accountId, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), accountId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/bank-accounts/{accountId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDeleteAccount(@PathVariable String storeId, @PathVariable String accountId, Locale locale,
                                          RedirectAttributes redirectAttributes) {
        return delete(storeId, accountId, locale, redirectAttributes);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        requireStore(storeId);
        return render(storeId, null, BankAccountForm.empty(), Map.of(), model, locale);
    }

    private String showEdit(String storeId, String accountId, Model model, Locale locale) {
        BankAccount account = requireAccount(requireStore(storeId), accountId);
        return render(storeId, account, BankAccountForm.from(account), Map.of(), model, locale);
    }

    private String create(String storeId, BankAccountForm form, boolean async, Model model, Locale locale,
                          RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(storeId, null, form, errors, async, model, locale, response);
        }
        store.addBankAccount(form.toNewBankAccount(), form.isMakeDefault());
        storesRepository.save(store);
        return saved(storeId, "store.payments.account.added", async, model, locale, redirectAttributes, request, response);
    }

    private String update(String storeId, String accountId, BankAccountForm form, boolean async, Model model,
                          Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                          HttpServletResponse response) {
        Store store = requireStore(storeId);
        BankAccount account = requireAccount(store, accountId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(storeId, account, form, errors, async, model, locale, response);
        }
        form.applyTo(account);
        // Unticking the box on the default account is ignored: the store always has a default while it has accounts.
        if (form.isMakeDefault()) {
            store.makeDefaultBankAccount(accountId);
        }
        storesRepository.save(store);
        return saved(storeId, "store.payments.account.updated", async, model, locale, redirectAttributes, request, response);
    }

    private String makeDefault(String storeId, String accountId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        requireAccount(store, accountId);
        store.makeDefaultBankAccount(accountId);
        storesRepository.save(store);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.payments.account.madeDefault", null, locale));
        return "redirect:" + SettingsPaths.store(storeId, "/payments");
    }

    private String confirmDelete(String storeId, String accountId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        BankAccount account = requireAccount(store, accountId);
        String name = BankAccountView.of(account, "").title();
        // The last account takes the transfer details off offers and orders and leaves cash on delivery without a payee.
        String messageKey = store.getBankAccounts().size() == 1
                ? "store.payments.account.delete.message.last"
                : "store.payments.account.delete.message";
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.payments.account.delete.title", new Object[]{name}, locale),
                messageSource.getMessage(messageKey, null, locale),
                messageSource.getMessage("store.payments.account.delete.action", null, locale),
                SettingsPaths.store(storeId, "/payments/bank-accounts/" + accountId + "/delete"),
                SettingsPaths.store(storeId, "/payments")));
        model.addAttribute("backLabel", messageSource.getMessage("store.payments", null, locale));
        return "settings-confirm";
    }

    private String delete(String storeId, String accountId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (store.removeBankAccount(accountId)) {
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.payments.account.deleted", null, locale));
        }
        return "redirect:" + SettingsPaths.store(storeId, "/payments");
    }

    private String rejected(String storeId, BankAccount existing, BankAccountForm form, Map<String, String> errors,
                            boolean async, Model model, Locale locale, HttpServletResponse response) {
        String view = render(storeId, existing, form, errors, model, locale);
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return FORM_FRAGMENT;
        }
        return view;
    }

    private String saved(String storeId, String messageKey, boolean async, Model model, Locale locale,
                         RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        String paymentsPath = SettingsPaths.store(storeId, "/payments");
        String message = messageSource.getMessage(messageKey, null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, paymentsPath, message);
            render(storeId, null, BankAccountForm.empty(), Map.of(), model, locale);
            model.addAttribute("redirectTo", paymentsPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + paymentsPath;
    }

    private String render(String storeId, BankAccount existing, BankAccountForm form, Map<String, String> errors,
                          Model model, Locale locale) {
        String basePath = SettingsPaths.store(storeId, "/payments/bank-accounts");
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("currencies", CurrencyOptions.forPicker(form.getCurrency(), locale));
        model.addAttribute("formAction", existing == null ? basePath + "/new" : basePath + "/" + existing.getId());
        model.addAttribute("pageTitle", existing == null
                ? messageSource.getMessage("store.payments.account.new.title", null, locale)
                : messageSource.getMessage("store.payments.account.edit.title", null, locale));
        model.addAttribute("alreadyDefault", existing != null && existing.is_default());
        model.addAttribute("paymentsHref", SettingsPaths.store(storeId, "/payments"));
        model.addAttribute("backLabel", messageSource.getMessage("store.payments", null, locale));
        return VIEW;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private BankAccount requireAccount(Store store, String accountId) {
        return store.findBankAccount(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
