package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.*;
import pl.commercelink.shipping.AbstractShippingController;
import pl.commercelink.shipping.ShipmentTrackingSubscriber;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import pl.commercelink.shipping.DeliveryTarget;
import pl.commercelink.shipping.ShippingPageView;
import pl.commercelink.orders.Shipment;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

@Controller
@RequestMapping("/dashboard/orders/{orderId}/shipping")
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class OrdersShippingController extends AbstractShippingController {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderLifecycle orderLifecycle;

    @Autowired
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;

    @Autowired
    private ShipmentTrackingSubscriber shipmentTrackingSubscriber;

    @GetMapping("")
    public String initiate(@PathVariable("orderId") String orderId, Model model,
                           RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(orderId);
        // the order page offers no "Zamów kuriera" in either case (OrderPageModelFactory#header); an address typed in
        // or an old bookmark gets the reason instead of a page that would fail at "Wyceń przesyłkę"
        String refusal = !shippingService.isAvailable(getStore()) ? noProviderKey() : refuseBooking(order);
        if (refusal != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(refusal, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        ShippingForm form = new ShippingForm(orderId, "orders");
        return renderShippingForm(getStore(), form, Collections.singletonList(order.getShippingDetails()), model);
    }

    @Override
    protected String renderShippingForm(Store store, ShippingForm form, List<ShippingDetails> shippingDetailsList, Model model) {
        Order order = requireOrder(form.getShippingEntityId());
        if (order.isCourierBookingEarlierThanPreferred(LocalDate.now())) {
            model.addAttribute("preferredShippingWarning", order.getPreferredShippingAt());
        }
        return super.renderShippingForm(store, form, shippingDetailsList, model);
    }

    @Override
    protected String noProviderKey() {
        return "shipping.error.no.provider.order";
    }

    @Override
    protected String refuseBooking(ShippingForm form) {
        return refuseBooking(requireOrder(form.getShippingEntityId()));
    }

    /** Every shipment already has its shipping data (a courier booked in another tab): nothing is left to book. */
    private static String refuseBooking(Order order) {
        return order.hasShipmentWithoutShippingData() ? null : "shipping.error.all.defined";
    }

    @Override
    protected double calculateShippingInsurance(ShippingForm form) {
        Order order = requireOrder(form.getShippingEntityId());
        return order.getTotalPrice();
    }

    @Override
    protected List<ShippingDetails> retrieveShippingDetailsList(ShippingForm form) {
        Order order = requireOrder(form.getShippingEntityId());
        return Collections.singletonList(order.getShippingDetails());
    }

    @Override
    protected DeliveryTarget resolveDeliveryTarget(ShippingForm form) {
        Order order = requireOrder(form.getShippingEntityId());
        String shippingProvider = getStore().getConfigurationValue(IntegrationType.SHIPPING_PROVIDER);
        return order.firstShipment()
                .map(shipment -> new DeliveryTarget(shippingProvider, shipment.getCarrier(),
                        shipment.getCollectionPointCode()))
                .orElseGet(() -> new DeliveryTarget(null, null, null));
    }

    @Override
    protected void onShippingCreated(ShippingForm form, List<Shipment> shipments) {
        Order order = requireOrder(form.getShippingEntityId());

        order.replaceShipments(shipments);
        shipmentTrackingSubscriber.subscribe(getStoreId(), order);

        orderLifecycle.update(order);
        orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.ShipmentCreated);
    }

    @Override
    protected ShippingPageView pageView(ShippingForm form) {
        Order order = requireOrder(form.getShippingEntityId());
        return new ShippingPageView("/dashboard/orders/" + order.getOrderId(), "order.page.title",
                order.getShortenedOrderId(), "shipping.lead.order", order.getShortenedOrderId());
    }

    private Order requireOrder(String orderId) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return order;
    }
}
