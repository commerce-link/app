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
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.shipping.AbstractShippingController;
import pl.commercelink.shipping.ParcelForm;
import pl.commercelink.shipping.ShippingIntegrationChoice;
import pl.commercelink.shipping.ShippingIntegrationOption;
import pl.commercelink.shipping.ShippingIntegrationViews;
import pl.commercelink.shipping.api.ShipmentProposal;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.shipping.ShipmentCreationCheckRequest;
import pl.commercelink.shipping.ShipmentOwnerType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import pl.commercelink.shipping.DeliveryTarget;
import pl.commercelink.shipping.ShippingPageView;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

@Controller
@RequestMapping("/dashboard/orders/{orderId}/shipping")
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class OrdersShippingController extends AbstractShippingController {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private ShippingIntegrationChoice shippingIntegrationChoice;

    @Autowired
    private ShippingIntegrationViews shippingIntegrationViews;

    @GetMapping("")
    public String initiate(@PathVariable("orderId") String orderId,
                           @RequestParam(value = "provider", required = false) String provider,
                           @RequestParam(value = "packageTemplateId", required = false) String packageTemplateId,
                           Model model, RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(orderId);
        Store store = getStore();
        // the order page offers no "Nadaj przesyłkę" in either case (OrderPageModelFactory#header); an address typed in
        // or an old bookmark gets the reason instead of a page that would fail at "Wyceń przesyłkę"
        String refusal = !shippingService.isAvailableFor(store, order) ? noProviderKey() : refuseBooking(order);
        if (refusal != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(refusal, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        ShippingForm form = new ShippingForm(orderId, "orders");
        form.setProvider(provider);
        form.setPackageTemplateId(packageTemplateId);
        return renderShippingForm(store, form, Collections.singletonList(order.getShippingDetails()), model);
    }

    @Override
    protected String renderShippingForm(Store store, ShippingForm form, List<ShippingDetails> shippingDetailsList, Model model) {
        Order order = requireOrder(form.getShippingEntityId());
        if (order.isCourierBookingEarlierThanPreferred(LocalDate.now())) {
            model.addAttribute("preferredShippingWarning", order.getPreferredShippingAt());
        }
        Locale locale = LocaleContextHolder.getLocale();
        List<ShippingIntegrationOption> options = integrationOptions(store, order, model);
        String selected = ShippingIntegrationChoice.selected(options, form.getProvider());
        model.addAttribute("integrationChoice", shippingIntegrationViews.choice(options, selected, locale));
        if (selected == null) {
            model.addAttribute("shippingUnavailable", messageSource.getMessage("shipping.integration.none", null, locale));
        }
        Optional<ShippingIntegrationOption> allegro = ShippingIntegrationChoice.availableNamed(options, selected)
                .filter(ShippingIntegrationOption::isAllegro);
        if (allegro.isPresent()) {
            if (!model.containsAttribute("allegroErrors")) {
                model.addAttribute("allegroErrors", Map.of());
            }
            form.setProvider(ShippingIntegrationChoice.ALLEGRO);
            prefillAllegro(form, order, store, allegro.get().proposal());
            model.addAttribute("allegroShipping",
                    shippingIntegrationViews.allegro(allegro.get().proposal(), order, store, locale));
        } else if (options.size() > 1) {
            // the default integration's steps carry it, so a re-rendered step does not switch back to Allegro
            form.setProvider(selected);
        }
        return super.renderShippingForm(store, form, shippingDetailsList, model);
    }

    /** The options computed earlier in this request (the Allegro create step), else asked now: Allegro once per page. */
    @SuppressWarnings("unchecked")
    private List<ShippingIntegrationOption> integrationOptions(Store store, Order order, Model model) {
        Object known = model.getAttribute("integrationOptions");
        if (known instanceof List<?> list) {
            return (List<ShippingIntegrationOption>) list;
        }
        List<ShippingIntegrationOption> options = shippingIntegrationChoice.forOrder(store, order);
        model.addAttribute("integrationOptions", options);
        return options;
    }

    /**
     * The first visit of the Allegro form (or a template picked): one parcel of the chosen template (the store's
     * default one before any choice), cash on delivery of the unpaid amount, insurance at least that much and at most
     * the method's limit. A form posted back with errors keeps what the operator typed.
     */
    private void prefillAllegro(ShippingForm form, Order order, Store store, ShipmentProposal proposal) {
        if (!form.getParcels().isEmpty()) {
            return;
        }
        String templateId = form.getPackageTemplateId() != null ? form.getPackageTemplateId()
                : store.getPackageTemplates().stream().filter(PackageTemplate::isDefault).map(PackageTemplate::getId)
                        .findFirst().orElse(null);
        form.setPackageTemplateId(templateId);
        List<ParcelForm> parcels = templateId == null ? List.of()
                : shippingService.retrieveParcelsListBasedOnPackageTemplate(order.getTotalPrice(), templateId, store);
        ParcelForm parcel = parcels.isEmpty() ? ParcelForm.empty() : parcels.get(0);
        parcel.setType("package");
        double unpaid = order.getUnpaidAmount();
        if (unpaid > 0) {
            form.setCashOnDelivery(true);
            form.setCashOnDeliveryAmount(unpaid);
        }
        int insurance = Math.max(parcel.getValue(), (int) Math.ceil(form.isCashOnDelivery() ? unpaid : 0));
        if (proposal.maxInsurance() != null) {
            insurance = Math.min(insurance, proposal.maxInsurance().intValue());
        }
        parcel.setValue(insurance);
        form.setParcels(new ArrayList<>(List.of(parcel)));
    }

    @Override
    protected String noProviderKey() {
        return "shipping.error.no.provider.order";
    }

    @Override
    protected String refuseBooking(ShippingForm form) {
        return refuseBooking(requireOrder(form.getShippingEntityId()));
    }

    /**
     * A shipment is still being created (its number comes in a few seconds), or every shipment already has its shipping
     * data (a courier booked in another tab): nothing is to be booked now.
     */
    private static String refuseBooking(Order order) {
        if (order.hasShipmentBeingCreated()) {
            return "shipping.error.creating";
        }
        return order.hasShipmentToBook() ? null : "shipping.error.all.defined";
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
    protected ShipmentCreationCheckRequest.ShipmentCreationCheckRequestBuilder creationSeed(ShippingForm form) {
        return ShipmentCreationCheckRequest.builder()
                .ownerType(ShipmentOwnerType.ORDER)
                .ownerId(form.getShippingEntityId())
                .pickUpAddressId(form.getPickUpAddressId());
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
