package pl.commercelink.web;

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
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ReceiptSettingsForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Settings › E-receipts: the status of the e-receipt system and the automatic-receipts form. The page carries one
 * form: the system is chosen and its access details entered on its own subpage ({@link StoreReceiptSystemController}),
 * so a single "Save changes" can only mean the receipt settings. The store comes from the session (ADMIN) or the path
 * (SUPER_ADMIN), like every other settings page.
 */
@Controller
@RequiredArgsConstructor
public class StoreReceiptsSettingsController {

    private static final String VIEW = "store-receipts";
    private static final String RECEIPTS_FRAGMENT = VIEW + " :: receiptsForm";

    private final StoresRepository storesRepository;
    private final ReceiptSystems receiptSystems;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/receipts")
    @PreAuthorize("hasRole('ADMIN')")
    public String receipts(Model model) {
        return show(CustomSecurityContext.getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/receipts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminReceipts(@PathVariable String storeId, Model model) {
        return show(storeId, model);
    }

    @PostMapping("/dashboard/store/receipts")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveReceipts(@ModelAttribute ReceiptSettingsForm form,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               Model model, Locale locale, RedirectAttributes redirectAttributes,
                               HttpServletResponse response) {
        return saveReceipts(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/receipts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveReceipts(@PathVariable String storeId, @ModelAttribute ReceiptSettingsForm form,
                                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                                         HttpServletResponse response) {
        return saveReceipts(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, response);
    }

    private String show(String storeId, Model model) {
        Store store = requireStore(storeId);
        render(store, ReceiptSettingsForm.from(store.getReceiptConfiguration()), Map.of(), model);
        return VIEW;
    }

    private String saveReceipts(String storeId, ReceiptSettingsForm form, boolean async, Model model, Locale locale,
                                RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = new LinkedHashMap<>(form.validate());
        if (errors.isEmpty() && form.isEnabled() && !receiptSystems.status(store).configured()) {
            errors.put("enabled", "store.receipts.enabled.noSystem");
        }
        if (!errors.isEmpty()) {
            render(store, form, errors, model);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return RECEIPTS_FRAGMENT;
            }
            return VIEW;
        }
        form.applyTo(store, LocalDateTime.now());
        storesRepository.save(store);
        String message = messageSource.getMessage("store.receipts.update.success", null, locale);
        if (async) {
            render(store, ReceiptSettingsForm.from(store.getReceiptConfiguration()), Map.of(), model);
            model.addAttribute("savedMessage", message);
            return RECEIPTS_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + SettingsPaths.store(storeId, "/receipts");
    }

    private void render(Store store, ReceiptSettingsForm receipts, Map<String, String> receiptsErrors, Model model) {
        String storeId = store.getStoreId();
        model.addAttribute("systemStatus", receiptSystems.status(store));
        model.addAttribute("systemHref", SettingsPaths.store(storeId, "/receipts/system"));
        model.addAttribute("disconnectHref", SettingsPaths.store(storeId, "/receipts/system/disconnect"));
        model.addAttribute("receiptsForm", receipts);
        model.addAttribute("receiptsErrors", receiptsErrors);
        model.addAttribute("receiptsAction", SettingsPaths.store(storeId, "/receipts"));
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
