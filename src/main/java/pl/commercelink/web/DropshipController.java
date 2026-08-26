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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.deliveries.DeliveryTaxResolver;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.inventory.deliveries.DropshipPurchaseService;
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
    private final DropshipPurchaseService dropshipPurchaseService;
    private final DeliveryTaxResolver deliveryTaxResolver;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/orders/{orderId}/dropship")
    @PreAuthorize("hasRole('ADMIN')")
    public String dropshipCreate(@PathVariable("orderId") String orderId, Model model) {
        return showDropshipCreate(getStoreId(), orderId, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String dropshipCreateForSuperAdmin(@PathVariable("storeId") String storeId,
                                              @PathVariable("orderId") String orderId, Model model) {
        return showDropshipCreate(storeId, orderId, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/dropship/purchase")
    @PreAuthorize("hasRole('ADMIN')")
    public String dropshipPurchase(@PathVariable("orderId") String orderId,
                                   @ModelAttribute DeliveryCreationForm form, Model model) {
        return showDropshipConfirmation(getStoreId(), orderId, form, model);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship/purchase")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String dropshipPurchaseForSuperAdmin(@PathVariable("storeId") String storeId,
                                                @PathVariable("orderId") String orderId,
                                                @ModelAttribute DeliveryCreationForm form, Model model) {
        return showDropshipConfirmation(storeId, orderId, form, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/dropship/purchase/back")
    @PreAuthorize("hasRole('ADMIN')")
    public String backFromDropshipConfirmation(@PathVariable("orderId") String orderId,
                                               @ModelAttribute DeliveryCreationForm posted, Model model) {
        return showDropshipCreate(getStoreId(), orderId, model, posted);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship/purchase/back")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String backFromDropshipConfirmationForSuperAdmin(@PathVariable("storeId") String storeId,
                                                            @PathVariable("orderId") String orderId,
                                                            @ModelAttribute DeliveryCreationForm posted, Model model) {
        return showDropshipCreate(storeId, orderId, model, posted);
    }

    @PostMapping("/dashboard/orders/{orderId}/dropship/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createManualDropship(@PathVariable("orderId") String orderId,
                                       @ModelAttribute DeliveryCreationForm form,
                                       RedirectAttributes redirectAttributes, Locale locale) {
        return executeManualDropship(getStoreId(), orderId, form, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship/create")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String createManualDropshipForSuperAdmin(@PathVariable("storeId") String storeId,
                                                    @PathVariable("orderId") String orderId,
                                                    @ModelAttribute DeliveryCreationForm form,
                                                    RedirectAttributes redirectAttributes, Locale locale) {
        return executeManualDropship(storeId, orderId, form, redirectAttributes, locale);
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

    private String showDropshipCreate(String storeId, String orderId, Model model) {
        return showDropshipCreate(storeId, orderId, model, null);
    }

    private String showDropshipCreate(String storeId, String orderId, Model model, DeliveryCreationForm posted) {
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
        if (posted != null) {
            form.applyUserSelections(posted);
        }
        addConfirmationModel(model, storeId, order, form);
        return "dropshipCreate";
    }

    private Optional<Order> eligibleOrderMatching(String storeId, String orderId, DeliveryCreationForm form) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            return Optional.empty();
        }
        Optional<String> provider = eligibleProviderFor(order);
        if (provider.isEmpty() || !provider.get().equals(form.getProvider())) {
            return Optional.empty();
        }
        return Optional.of(order);
    }

    private String showDropshipConfirmation(String storeId, String orderId, DeliveryCreationForm form, Model model) {
        Optional<Order> order = eligibleOrderMatching(storeId, orderId, form);
        if (order.isEmpty()) {
            return orderDetailsRedirect(storeId, orderId);
        }
        form.setStoreId(storeId);
        addConfirmationModel(model, storeId, order.get(), form);
        model.addAttribute("purchaseRef", UUID.randomUUID().toString());
        return "dropshipConfirmation";
    }

    private String executeManualDropship(String storeId, String orderId, DeliveryCreationForm form,
                                         RedirectAttributes redirectAttributes, Locale locale) {
        Optional<Order> order = eligibleOrderMatching(storeId, orderId, form);
        if (order.isEmpty()) {
            return orderDetailsRedirect(storeId, orderId);
        }
        form.setStoreId(storeId);
        if (!form.hasRequestedItems() && form.isRemoveUnselected()) {
            dropshipPurchaseService.releaseUnselected(storeId, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("orders.dropship.unselectedReleased", null, locale));
            return orderDetailsRedirect(storeId, orderId);
        }
        OperationResult<String> result = dropshipPurchaseService.createManualDropship(storeId, order.get(), form);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
            return dropshipCreateRedirect(storeId, orderId);
        }
        return deliveryDetailsRedirect(storeId, result.getPayload());
    }

    private Optional<String> eligibleProviderFor(Order order) {
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
        return dropshipEligibility.eligibleProvider(order, orderItems);
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
            if (!dropshipPurchaseService.isDropshipAvailable(storeId, form.getProvider())) {
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
        Optional<Order> order = eligibleOrderMatching(storeId, orderId, form);
        if (order.isEmpty()) {
            return orderDetailsRedirect(storeId, orderId);
        }
        form.setStoreId(storeId);
        OperationResult<PurchaseSubmission> result =
                dropshipPurchaseService.submitDropship(storeId, order.get(), form, purchaseRef);

        if (!result.isSuccess()) {
            addConfirmationModel(model, storeId, order.get(), form);
            model.addAttribute("purchaseRef", purchaseRef);
            model.addAttribute("errorMessage", messageSource.getMessage(result.getMessage(), null, locale));
            return "dropshipConfirmation";
        }
        return deliveryDetailsRedirect(storeId, result.getPayload().deliveryId());
    }

    private String orderDetailsRedirect(String storeId, String orderId) {
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/orders/%s", storeId, orderId)
                : "redirect:/dashboard/orders/" + orderId;
    }

    private String deliveryDetailsRedirect(String storeId, String deliveryId) {
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/deliveries/details?deliveryId=%s", storeId, deliveryId)
                : "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    private String dropshipCreateRedirect(String storeId, String orderId) {
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/orders/%s/dropship", storeId, orderId)
                : "redirect:/dashboard/orders/" + orderId + "/dropship";
    }
}
