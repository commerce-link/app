package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DropshipAssessment;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.web.deliveries.create.DeliveryCreateLinks;
import pl.commercelink.web.deliveries.create.DropshipRejectionMessages;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class DropshipController extends BaseController {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final DropshipEligibility dropshipEligibility;
    private final MessageSource messageSource;

    /** The dropship page moved to /deliveries/create/{supplier}?order=; old links keep working. */
    @GetMapping("/dashboard/orders/{orderId}/dropship")
    @PreAuthorize("hasRole('ADMIN')")
    public String dropshipCreate(@PathVariable("orderId") String orderId,
                                 @RequestParam(value = "provider", required = false) String provider,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        return redirectToCreate(getStoreId(), orderId, provider, redirectAttributes, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/dropship")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String dropshipCreateForSuperAdmin(@PathVariable("storeId") String storeId,
                                              @PathVariable("orderId") String orderId,
                                              @RequestParam(value = "provider", required = false) String provider,
                                              RedirectAttributes redirectAttributes, Locale locale) {
        return redirectToCreate(storeId, orderId, provider, redirectAttributes, locale);
    }

    private String redirectToCreate(String storeId, String orderId, String provider,
                                    RedirectAttributes redirectAttributes, Locale locale) {
        String base = isSuperAdmin() ? "/dashboard/store/" + storeId : "/dashboard";
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            return "redirect:" + base + "/orders/" + orderId;
        }
        if (provider == null) {
            DropshipAssessment assessment = dropshipEligibility.assess(order, orderItemsRepository.findByOrderId(orderId));
            if (!assessment.hasProviders()) {
                redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(
                        DropshipRejectionMessages.keyFor(assessment.rejection()), null, locale));
                return "redirect:" + base + "/orders/" + orderId;
            }
            if (assessment.providers().size() > 1) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage("orders.dropship.chooseProvider", null, locale));
                return "redirect:" + base + "/deliveries/preview";
            }
            provider = assessment.providers().getFirst();
        }
        return "redirect:" + DeliveryCreateLinks.of(isSuperAdmin(), storeId, provider, orderId, DeliveryCreateLinks.FROM_ORDER).items();
    }
}
