package pl.commercelink.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.payments.statements.BankStatementSniffer;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.PayoutChannel;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.BankStatementFormatForm;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.settings.BankAccountView;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.StatementPreview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The statement format of one bank account: how to read the CSV its bank exports, so operators can upload statements
 * on the Payments page and book the customers' transfers from them. A sample file fills the form in on its first
 * upload and is read again under the form on every "Odśwież podgląd"; it is never stored. The store comes from the
 * session (ADMIN) or the path (SUPER_ADMIN), the account from the path.
 */
@Controller
@RequiredArgsConstructor
public class StoreBankStatementController {

    private static final String VIEW = "store-bank-statement";
    private static final String PREVIEW = "preview";
    private static final String SAVE = "save";
    // the sample travels back with the form as Base64; a statement of a few months is far below this
    private static final long SAMPLE_LIMIT = 2 * 1024 * 1024;

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/payments/bank-accounts/{accountId}/statement")
    @PreAuthorize("hasRole('ADMIN')")
    public String show(@PathVariable String accountId, Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), accountId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/bank-accounts/{accountId}/statement")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminShow(@PathVariable String storeId, @PathVariable String accountId, Model model, Locale locale) {
        return show(storeId, accountId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/bank-accounts/{accountId}/statement")
    @PreAuthorize("hasRole('ADMIN')")
    public String submit(@PathVariable String accountId, @ModelAttribute BankStatementFormatForm form,
                         @RequestParam(required = false) MultipartFile sample,
                         @RequestParam(defaultValue = SAVE) String action,
                         Model model, Locale locale, RedirectAttributes redirectAttributes) {
        return submit(CustomSecurityContext.getStoreId(), accountId, form, sample, action, model, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/bank-accounts/{accountId}/statement")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSubmit(@PathVariable String storeId, @PathVariable String accountId,
                                   @ModelAttribute BankStatementFormatForm form,
                                   @RequestParam(required = false) MultipartFile sample,
                                   @RequestParam(defaultValue = SAVE) String action,
                                   Model model, Locale locale, RedirectAttributes redirectAttributes) {
        return submit(storeId, accountId, form, sample, action, model, locale, redirectAttributes);
    }

    private String show(String storeId, String accountId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        BankAccount account = requireAccount(store, accountId);
        return render(store, account, BankStatementFormatForm.from(account.getStatementFormat()), Map.of(), null, model, locale);
    }

    private String submit(String storeId, String accountId, BankStatementFormatForm form, MultipartFile sample,
                          String action, Model model, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        BankAccount account = requireAccount(store, accountId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (sample != null && !sample.isEmpty()) {
            if (sample.getSize() > SAMPLE_LIMIT) {
                errors.put("sample", "store.payments.statement.error.sampleSize");
            } else {
                byte[] content = read(sample);
                if (content == null) {
                    errors.put("sample", "store.payments.statement.error.sampleRead");
                } else {
                    form.attachSample(content, sample.getOriginalFilename());
                    // a first sample fills in what the operator has not set yet; a later one only refreshes the preview
                    if (form.hasNoColumns()) {
                        form = form.withGuess(BankStatementSniffer.guess(content));
                    }
                }
            }
        }
        if (SAVE.equals(action) && errors.isEmpty()) {
            errors.putAll(form.validate());
            if (errors.isEmpty()) {
                account.setStatementFormat(form.toFormat());
                storesRepository.save(store);
                SettingsFlash.onRedirect(redirectAttributes,
                        messageSource.getMessage("store.payments.statement.saved", null, locale));
                return "redirect:" + SettingsPaths.store(storeId, "/payments");
            }
        }
        return render(store, account, form.withEmptyChannelRows(), errors, preview(form, account, locale), model, locale);
    }

    private StatementPreview preview(BankStatementFormatForm form, BankAccount account, Locale locale) {
        byte[] sample = form.sample();
        if (sample == null) {
            return null;
        }
        return StatementPreview.of(sample, form.getSampleName(), form.toPreviewFormat(), account.getIban(), messageSource, locale);
    }

    private String render(Store store, BankAccount account, BankStatementFormatForm form, Map<String, String> errors,
                          StatementPreview preview, Model model, Locale locale) {
        String storeId = store.getStoreId();
        String accountTitle = BankAccountView.of(account, "").title();
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("preview", preview);
        model.addAttribute("headers", preview == null ? List.of() : preview.headers());
        model.addAttribute("encodings", BankStatementFormatForm.ENCODINGS.stream()
                .map(encoding -> new PickerOption(encoding, encoding)).toList());
        model.addAttribute("separators", BankStatementFormatForm.SEPARATORS.stream()
                .map(separator -> new PickerOption(separator,
                        messageSource.getMessage("store.payments.statement.separator." + separatorKey(separator), null, locale)))
                .toList());
        model.addAttribute("dateFormats", BankStatementFormatForm.DATE_FORMATS.stream()
                .map(pattern -> new PickerOption(pattern, pattern.toUpperCase(Locale.ROOT).replace("YYYY", "RRRR")))
                .toList());
        model.addAttribute("decimalMarks", List.of(
                new PickerOption(BankStatementFormatForm.COMMA, "1 234,56"),
                new PickerOption(BankStatementFormatForm.DOT, "1234.56")));
        model.addAttribute("sourceOptions", sourceOptions(store, form, locale));
        model.addAttribute("methodOptions", methodOptions(locale));
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/payments/bank-accounts/" + account.getId() + "/statement"));
        model.addAttribute("pageTitle", messageSource.getMessage("store.payments.statement.title", new Object[]{accountTitle}, locale));
        model.addAttribute("paymentsHref", SettingsPaths.store(storeId, "/payments"));
        model.addAttribute("backLabel", messageSource.getMessage("store.payments", null, locale));
        return VIEW;
    }

    /** Where a payout's orders come from: any, the store's own shop, or one of its marketplaces. */
    private List<PickerOption> sourceOptions(Store store, BankStatementFormatForm form, Locale locale) {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("", text("store.payments.statement.channels.any.source", locale));
        options.put(PayoutChannel.SHOP, text("store.payments.statement.channels.shop", locale));
        Objects.requireNonNullElse(store.getMarketplaces(), List.<MarketplaceIntegration>of())
                .forEach(marketplace -> options.put(marketplace.getName(), marketplace.getName()));
        keepSaved(options, form, BankStatementFormatForm.ChannelRow::getSource);
        return pickerOptions(options);
    }

    private List<PickerOption> methodOptions(Locale locale) {
        List<PickerOption> options = new ArrayList<>();
        options.add(new PickerOption("", text("store.payments.statement.channels.any.method", locale)));
        for (PaymentSource source : PaymentSource.values()) {
            options.add(new PickerOption(source.name(), text("PaymentSource." + source.name(), locale)));
        }
        return options;
    }

    private static void keepSaved(Map<String, String> options, BankStatementFormatForm form,
                                  Function<BankStatementFormatForm.ChannelRow, String> value) {
        form.getChannels().stream()
                .filter(Objects::nonNull)
                .map(value)
                .filter(StringUtils::isNotBlank)
                .forEach(saved -> options.putIfAbsent(saved, saved));
    }

    private static List<PickerOption> pickerOptions(Map<String, String> options) {
        List<PickerOption> picked = new ArrayList<>();
        options.forEach((value, label) -> picked.add(new PickerOption(value, label)));
        return picked;
    }

    private String text(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    private static String separatorKey(String separator) {
        return switch (separator) {
            case ";" -> "semicolon";
            case "," -> "comma";
            case "|" -> "pipe";
            default -> "tab";
        };
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            return null;
        }
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
