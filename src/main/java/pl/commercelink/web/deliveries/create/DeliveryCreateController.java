package pl.commercelink.web.deliveries.create;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveryFulfilmentUpdateService;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.deliveries.PurchaseSubmission;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.DeliveryFulfilmentUpdateForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The new-delivery flow for a warehouse batch (no order) and a dropship order (?order=): step 1 "what you order",
 * then step 2 through the integration (purchase, validate, confirm) or outside the system (manual, save), and back
 * to step 1 with the state. Every route has a store variant (ADMIN) and a super admin variant under
 * /dashboard/store/{storeId}. Ordering itself stays in the scopes' purchase services.
 */
@Controller
@RequiredArgsConstructor
public class DeliveryCreateController {

    private static final String STORE = "/dashboard/deliveries/create/{provider}";
    private static final String SUPER = "/dashboard/store/{storeId}/deliveries/create/{provider}";
    private static final String NOTHING_REQUESTED = "deliveries.create.error.nothingRequested";
    private static final String ITEM_NUMBER = "deliveries.create.error.itemNumber";
    private static final Pattern ITEM_NUMBER_FIELD =
            Pattern.compile("(items\\[\\d+]\\.(unitCost|requestedQty))|(suggestedItems\\[\\d+]\\..*)");
    private static final Pattern ROW_NUMBER_FIELD =
            Pattern.compile("(items|suggestedItems)\\[(\\d+)]\\.(unitCost|requestedQty)");
    private static final String FULFILMENT_INVALID = "error.message.delivery.fulfilment.invalid";
    private static final String CHECK_FAILED = "deliveries.purchase.confirm.checkFailed";
    private static final String VALIDATION_RESULT = "deliveries/create/purchase :: validationResult";

    private final DeliveryScopes scopes;
    private final SupplierPurchaseService supplierPurchaseService;
    private final DeliveryFulfilmentUpdateService fulfilmentUpdateService;
    private final SupplierLabels supplierLabels;
    private final MessageSource messageSource;

    // ---- step 1

    @GetMapping(STORE)
    @PreAuthorize("hasRole('ADMIN')")
    public String items(@PathVariable("provider") String provider,
                        @RequestParam(value = "order", required = false) String orderId,
                        @RequestParam(value = "from", required = false) String from,
                        Model model, RedirectAttributes flash, Locale locale) {
        return showItems(storeId(), provider, orderId, from, null, null, model, flash, locale);
    }

    @GetMapping(SUPER)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String itemsForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                     @RequestParam(value = "order", required = false) String orderId,
                                     @RequestParam(value = "from", required = false) String from,
                                     Model model, RedirectAttributes flash, Locale locale) {
        return showItems(storeId, provider, orderId, from, null, null, model, flash, locale);
    }

    @PostMapping(STORE + "/back")
    @PreAuthorize("hasRole('ADMIN')")
    public String back(@PathVariable("provider") String provider, @ModelAttribute("form") DeliveryCreationForm form,
                       BindingResult binding, @RequestParam(value = "order", required = false) String orderId,
                       @RequestParam(value = "from", required = false) String from,
                       Model model, RedirectAttributes flash, Locale locale) {
        return showStepOneAfterBack(storeId(), provider, orderId, from, form, binding, model, flash, locale);
    }

    @PostMapping(SUPER + "/back")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String backForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                    @ModelAttribute("form") DeliveryCreationForm form, BindingResult binding,
                                    @RequestParam(value = "order", required = false) String orderId,
                                    @RequestParam(value = "from", required = false) String from,
                                    Model model, RedirectAttributes flash, Locale locale) {
        return showStepOneAfterBack(storeId, provider, orderId, from, form, binding, model, flash, locale);
    }

    // ---- step 2, outside the system

    @PostMapping(STORE + "/manual")
    @PreAuthorize("hasRole('ADMIN')")
    public String manual(@PathVariable("provider") String provider, @ModelAttribute("form") DeliveryCreationForm form,
                         BindingResult binding, @RequestParam(value = "order", required = false) String orderId,
                         @RequestParam(value = "from", required = false) String from,
                         Model model, RedirectAttributes flash, Locale locale) {
        return showManual(storeId(), provider, orderId, from, form, binding, model, flash, locale);
    }

    @PostMapping(SUPER + "/manual")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String manualForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                      @ModelAttribute("form") DeliveryCreationForm form, BindingResult binding,
                                      @RequestParam(value = "order", required = false) String orderId,
                                      @RequestParam(value = "from", required = false) String from,
                                      Model model, RedirectAttributes flash, Locale locale) {
        return showManual(storeId, provider, orderId, from, form, binding, model, flash, locale);
    }

    @PostMapping(STORE + "/manual/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@PathVariable("provider") String provider, @ModelAttribute("form") DeliveryCreationForm form,
                       BindingResult binding, @RequestParam(value = "order", required = false) String orderId,
                       @RequestParam(value = "from", required = false) String from,
                       Model model, RedirectAttributes flash, Locale locale) {
        return executeSave(storeId(), provider, orderId, from, form, binding, model, flash, locale);
    }

    @PostMapping(SUPER + "/manual/save")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                    @ModelAttribute("form") DeliveryCreationForm form, BindingResult binding,
                                    @RequestParam(value = "order", required = false) String orderId,
                                    @RequestParam(value = "from", required = false) String from,
                                    Model model, RedirectAttributes flash, Locale locale) {
        return executeSave(storeId, provider, orderId, from, form, binding, model, flash, locale);
    }

    // ---- step 2, through the integration

    @PostMapping(STORE + "/purchase")
    @PreAuthorize("hasRole('ADMIN')")
    public String purchase(@PathVariable("provider") String provider, @ModelAttribute("form") DeliveryCreationForm form,
                           BindingResult binding, @RequestParam(value = "order", required = false) String orderId,
                           @RequestParam(value = "from", required = false) String from,
                           Model model, RedirectAttributes flash, Locale locale) {
        return showPurchase(storeId(), provider, orderId, from, form, binding, model, flash, locale);
    }

    @PostMapping(SUPER + "/purchase")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String purchaseForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                        @ModelAttribute("form") DeliveryCreationForm form, BindingResult binding,
                                        @RequestParam(value = "order", required = false) String orderId,
                                        @RequestParam(value = "from", required = false) String from,
                                        Model model, RedirectAttributes flash, Locale locale) {
        return showPurchase(storeId, provider, orderId, from, form, binding, model, flash, locale);
    }

    @PostMapping(STORE + "/purchase/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public String validate(@PathVariable("provider") String provider, @ModelAttribute("form") DeliveryCreationForm form,
                           BindingResult binding, @RequestParam(value = "order", required = false) String orderId,
                           @RequestParam(value = "from", required = false) String from,
                           Model model, RedirectAttributes flash, Locale locale) {
        return renderValidation(storeId(), provider, orderId, from, form, model, locale);
    }

    @PostMapping(SUPER + "/purchase/validate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String validateForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                        @ModelAttribute("form") DeliveryCreationForm form, BindingResult binding,
                                        @RequestParam(value = "order", required = false) String orderId,
                                        @RequestParam(value = "from", required = false) String from,
                                        Model model, RedirectAttributes flash, Locale locale) {
        return renderValidation(storeId, provider, orderId, from, form, model, locale);
    }

    @PostMapping(STORE + "/purchase/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirm(@PathVariable("provider") String provider, @RequestParam("purchaseRef") String purchaseRef,
                          @ModelAttribute("form") DeliveryCreationForm form, BindingResult binding,
                          @RequestParam(value = "order", required = false) String orderId,
                          @RequestParam(value = "from", required = false) String from,
                          Model model, RedirectAttributes flash, Locale locale) {
        return executePurchase(storeId(), provider, orderId, from, purchaseRef, form, binding, model, flash, locale);
    }

    @PostMapping(SUPER + "/purchase/confirm")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String confirmForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                       @RequestParam("purchaseRef") String purchaseRef,
                                       @ModelAttribute("form") DeliveryCreationForm form, BindingResult binding,
                                       @RequestParam(value = "order", required = false) String orderId,
                                       @RequestParam(value = "from", required = false) String from,
                                       Model model, RedirectAttributes flash, Locale locale) {
        return executePurchase(storeId, provider, orderId, from, purchaseRef, form, binding, model, flash, locale);
    }

    // ---- edit-product dialog (warehouse step 1)

    @PostMapping(value = STORE + "/fulfilment", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public FulfilmentUpdateResponse fulfilmentJson(@PathVariable("provider") String provider,
                                                   @ModelAttribute DeliveryFulfilmentUpdateForm update,
                                                   BindingResult binding, Locale locale) {
        return updateFulfilment(storeId(), provider, update, binding, locale);
    }

    @PostMapping(value = SUPER + "/fulfilment", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public FulfilmentUpdateResponse fulfilmentJsonForSuperAdmin(@PathVariable("storeId") String storeId,
                                                                @PathVariable("provider") String provider,
                                                                @ModelAttribute DeliveryFulfilmentUpdateForm update,
                                                                BindingResult binding, Locale locale) {
        return updateFulfilment(storeId, provider, update, binding, locale);
    }

    @PostMapping(STORE + "/fulfilment")
    @PreAuthorize("hasRole('ADMIN')")
    public String fulfilment(@PathVariable("provider") String provider,
                             @ModelAttribute DeliveryFulfilmentUpdateForm update, BindingResult binding,
                             RedirectAttributes flash, Locale locale) {
        return fulfilmentWithReload(storeId(), provider, update, binding, flash, locale);
    }

    @PostMapping(SUPER + "/fulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String fulfilmentForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("provider") String provider,
                                          @ModelAttribute DeliveryFulfilmentUpdateForm update, BindingResult binding,
                                          RedirectAttributes flash, Locale locale) {
        return fulfilmentWithReload(storeId, provider, update, binding, flash, locale);
    }

    // ---- implementation

    private String showItems(String storeId, String provider, String orderId, String from, DeliveryCreationForm posted,
                             String stepError, Model model, RedirectAttributes flash, Locale locale) {
        DeliveryCreateLinks links = links(storeId, provider, orderId, from);
        return withScope(storeId, provider, links, flash, locale, scope -> items(scope, links, posted, stepError, model));
    }

    private String items(DeliveryScope scope, DeliveryCreateLinks links, DeliveryCreationForm posted, String stepError,
                         Model model) {
        DeliveryCreationForm form = scope.plannedForm();
        if (form == null) {
            return "redirect:" + links.preview();
        }
        if (posted != null) {
            form.applyUserSelections(posted);
        }
        addPage(model, scope, links, form);
        model.addAttribute("stepError", stepError);
        return "deliveries/create/items";
    }

    private String showStepOneAfterBack(String storeId, String provider, String orderId, String from,
                                        DeliveryCreationForm posted, BindingResult binding, Model model,
                                        RedirectAttributes flash, Locale locale) {
        DeliveryCreateLinks links = links(storeId, provider, orderId, from);
        return withScope(storeId, provider, links, flash, locale, scope -> {
            // an emptied VAT field did not bind, and the form would otherwise carry the bean's own default back to step 1
            if (binding.hasFieldErrors("tax")) {
                posted.setTax(scope.defaultTax());
            }
            return items(scope, links, posted, null, model);
        });
    }

    // A cleared or malformed quantity or cost does not bind and would silently become 0 on a real order.
    private static String nothingRequested(DeliveryScope scope) {
        return scope.dropship() ? NOTHING_REQUESTED + ".dropship" : NOTHING_REQUESTED;
    }

    private static boolean hasUnreadableItemNumber(BindingResult binding) {
        return binding.getFieldErrors().stream().anyMatch(error -> ITEM_NUMBER_FIELD.matcher(error.getField()).matches());
    }

    /**
     * Step 1 again after a quantity or cost that did not bind. The field keeps the planned value instead of the 0 the
     * failed binding left behind (a second click would otherwise order at 0), and the page marks it.
     */
    private String itemsWithUnreadableNumbers(DeliveryScope scope, DeliveryCreateLinks links, DeliveryCreationForm posted,
                                              BindingResult binding, Model model) {
        DeliveryCreationForm form = scope.plannedForm();
        if (form == null) {
            return "redirect:" + links.preview();
        }
        Set<String> invalidFields = new LinkedHashSet<>();
        for (FieldError error : binding.getFieldErrors()) {
            Matcher field = ROW_NUMBER_FIELD.matcher(error.getField());
            if (field.matches()) {
                restorePlannedValue(form, posted, field.group(1), Integer.parseInt(field.group(2)), field.group(3))
                        .ifPresent(invalidFields::add);
            }
        }
        form.applyUserSelections(posted);
        addPage(model, scope, links, form);
        model.addAttribute("stepError", ITEM_NUMBER);
        model.addAttribute("invalidFields", invalidFields);
        return "deliveries/create/items";
    }

    /** Copies the planned value of one unbound row field into the posted form; answers "mfn|field" of the row. */
    private static Optional<String> restorePlannedValue(DeliveryCreationForm planned, DeliveryCreationForm posted,
                                                        String list, int index, String property) {
        if ("items".equals(list)) {
            if (posted.getItems() == null || index >= posted.getItems().size()) {
                return Optional.empty();
            }
            DeliveryItem row = posted.getItems().get(index);
            planned.getItems().stream().filter(item -> Objects.equals(item.getMfn(), row.getMfn())).findFirst()
                    .ifPresent(item -> {
                        if ("unitCost".equals(property)) {
                            row.setUnitCost(item.getUnitCost());
                        } else {
                            row.setRequestedQty(item.getRequestedQty());
                        }
                    });
            return Optional.of(row.getMfn() + "|" + property);
        }
        if (posted.getSuggestedItems() == null || index >= posted.getSuggestedItems().size()) {
            return Optional.empty();
        }
        SuggestedDeliveryItem row = posted.getSuggestedItems().get(index);
        planned.getSuggestedItems().stream().filter(item -> Objects.equals(item.getMfn(), row.getMfn())).findFirst()
                .ifPresent(item -> {
                    if ("unitCost".equals(property)) {
                        row.setUnitCost(item.getUnitCost());
                    } else {
                        row.setRequestedQty(item.getRequestedQty());
                    }
                });
        return Optional.of(row.getMfn() + "|" + property);
    }

    private String showManual(String storeId, String provider, String orderId, String from, DeliveryCreationForm form,
                              BindingResult binding, Model model, RedirectAttributes flash, Locale locale) {
        DeliveryCreateLinks links = links(storeId, provider, orderId, from);
        return withScope(storeId, provider, links, flash, locale, scope -> {
            if (hasUnreadableItemNumber(binding)) {
                return itemsWithUnreadableNumbers(scope, links, form, binding, model);
            }
            prepare(form, storeId, provider, scope);
            if (!form.hasRequestedItems()) {
                if (scope.dropship() && form.isRemoveUnselected()) {
                    scope.releaseUnselected(form);
                    flash.addFlashAttribute("successMessage", message("orders.dropship.unselectedReleased", locale));
                    return "redirect:" + links.order();
                }
                return items(scope, links, form, nothingRequested(scope), model);
            }
            addPage(model, scope, links, form);
            model.addAttribute("errors", Map.of());
            return "deliveries/create/manual";
        });
    }

    private String executeSave(String storeId, String provider, String orderId, String from, DeliveryCreationForm form,
                               BindingResult binding, Model model, RedirectAttributes flash, Locale locale) {
        DeliveryCreateLinks links = links(storeId, provider, orderId, from);
        return withScope(storeId, provider, links, flash, locale, scope -> {
            prepare(form, storeId, provider, scope);
            Map<String, String> errors = ManualOrderValidator.validate(form, binding, scope.requiresOrderIdentity());
            if (!errors.isEmpty()) {
                addPage(model, scope, links, form);
                model.addAttribute("errors", errors);
                return "deliveries/create/manual";
            }
            OperationResult<String> result = scope.save(form);
            if (!result.isSuccess()) {
                addPage(model, scope, links, form);
                model.addAttribute("errors", Map.of());
                model.addAttribute("failureMessage", message(result.getMessage(), locale));
                return "deliveries/create/manual";
            }
            return "redirect:" + links.deliveryDetails(result.getPayload());
        });
    }

    private String showPurchase(String storeId, String provider, String orderId, String from, DeliveryCreationForm form,
                                BindingResult binding, Model model, RedirectAttributes flash, Locale locale) {
        DeliveryCreateLinks links = links(storeId, provider, orderId, from);
        return withScope(storeId, provider, links, flash, locale, scope -> {
            if (!scope.purchaseAvailable()) {
                return "redirect:" + links.items();
            }
            if (hasUnreadableItemNumber(binding)) {
                return itemsWithUnreadableNumbers(scope, links, form, binding, model);
            }
            prepare(form, storeId, provider, scope);
            if (!form.hasRequestedItems()) {
                return items(scope, links, form, nothingRequested(scope), model);
            }
            addPage(model, scope, links, form);
            model.addAttribute("purchaseRef", UUID.randomUUID().toString());
            scope.addPurchaseModel(form, model);
            return "deliveries/create/purchase";
        });
    }

    private String renderValidation(String storeId, String provider, String orderId, String from,
                                    DeliveryCreationForm form, Model model, Locale locale) {
        DeliveryCreateLinks links = links(storeId, provider, orderId, from);
        DeliveryScopes.Resolution resolution = scopes.resolve(storeId, provider, links.orderId());
        // The page fetches this fragment into the availability card, so a refusal is answered there, not by a redirect.
        if (resolution instanceof DeliveryScopes.Resolution.Refused refused) {
            model.addAttribute("validationError", message(
                    refused.messageKey() != null ? refused.messageKey() : CHECK_FAILED, locale));
            return VALIDATION_RESULT;
        }
        DeliveryScope scope = ((DeliveryScopes.Resolution.Found) resolution).scope();
        form.setStoreId(storeId);
        form.setProvider(provider);
        addPage(model, scope, links, form);
        try {
            model.addAttribute("validation", scope.validate(form));
        } catch (Exception e) {
            model.addAttribute("validationError", message(CHECK_FAILED, locale)
                    + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
        }
        return VALIDATION_RESULT;
    }

    private String executePurchase(String storeId, String provider, String orderId, String from, String purchaseRef,
                                   DeliveryCreationForm form, BindingResult binding, Model model,
                                   RedirectAttributes flash, Locale locale) {
        DeliveryCreateLinks links = links(storeId, provider, orderId, from);
        // A confirmation sent again (browser back, a repeated request) opens the delivery it already created. Checked
        // before the scope: the order's lines now point at that delivery, so a dropship order no longer qualifies.
        Optional<String> placed = supplierPurchaseService.submittedDeliveryId(storeId, purchaseRef);
        if (placed.isPresent()) {
            return "redirect:" + links.deliveryDetails(placed.get());
        }
        return withScope(storeId, provider, links, flash, locale, scope -> {
            if (!scope.purchaseAvailable()) {
                return "redirect:" + links.items();
            }
            form.setStoreId(storeId);
            form.setProvider(provider);
            countTickedDropshipLines(form, scope);
            OrderData typed = OrderData.of(form);
            recordNoOrderData(form, scope);
            OperationResult<PurchaseSubmission> result = scope.submit(form, purchaseRef);
            if (!result.isSuccess()) {
                // the page carries the record step's data on the way back; only the order itself ignores it
                typed.restoreTo(form);
                addPage(model, scope, links, form);
                model.addAttribute("purchaseRef", purchaseRef);
                model.addAttribute("failureMessage", message(result.getMessage(), locale));
                scope.addPurchaseModel(form, model);
                return "deliveries/create/purchase";
            }
            return "redirect:" + links.deliveryDetails(result.getPayload().deliveryId());
        });
    }

    /**
     * Ordering through the integration asks for no order data: the supplier answers with the number and date, and the
     * costs, VAT and payment terms start from their defaults (editable later on the delivery). The hidden fields still
     * carry whatever "Zarejestruj zamówienie" was given before the operator went back, so they are reset here — a
     * leftover EUR would otherwise convert the claimed item costs.
     */
    private static void recordNoOrderData(DeliveryCreationForm form, DeliveryScope scope) {
        form.setSourceCurrency("PLN");
        form.setShippingCost(0);
        form.setPaymentCost(0);
        form.setPaymentTerms(0);
        form.setTax(scope.defaultTax());
    }

    /** What "Zarejestruj zamówienie" was given; an order through the integration does not read it. */
    private record OrderData(String currency, double shippingCost, double paymentCost, int paymentTerms, double tax) {

        static OrderData of(DeliveryCreationForm form) {
            return new OrderData(form.getSourceCurrency(), form.getShippingCost(), form.getPaymentCost(),
                    form.getPaymentTerms(), form.getTax());
        }

        void restoreTo(DeliveryCreationForm form) {
            form.setSourceCurrency(currency);
            form.setShippingCost(shippingCost);
            form.setPaymentCost(paymentCost);
            form.setPaymentTerms(paymentTerms);
            form.setTax(tax);
        }
    }

    private FulfilmentUpdateResponse updateFulfilment(String storeId, String provider, DeliveryFulfilmentUpdateForm update,
                                                      BindingResult binding, Locale locale) {
        OperationResult<Void> result = runFulfilmentUpdate(storeId, provider, update, binding);
        return result.isSuccess()
                ? FulfilmentUpdateResponse.saved(update, message("deliveries.create.fulfilment.saved", locale))
                : FulfilmentUpdateResponse.failed(message(result.getMessage(), locale));
    }

    private String fulfilmentWithReload(String storeId, String provider, DeliveryFulfilmentUpdateForm update,
                                        BindingResult binding, RedirectAttributes flash, Locale locale) {
        OperationResult<Void> result = runFulfilmentUpdate(storeId, provider, update, binding);
        if (!result.isSuccess()) {
            flash.addFlashAttribute("errorMessage", message(result.getMessage(), locale));
        }
        return "redirect:" + links(storeId, provider, null, null).items();
    }

    // a cost that did not bind (empty, not a number) is the service's own "invalid" answer, not HTTP 400
    private OperationResult<Void> runFulfilmentUpdate(String storeId, String provider, DeliveryFulfilmentUpdateForm update,
                                                      BindingResult binding) {
        return binding.hasErrors()
                ? OperationResult.failure(FULFILMENT_INVALID)
                : fulfilmentUpdateService.run(storeId, provider, update);
    }

    private String withScope(String storeId, String provider, DeliveryCreateLinks links, RedirectAttributes flash,
                             Locale locale, Function<DeliveryScope, String> action) {
        DeliveryScopes.Resolution resolution = scopes.resolve(storeId, provider, links.orderId());
        if (resolution instanceof DeliveryScopes.Resolution.Refused refused) {
            if (refused.messageKey() != null) {
                flash.addFlashAttribute("errorMessage", message(refused.messageKey(), locale));
            }
            return "redirect:" + links.order();
        }
        return action.apply(((DeliveryScopes.Resolution.Found) resolution).scope());
    }

    private void prepare(DeliveryCreationForm form, String storeId, String provider, DeliveryScope scope) {
        form.setStoreId(storeId);
        form.setProvider(provider);
        supplierPurchaseService.mergeSuggestedItems(form);
        countTickedDropshipLines(form, scope);
    }

    /**
     * A dropship row's quantity is the sum of its ticked lines. The page counts it in a hidden field, which a page
     * without its script (or restored from the browser cache) leaves stale, so the server counts it again.
     */
    private static void countTickedDropshipLines(DeliveryCreationForm form, DeliveryScope scope) {
        if (scope.dropship() && form.getItems() != null) {
            form.getItems().forEach(item -> item.setRequestedQty(item.getMinQty()));
        }
    }

    private void addPage(Model model, DeliveryScope scope, DeliveryCreateLinks links, DeliveryCreationForm form) {
        String supplierName = supplierLabels.forStoreId(scope.storeId()).of(scope.provider());
        model.addAttribute("form", form);
        model.addAttribute("page", DeliveryCreatePage.of(scope, links, supplierName, form));
    }

    private DeliveryCreateLinks links(String storeId, String provider, String orderId, String from) {
        return DeliveryCreateLinks.of(isSuperAdmin(), storeId, provider, orderId, from);
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    private static String storeId() {
        return CustomSecurityContext.getStoreId();
    }

    private static boolean isSuperAdmin() {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }
}
