package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.deliveries.DeliveryTaxResolver;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.inventory.deliveries.PurchaseSubmission;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DropshipController extends BaseController {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final DropshipEligibility dropshipEligibility;
    private final SupplierPurchaseService supplierPurchaseService;
    private final DeliveryTaxResolver deliveryTaxResolver;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/orders/{orderId}/dropship")
    @PreAuthorize("hasRole('ADMIN')")
    public String dropshipConfirmation(@PathVariable("orderId") String orderId, Model model) {
        return showDropshipConfirmation(getStoreId(), orderId, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String dropshipConfirmationForSuperAdmin(@PathVariable("storeId") String storeId,
                                                    @PathVariable("orderId") String orderId, Model model) {
        return showDropshipConfirmation(storeId, orderId, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/dropship/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public String validateDropship(@PathVariable("orderId") String orderId,
                                   @ModelAttribute DeliveryCreationForm form, Model model, Locale locale) {
        return renderValidationFragment(getStoreId(), form, model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship/validate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String validateDropshipForSuperAdmin(@PathVariable("storeId") String storeId,
                                                @PathVariable("orderId") String orderId,
                                                @ModelAttribute DeliveryCreationForm form, Model model, Locale locale) {
        return renderValidationFragment(storeId, form, model, locale);
    }

    @PostMapping("/dashboard/orders/{orderId}/dropship/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDropship(@PathVariable("orderId") String orderId,
                                  @RequestParam("purchaseRef") String purchaseRef,
                                  @ModelAttribute DeliveryCreationForm form, Model model, Locale locale) {
        return executeDropship(getStoreId(), orderId, purchaseRef, form, model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship/confirm")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String confirmDropshipForSuperAdmin(@PathVariable("storeId") String storeId,
                                               @PathVariable("orderId") String orderId,
                                               @RequestParam("purchaseRef") String purchaseRef,
                                               @ModelAttribute DeliveryCreationForm form, Model model, Locale locale) {
        return executeDropship(storeId, orderId, purchaseRef, form, model, locale);
    }

    private String showDropshipConfirmation(String storeId, String orderId, Model model) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            return orderDetailsRedirect(storeId, orderId);
        }
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);
        Optional<String> provider = dropshipEligibility.eligibleProvider(order, orderItems);
        if (provider.isEmpty()) {
            return orderDetailsRedirect(storeId, orderId);
        }

        DeliveryCreationForm form = buildForm(storeId, order, orderItems, provider.get());
        addConfirmationModel(model, storeId, order, form);
        model.addAttribute("purchaseRef", UUID.randomUUID().toString());
        return "dropshipConfirmation";
    }

    private DeliveryCreationForm buildForm(String storeId, Order order, List<OrderItem> orderItems, String provider) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId(storeId);
        form.setProvider(provider);
        form.setTax(deliveryTaxResolver.resolveFor(provider));

        List<Allocation> allocations = orderItems.stream()
                .filter(OrderItem::isInAllocation)
                .map(item -> Allocation.fromOrderItem(order, item))
                .toList();
        form.setItems(DeliveryItem.groupAndUnify(allocations));
        form.getItems().forEach(item -> item.getAllocations()
                .forEach(allocation -> allocation.setSelected(true)));
        return form;
    }

    private void addConfirmationModel(Model model, String storeId, Order order, DeliveryCreationForm form) {
        model.addAttribute("form", form);
        model.addAttribute("order", order);
        model.addAttribute("consignee", order.getShippingDetails());
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("requiresApproval", supplierPurchaseService.requiresApproval(storeId, form.getProvider()));
    }

    private String renderValidationFragment(String storeId, DeliveryCreationForm form, Model model, Locale locale) {
        form.setStoreId(storeId);
        try {
            if (!supplierPurchaseService.isDropshipAvailable(storeId, form.getProvider())) {
                throw new IllegalStateException(form.getProvider());
            }
            model.addAttribute("validation", supplierPurchaseService.validate(storeId, form));
        } catch (Exception e) {
            model.addAttribute("validationError",
                    messageSource.getMessage("deliveries.purchase.confirm.checkFailed", null, locale)
                            + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
        }
        return "deliveryPurchaseConfirmation :: validationResult";
    }

    private String executeDropship(String storeId, String orderId, String purchaseRef,
                                   DeliveryCreationForm form, Model model, Locale locale) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            return orderDetailsRedirect(storeId, orderId);
        }
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);
        Optional<String> provider = dropshipEligibility.eligibleProvider(order, orderItems);
        if (provider.isEmpty() || !provider.get().equals(form.getProvider())) {
            return orderDetailsRedirect(storeId, orderId);
        }
        form.setStoreId(storeId);
        OperationResult<PurchaseSubmission> result =
                supplierPurchaseService.submitDropship(storeId, order, form, purchaseRef);

        if (!result.isSuccess()) {
            addConfirmationModel(model, storeId, order, form);
            model.addAttribute("purchaseRef", purchaseRef);
            model.addAttribute("errorMessage", messageSource.getMessage(result.getMessage(), null, locale));
            return "dropshipConfirmation";
        }

        String deliveryId = result.getPayload().deliveryId();
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/deliveries/details?deliveryId=%s", storeId, deliveryId)
                : "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    private String orderDetailsRedirect(String storeId, String orderId) {
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/orders/%s", storeId, orderId)
                : "redirect:/dashboard/orders/" + orderId;
    }
}
