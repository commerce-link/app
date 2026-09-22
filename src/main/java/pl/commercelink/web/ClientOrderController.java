package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.clientaccess.ClientVerificationException;
import pl.commercelink.clientaccess.ClientVerificationPurpose;
import pl.commercelink.clientaccess.ClientVerificationRateLimiter;
import pl.commercelink.clientaccess.ClientVerificationService;
import pl.commercelink.clientaccess.ClientVerificationSubject;
import pl.commercelink.orders.ClientPreferredShippingDateException;
import pl.commercelink.orders.ClientPreferredShippingDateService;
import pl.commercelink.orders.ClientShippingAddressChangeException;
import pl.commercelink.orders.ClientShippingAddressChangeService;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.dtos.ClientOrderView;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
@RequiredArgsConstructor
@RequestMapping("/store/{storeId}/client/order/{orderId}")
public class ClientOrderController {

    private static final String NOT_FOUND = "error/404";
    static final String ADDRESS_STEP_CODE = "code";
    static final String ADDRESS_STEP_EDIT = "edit";
    private static final String PREFERRED_SHIPPING_ERROR_PREFIX = "client.order.preferred.shipping.error.";

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final StoresRepository storesRepository;
    private final CategoryLocalizer categoryLocalizer;
    private final ClientVerificationService clientVerificationService;
    private final ClientVerificationRateLimiter clientVerificationRateLimiter;
    private final ClientShippingAddressChangeService addressChangeService;
    private final ClientPreferredShippingDateService preferredShippingDateService;
    private final MessageSource messageSource;

    @GetMapping("")
    public String getOrderForClient(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId,
                                    @RequestParam(value = "v", required = false) String verificationId,
                                    @RequestParam(value = "t", required = false) String editToken,
                                    Model model, HttpServletResponse response, Locale locale) {
        ClientOrderContext context = load(storeId, orderId);
        if (context == null) {
            return NOT_FOUND;
        }

        boolean editable = addressChangeService.isEditable(context.order(), context.store());
        if (isNotBlank(editToken)) {
            prepareEditStep(context, editToken, editable, model, response, locale);
        } else if (isNotBlank(verificationId)) {
            prepareCodeStep(context, verificationId, editable, model, locale);
        }

        boolean preferredShippingEditable = preferredShippingDateService.isEditable(context.order(), context.store());
        ClientOrderView view = ClientOrderView.from(context.order(), orderItemsRepository.findByOrderId(orderId), context.store(),
                categoryLocalizer, editable, preferredShippingEditable);
        model.addAttribute("view", view);
        addStore(model, context.store());

        return "clientOrder";
    }

    @PostMapping("address/request-code")
    public String requestAddressCode(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes, Locale locale) {
        ClientOrderContext context = load(storeId, orderId);
        if (context == null) {
            return NOT_FOUND;
        }
        if (!clientVerificationRateLimiter.tryAcquire(clientIp(request))) {
            return redirectWithError(context, redirectAttributes, locale, ClientVerificationException.Reason.TOO_MANY_REQUESTS.name());
        }
        if (!addressChangeService.isEditable(context.order(), context.store())) {
            return redirectWithError(context, redirectAttributes, locale, ClientShippingAddressChangeException.Reason.NOT_EDITABLE.name());
        }

        try {
            String verificationId = clientVerificationService.issue(context.subject(), ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE,
                    context.order().getBillingDetails().getEmail(), context.order().getBillingDetails().getName());
            return "redirect:" + context.basePath() + "?v=" + verificationId;
        } catch (ClientVerificationException e) {
            return redirectWithError(context, redirectAttributes, locale, e.getReason().name());
        }
    }

    @PostMapping("address/confirm-code")
    public String confirmAddressCode(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId,
                                     @RequestParam("v") String verificationId, @RequestParam("code") String code,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes, Locale locale) {
        ClientOrderContext context = load(storeId, orderId);
        if (context == null) {
            return NOT_FOUND;
        }
        if (!clientVerificationRateLimiter.tryAcquire(clientIp(request))) {
            return redirectWithError(context, redirectAttributes, locale, ClientVerificationException.Reason.TOO_MANY_REQUESTS.name());
        }

        try {
            String editToken = clientVerificationService.confirm(context.subject(), verificationId, code.trim());
            return "redirect:" + context.basePath() + "?t=" + editToken;
        } catch (ClientVerificationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage(e.getReason().name(), locale));
            return "redirect:" + context.basePath() + "?v=" + verificationId;
        }
    }

    @PostMapping("address")
    public String changeAddress(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId,
                                @RequestParam("t") String editToken, @ModelAttribute("form") ShippingDetails form,
                                RedirectAttributes redirectAttributes, Locale locale) {
        ClientOrderContext context = load(storeId, orderId);
        if (context == null) {
            return NOT_FOUND;
        }

        ShippingDetails validated;
        try {
            validated = addressChangeService.validate(context.order(), form);
        } catch (ClientShippingAddressChangeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage(e.getReason().name(), locale));
            return "redirect:" + context.basePath() + "?t=" + editToken;
        }

        try {
            clientVerificationService.consume(context.subject(), editToken);
            addressChangeService.change(context.order(), validated, context.store());
        } catch (ClientVerificationException e) {
            return redirectWithError(context, redirectAttributes, locale, e.getReason().name());
        } catch (ClientShippingAddressChangeException e) {
            return redirectWithError(context, redirectAttributes, locale, e.getReason().name());
        }

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("client.order.address.success", null, locale));
        return "redirect:" + context.basePath();
    }

    @PostMapping("preferred-shipping")
    public String changePreferredShipping(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId,
                                          @RequestParam(value = "date", required = false) String date,
                                          RedirectAttributes redirectAttributes, Locale locale) {
        ClientOrderContext context = load(storeId, orderId);
        if (context == null) {
            return NOT_FOUND;
        }

        LocalDate requested;
        try {
            requested = isBlank(date) ? null : LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return redirectWithErrorKey(context, redirectAttributes, PREFERRED_SHIPPING_ERROR_PREFIX + "invalid.date", locale);
        }

        try {
            preferredShippingDateService.change(context.order(), requested, context.store());
        } catch (ClientPreferredShippingDateException e) {
            return redirectWithErrorKey(context, redirectAttributes, PREFERRED_SHIPPING_ERROR_PREFIX + reasonToKey(e.getReason().name()), locale);
        }

        String successKey = requested == null ? "client.order.preferred.shipping.cleared" : "client.order.preferred.shipping.success";
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage(successKey, null, locale));
        return "redirect:" + context.basePath();
    }

    private void prepareCodeStep(ClientOrderContext context, String verificationId, boolean editable, Model model, Locale locale) {
        if (!editable) {
            model.addAttribute("errorMessage", errorMessage(ClientShippingAddressChangeException.Reason.NOT_EDITABLE.name(), locale));
            return;
        }
        model.addAttribute("addressStep", ADDRESS_STEP_CODE);
        model.addAttribute("verificationId", verificationId);
        model.addAttribute("maskedEmail", maskEmail(context.order().getBillingDetails().getEmail()));
        model.addAttribute("codeValidityMinutes", ClientVerificationService.CODE_VALIDITY_MINUTES);
    }

    private void prepareEditStep(ClientOrderContext context, String editToken, boolean editable, Model model,
                                 HttpServletResponse response, Locale locale) {
        if (clientVerificationService.findByEditToken(context.subject(), editToken).isEmpty()) {
            model.addAttribute("errorMessage", errorMessage(ClientVerificationException.Reason.INVALID_TOKEN.name(), locale));
            return;
        }
        if (!editable) {
            model.addAttribute("errorMessage", errorMessage(ClientShippingAddressChangeException.Reason.NOT_EDITABLE.name(), locale));
            return;
        }
        // The edit token travels in the URL; same-origin keeps it out of third-party request logs (no-referrer would make
        // the browser send "Origin: null" on the form POST, which Spring rejects as an invalid CORS request).
        response.setHeader("Referrer-Policy", "same-origin");
        model.addAttribute("addressStep", ADDRESS_STEP_EDIT);
        model.addAttribute("editToken", editToken);
        model.addAttribute("form", context.order().getShippingDetails().copy());
        model.addAttribute("editTokenValidityMinutes", ClientVerificationService.EDIT_TOKEN_VALIDITY_MINUTES);
    }

    private ClientOrderContext load(String storeId, String orderId) {
        Store store = storesRepository.findById(storeId);
        if (store == null || !store.isClientOrderPageEnabled()) {
            return null;
        }
        Order order = ordersRepository.findById(storeId, orderId);
        // A completed order has nothing left to track; the link expires with it rather than staying public forever.
        if (order == null || order.hasStatus(OrderStatus.Completed)) {
            return null;
        }
        return new ClientOrderContext(store, order);
    }

    private static void addStore(Model model, Store store) {
        model.addAttribute("store", store);
        model.addAttribute("branding", store.getBranding() != null ? store.getBranding() : new Branding());
    }

    private String redirectWithError(ClientOrderContext context, RedirectAttributes redirectAttributes, Locale locale, String reason) {
        return redirectWithErrorKey(context, redirectAttributes, "client.order.address.error." + reasonToKey(reason), locale);
    }

    private String redirectWithErrorKey(ClientOrderContext context, RedirectAttributes redirectAttributes, String key, Locale locale) {
        redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(key, null, locale));
        return "redirect:" + context.basePath();
    }

    private String errorMessage(String reason, Locale locale) {
        return messageSource.getMessage("client.order.address.error." + reasonToKey(reason), null, locale);
    }

    private static String reasonToKey(String reason) {
        return reason.toLowerCase().replace('_', '.');
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return isNotBlank(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private record ClientOrderContext(Store store, Order order) {

        ClientVerificationSubject subject() {
            return ClientVerificationSubject.order(store.getStoreId(), order.getOrderId());
        }

        String basePath() {
            return "/store/" + store.getStoreId() + "/client/order/" + order.getOrderId();
        }
    }
}
