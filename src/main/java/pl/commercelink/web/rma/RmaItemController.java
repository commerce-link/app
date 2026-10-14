package pl.commercelink.web.rma;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.RmaItemForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Locale;
import java.util.Map;

/** The page of one RMA item: its serial number, reason and comment, and what is known about it (spec 2026-10-14). */
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
class RmaItemController {

    static final String VIEW = "rma-item";
    static final String FORM_FRAGMENT = VIEW + " :: itemForm";

    @Autowired private RMARepository rmaRepository;
    @Autowired private RMAItemsRepository rmaItemsRepository;
    @Autowired private OrderItemsRepository orderItemsRepository;
    @Autowired private DeliveriesRepository deliveriesRepository;
    @Autowired private SupplierLabels supplierLabels;
    @Autowired private RmaItemPageFactory pages;
    @Autowired private MessageSource messageSource;

    @GetMapping("/dashboard/rma/{rmaId}/items/{rmaItemId}")
    String show(@PathVariable String rmaId, @PathVariable String rmaItemId, Model model, Locale locale) {
        RMA rma = rma(rmaId);
        RMAItem item = item(rmaId, rmaItemId);
        model.addAttribute("page", page(rma, item, null, Map.of(), null, null, locale));
        return VIEW;
    }

    // only a page opened before the redesign posts here (still open during a rolling deploy); it sends no version
    @PostMapping("/dashboard/rma/{rmaId}/items/{rmaItemId}/save")
    String saveFromPageBeforeRedesign(@PathVariable String rmaId, @PathVariable String rmaItemId, @ModelAttribute("form") RmaItemForm form,
                                      @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                      Model model, Locale locale, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        return save(rmaId, rmaItemId, form, requestedWith, model, locale, response, redirectAttributes);
    }

    @PostMapping("/dashboard/rma/{rmaId}/items/{rmaItemId}")
    String save(@PathVariable String rmaId, @PathVariable String rmaItemId, @ModelAttribute("form") RmaItemForm form,
                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                Model model, Locale locale, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        boolean async = SettingsPaths.isAsync(requestedWith);
        RMA rma = rma(rmaId);
        RMAItem stored = item(rmaId, rmaItemId);
        String self = "/dashboard/rma/" + rmaId + "/items/" + rmaItemId;
        if (rma.getStatus() != null && rma.getStatus().isClosed()) {
            if (!async) {
                SettingsFlash.errorOnRedirect(redirectAttributes, text(locale, "rma.item.page.error.closed"));
                return "redirect:" + self;
            }
            return refused(rma, stored, form, Map.of(), text(locale, "rma.item.page.error.closed"), true, model, locale, response);
        }
        // a page left open while someone else saved, or an RMA action changed the item, must not overwrite it
        if (form.getVersion() != null && !form.getVersion().equals(RmaItemForm.versionOf(stored))) {
            return refused(rma, stored, form, Map.of(), text(locale, "rma.item.page.error.conflict"), async, model, locale, response);
        }
        Map<String, String> errors = form.validate(stored);
        if (!errors.isEmpty()) {
            return refused(rma, stored, form, errors, null, async, model, locale, response);
        }
        form.applyTo(stored);
        try {
            rmaItemsRepository.save(stored);
        } catch (ConditionalCheckFailedException e) {
            return refused(rma, item(rmaId, rmaItemId), form, Map.of(), text(locale, "rma.item.page.error.conflict"), async, model, locale, response);
        }
        String saved = text(locale, "rma.item.page.saved");
        if (!async) {
            SettingsFlash.onRedirect(redirectAttributes, saved);
            return "redirect:" + self;
        }
        model.addAttribute("page", page(rma, stored, null, Map.of(), null, saved, locale));
        return FORM_FRAGMENT;
    }

    // the form keeps the stale version on purpose: every resubmit is refused until the page is reloaded
    private String refused(RMA rma, RMAItem stored, RmaItemForm form, Map<String, String> errors, String message, boolean async,
                           Model model, Locale locale, HttpServletResponse response) {
        // 422 for every refusal: async-form.js swaps the form in only for 200 and 422
        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        model.addAttribute("page", page(rma, stored, form, errors, message, null, locale));
        return async ? FORM_FRAGMENT : VIEW;
    }

    private RmaItemPage page(RMA rma, RMAItem item, RmaItemForm posted, Map<String, String> errors, String errorMessage,
                        String savedMessage, Locale locale) {
        OrderItem orderItem = StringUtils.isAnyBlank(rma.getOrderId(), item.getItemId()) ? null
                : orderItemsRepository.findById(rma.getOrderId(), item.getItemId());
        Delivery delivery = StringUtils.isBlank(item.getDeliveryId()) ? null : deliveriesRepository.findById(getStoreId(), item.getDeliveryId());
        String supplier = delivery == null || delivery.getProvider() == null ? null : supplierLabels.forStoreId(getStoreId()).of(delivery.getProvider());
        return pages.page(getStoreId(), rma, item, orderItem, delivery, supplier, posted, errors, errorMessage, savedMessage, locale);
    }

    private RMA rma(String rmaId) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        if (rma == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return rma;
    }

    // the item table is keyed by RMA only, so the item belongs to the store through the RMA found above
    private RMAItem item(String rmaId, String rmaItemId) {
        RMAItem item = rmaItemsRepository.findById(rmaId, rmaItemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return item;
    }

    private String text(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
