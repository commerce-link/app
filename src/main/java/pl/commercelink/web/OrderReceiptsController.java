package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.receipts.ReceiptActionException;
import pl.commercelink.receipts.ReceiptAttemptKeys;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.Locale;

/**
 * Operator actions on an order's e-receipt attempts. Each refusal names its reason; nothing here calls a provider.
 * "Wystaw ponownie" is confirmed first: there is no button variant of {@code confirm-dialog.js}, only the
 * {@code a[data-cl-confirm]} link one used across the settings screens, so it links here for a plain confirmation
 * page and is enhanced into the same dialog with JavaScript, exactly like every other confirmed link in the app.
 */
@Controller
@RequiredArgsConstructor
public class OrderReceiptsController {

    private final ReceiptAttemptService attemptService;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/orders/{orderId}/receipts/reissue")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmReissue(@PathVariable String orderId, Locale locale, Model model) {
        String orderPath = "/dashboard/orders/" + orderId;
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("receipts.action.reissue.confirm.title", null, locale),
                messageSource.getMessage("receipts.action.reissue.confirm.message", null, locale),
                messageSource.getMessage("receipts.action.reissue", null, locale),
                orderPath + "/receipts/reissue",
                orderPath));
        model.addAttribute("backLabel", messageSource.getMessage("receipts.action.reissue.confirm.back", null, locale));
        return "settings-confirm";
    }

    @PostMapping("/dashboard/orders/{orderId}/receipts/reissue")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String reissue(@PathVariable String orderId, Locale locale, RedirectAttributes redirectAttributes) {
        return run(orderId, locale, redirectAttributes, "receipts.action.reissue.done",
                () -> attemptService.reissue(CustomSecurityContext.getStoreId(), orderId, actor()));
    }

    @PostMapping("/dashboard/orders/{orderId}/receipts/check")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String check(@PathVariable String orderId, @RequestParam String receiptKey, Locale locale,
                        RedirectAttributes redirectAttributes) {
        return run(orderId, locale, redirectAttributes, "receipts.action.check.done", () -> {
            requireOwnKey(orderId, receiptKey);
            attemptService.checkNow(CustomSecurityContext.getStoreId(), receiptKey);
        });
    }

    @PostMapping("/dashboard/orders/{orderId}/receipts/resend-email")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String resendEmail(@PathVariable String orderId, @RequestParam String receiptKey, Locale locale,
                              RedirectAttributes redirectAttributes) {
        return run(orderId, locale, redirectAttributes, "receipts.flash.emailResent", () -> {
            requireOwnKey(orderId, receiptKey);
            attemptService.resendEmail(CustomSecurityContext.getStoreId(), orderId, receiptKey, actor());
        });
    }

    @PostMapping("/dashboard/orders/{orderId}/receipts/close")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String close(@PathVariable String orderId, @RequestParam String receiptKey,
                        @RequestParam(required = false) String number, @RequestParam(required = false) String link,
                        Locale locale, RedirectAttributes redirectAttributes) {
        return run(orderId, locale, redirectAttributes, "receipts.action.close.done", () -> {
            requireOwnKey(orderId, receiptKey);
            if (StringUtils.isBlank(number)) {
                throw new ReceiptActionException("receipts.action.close.numberRequired");
            }
            attemptService.closeManually(CustomSecurityContext.getStoreId(), receiptKey, number.strip(), link, actor());
        });
    }

    private String run(String orderId, Locale locale, RedirectAttributes redirectAttributes, String successKey,
                       Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage(successKey, null, locale));
        } catch (ReceiptActionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(e.getMessageKey(), null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    private static void requireOwnKey(String orderId, String receiptKey) {
        if (!receiptKey.startsWith(ReceiptAttemptKeys.orderPrefix(orderId))) {
            throw new ReceiptActionException("receipts.action.notFound");
        }
    }

    private static String actor() {
        return CustomSecurityContext.getLoggedInUser().map(CustomUser::getName).orElse("Operator");
    }
}
