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
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import pl.commercelink.shipping.AbstractShippingController;
import pl.commercelink.shipping.AllegroShipmentFormCheck;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.shipping.ShippingUnavailableException;
import pl.commercelink.shipping.api.DeliveryType;
import pl.commercelink.shipping.api.OrderReference;
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
        } else {
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
     * default one before any choice), the unpaid amount as the cash on delivery amount (the box is checked only when the buyer chose it), insurance at least that much and at most
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
            form.setCashOnDelivery(buyerChoseCashOnDelivery(order, proposal));
            form.setCashOnDeliveryAmount(unpaid);
        }
        int insurance = Math.max(parcel.getValue(), (int) Math.ceil(form.isCashOnDelivery() ? unpaid : 0));
        if (proposal.maxInsurance() != null) {
            insurance = Math.min(insurance, proposal.maxInsurance().intValue());
        }
        parcel.setValue(insurance);
        form.setParcels(new ArrayList<>(List.of(parcel)));
    }

    /**
     * Cash on delivery is pre-checked only when the buyer chose it. An Allegro order paid online often still looks
     * unpaid here, and a pre-checked box would charge the buyer a second time. The marketplace import records the
     * checkout form's payment type as the order's payment source; an order without one falls back to the proposal,
     * which carries a COD limit only for the POSTPAID option the adapter picks for cash-on-delivery orders.
     */
    private static boolean buyerChoseCashOnDelivery(Order order, ShipmentProposal proposal) {
        PaymentSource source = order.getPayments().stream().findFirst().map(Payment::getSource).orElse(null);
        return source != null ? source == PaymentSource.CashOnDelivery : proposal.maxCashOnDelivery() != null;
    }

    @Override
    protected String refuseIntegration(ShippingForm form) {
        String provider = form.getProvider();
        return provider == null || provider.equals(getStore().defaultShippingIntegration())
                ? null : "shipping.integration.error.unavailable";
    }

    @Override
    protected OrderReference orderReference(ShippingForm form) {
        return ShippingService.orderReference(requireOrder(form.getShippingEntityId()));
    }

    /**
     * "Utwórz przesyłkę" of Wysyłam z Allegro. The integration must be one the order can ship through right now (the
     * same choice the page showed: an order placed on Allegro whose method Allegro accepts); a post outside it is a
     * forged or long-stale form and is answered with 400 before Allegro is asked for anything. Limits are checked
     * first; a refused field re-renders the form with its reason.
     */
    @PostMapping("/allegro/create")
    public String createAllegroShipping(@PathVariable("orderId") String orderId, @ModelAttribute ShippingForm form,
                                        Model model, RedirectAttributes redirectAttributes, Locale locale) {
        form.setShippingEntityId(orderId);
        form.setShippingEntityType("orders");
        form.setProvider(ShippingIntegrationChoice.ALLEGRO);
        Order order = requireOrder(orderId);
        String refusal = refuseBooking(order);
        if (refusal != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(refusal, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        Store store = getStore();
        List<ShippingIntegrationOption> options = shippingIntegrationChoice.forOrder(store, order);
        model.addAttribute("integrationOptions", options);
        ShipmentProposal proposal = ShippingIntegrationChoice.availableNamed(options, ShippingIntegrationChoice.ALLEGRO)
                .map(ShippingIntegrationOption::proposal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Order " + orderId + " cannot be shipped through Wysyłam z Allegro"));
        List<AllegroShipmentFormCheck.Problem> problems =
                AllegroShipmentFormCheck.check(form, proposal, store.getDefaultBankAccount() != null);
        if (!problems.isEmpty()) {
            model.addAttribute("allegroErrors", shippingIntegrationViews.errors(problems, locale));
            return renderShippingForm(store, form, retrieveShippingDetailsList(form), model);
        }
        ShipmentCreationStart start;
        try {
            start = shipmentCreationService.start(creationSeed(form).storeId(getStoreId()).build(),
                    shippingService.buildAllegroRequest(form, store, order), store, allegroPlaceholder(proposal),
                    ShippingIntegrationChoice.ALLEGRO);
        } catch (ShippingUnavailableException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("shipping.integration.error.unavailable", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        return redirectAfterStart(start, form, redirectAttributes, locale);
    }

    /** What the order shows while Allegro creates the shipment: the buyer's method and point. */
    static Shipment allegroPlaceholder(ShipmentProposal proposal) {
        String point = proposal.deliveryPoint() == null ? null : StringUtils.trimToNull(proposal.deliveryPoint().code());
        boolean toPoint = point != null && proposal.deliveryType() != DeliveryType.DOOR;
        Shipment placeholder = new Shipment(toPoint ? ShipmentType.PickupPoint : ShipmentType.Courier);
        placeholder.setCollectionPointCode(toPoint ? point : null);
        placeholder.setCarrier(proposal.methodName());
        return placeholder;
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
