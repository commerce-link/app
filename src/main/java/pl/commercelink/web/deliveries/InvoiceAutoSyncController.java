package pl.commercelink.web.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.InvoiceAutoSyncResult;
import pl.commercelink.inventory.deliveries.InvoiceAutoSyncService;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Połącz i zsynchronizuj faktury" on the deliveries list: runs the automatic sync and reports it on the backlog list. */
@Controller
@RequiredArgsConstructor
public class InvoiceAutoSyncController {

    /** Flash attributes of the run's outcome, shown at the top of the list like the Payments page shows its own. */
    public static final String NOTICE = "deliveriesNotice";
    public static final String ERROR = "deliveriesError";

    private static final String BACKLOG_LIST = "redirect:/dashboard/deliveries?focus=invoice";

    private final InvoiceAutoSyncService service;
    private final MessageSource messages;

    @PostMapping("/dashboard/deliveries/auto-sync-invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public String autoSync(@RequestParam(required = false) String continueAfter, RedirectAttributes redirect, Locale locale) {
        InvoiceAutoSyncResult result = service.run(CustomSecurityContext.getStoreId(), parseTime(continueAfter));
        if (!result.configured()) {
            redirect.addFlashAttribute(ERROR, text(locale, "payments.sync.notConfigured"));
            return BACKLOG_LIST;
        }
        if (!result.failed().isEmpty()) {
            redirect.addFlashAttribute(ERROR,
                    text(locale, "deliveries.list.invoiceAutoSync.result.failed", String.join(", ", result.failed())));
        }
        redirect.addFlashAttribute(NOTICE, summary(result, locale));
        if (result.continueAfter() != null) {
            // the button on the list sends it back, so the next run starts where this one stopped
            redirect.addFlashAttribute("invoiceAutoSyncContinueAfter", result.continueAfter().toString());
        }
        return BACKLOG_LIST;
    }

    private String summary(InvoiceAutoSyncResult result, Locale locale) {
        if (result.checked() == 0 && result.remaining() == 0) {
            return text(locale, "deliveries.list.invoiceAutoSync.result.none");
        }
        List<String> parts = new ArrayList<>();
        parts.add(result.synced().isEmpty()
                ? text(locale, "deliveries.list.invoiceAutoSync.result.synced.none")
                : text(locale, "deliveries.list.invoiceAutoSync.result.synced", result.synced().size(), String.join(", ", result.synced())));
        if (result.unchanged() > 0) {
            parts.add(text(locale, "deliveries.list.invoiceAutoSync.result.unchanged", result.unchanged(), result.noInvoice(),
                    result.severalInvoices(), result.needsReview()));
        }
        if (result.remaining() > 0) {
            parts.add(text(locale, "deliveries.list.invoiceAutoSync.result.remaining", result.remaining()));
        }
        return String.join(" ", parts);
    }

    private static LocalDateTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
