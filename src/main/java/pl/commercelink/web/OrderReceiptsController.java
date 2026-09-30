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
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.web.orders.OrderConfirmPages;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.ReceiptCloseForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Operator actions on an order's e-receipt attempts, from the e-receipt row of the order's documents card. Each
 * refusal names its reason (the layout's error flash); a success is the order page's own notice (OrderFlash), like
 * every other action of the page, and an attempt blocked at once a warning notice. Nothing here calls a provider.
 * "E-paragon" and "Wystaw ponownie" are confirmed first: the {@code a[data-cl-confirm]} links lead to a plain confirmation page here, which JavaScript turns into the
 * page's confirmation dialog; "Zamknij ręcznie" opens its dialog, or its own page here without JavaScript.
 */
@Controller
@RequiredArgsConstructor
public class OrderReceiptsController {

    private final ReceiptAttemptService attemptService;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/orders/{orderId}/receipts/reissue")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmReissue(@PathVariable String orderId, Locale locale, Model model,
                                 RedirectAttributes redirectAttributes) {
        // the page must not offer a confirmation the POST would refuse (a live attempt, an order no longer eligible)
        String refusal = attemptService.reissueRefusal(CustomSecurityContext.getStoreId(), orderId);
        if (refusal != null) {
            return refuse(orderId, refusal, locale, redirectAttributes);
        }
        String orderPath = "/dashboard/orders/" + orderId;
        String messageKey = attemptService.reissueConfirmMessageKey(CustomSecurityContext.getStoreId(), orderId);
        // a new attempt is not destructive: the primary button, as in the dialog
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("receipts.action.reissue.confirm.title", null, locale),
                messageSource.getMessage(messageKey != null ? messageKey : "receipts.action.reissue.confirm.message",
                        null, locale),
                messageSource.getMessage("receipts.action.reissue", null, locale),
                orderPath + "/receipts/reissue", orderPath, false), backLabel(orderId, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/receipts/reissue")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String reissue(@PathVariable String orderId, Locale locale, RedirectAttributes redirectAttributes) {
        return start(orderId, locale, redirectAttributes, "receipts.action.reissue.done",
                () -> attemptService.reissue(CustomSecurityContext.getStoreId(), orderId, actor()));
    }

    /**
     * The "E-paragon" entry of the "Issue" menu without JavaScript. An order the POST would refuse (it already has an
     * attempt, a POS sale without the customer's e-mail, ...) is sent back with the reason at once; the POST still
     * checks everything itself.
     */
    @GetMapping("/dashboard/orders/{orderId}/receipts/issue")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmIssue(@PathVariable String orderId, Locale locale, Model model,
                               RedirectAttributes redirectAttributes) {
        String refusal = attemptService.issueRefusal(CustomSecurityContext.getStoreId(), orderId);
        if (refusal != null) {
            return refuse(orderId, refusal, locale, redirectAttributes);
        }
        String orderPath = "/dashboard/orders/" + orderId;
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("receipts.action.issue.confirm.title", null, locale),
                messageSource.getMessage("receipts.action.issue.confirm.message", null, locale),
                messageSource.getMessage("receipts.action.issue.confirm.action", null, locale),
                orderPath + "/receipts/issue", orderPath, false), backLabel(orderId, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/receipts/issue")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String issue(@PathVariable String orderId, Locale locale, RedirectAttributes redirectAttributes) {
        return start(orderId, locale, redirectAttributes, "receipts.action.issue.done",
                () -> attemptService.issueManually(CustomSecurityContext.getStoreId(), orderId, actor()));
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

    /**
     * "Zamknij ręcznie" without JavaScript: the dialog's form on its own page. Only an attempt of this order that can
     * still be closed (issuing or waiting for the printer) gets the form; anything else goes back with the reason.
     */
    @GetMapping("/dashboard/orders/{orderId}/receipts/close")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String closePage(@PathVariable String orderId, @RequestParam String receiptKey, Locale locale, Model model,
                            RedirectAttributes redirectAttributes) {
        if (!receiptKey.startsWith(ReceiptAttemptKeys.orderPrefix(orderId))) {
            return refuse(orderId, "receipts.action.notFound", locale, redirectAttributes);
        }
        Optional<ReceiptAttempt> attempt = attemptService.attemptsOf(CustomSecurityContext.getStoreId(), orderId).stream()
                .filter(a -> receiptKey.equals(a.getReceiptKey())).findFirst();
        if (attempt.isEmpty()) {
            return refuse(orderId, "receipts.action.notFound", locale, redirectAttributes);
        }
        ReceiptAttemptState state = attempt.get().getState();
        if (state != ReceiptAttemptState.ISSUING && state != ReceiptAttemptState.PENDING) {
            return refuse(orderId, "receipts.action.close.notHung", locale, redirectAttributes);
        }
        model.addAttribute("close", new ReceiptCloseForm(orderId, receiptKey, attempt.get().getAttemptNo()));
        model.addAttribute("shortId", ConversionUtil.getShortenedId(orderId));
        return "orders/receipt-close";
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
            OrderFlash.saved(redirectAttributes, messageSource.getMessage(successKey, null, locale));
        } catch (ReceiptActionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(e.getMessageKey(), null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    /**
     * "E-paragon" and "Wystaw ponownie": an attempt the converter blocked at once (no lines, an unknown VAT rate, ...)
     * was sent nowhere, so it is not reported as being issued but as a warning pointing at the row that says why.
     */
    private String start(String orderId, Locale locale, RedirectAttributes redirectAttributes, String successKey,
                         Supplier<ReceiptAttempt> action) {
        try {
            ReceiptAttempt attempt = action.get();
            if (attempt != null && attempt.getState() == ReceiptAttemptState.BLOCKED) {
                OrderFlash.warning(redirectAttributes, messageSource.getMessage("receipts.action.blockedAtOnce", null, locale));
            } else {
                OrderFlash.saved(redirectAttributes, messageSource.getMessage(successKey, null, locale));
            }
        } catch (ReceiptActionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(e.getMessageKey(), null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    private String refuse(String orderId, String key, Locale locale, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(key, null, locale));
        return "redirect:/dashboard/orders/" + orderId;
    }

    /** The confirmation pages lead back to the order by its number, as the order page's own confirmation pages do. */
    private String backLabel(String orderId, Locale locale) {
        return messageSource.getMessage("order.page.title", new Object[]{ConversionUtil.getShortenedId(orderId)}, locale);
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
