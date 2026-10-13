package pl.commercelink.shipping;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import pl.commercelink.rest.client.HttpClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.shipping.api.OrderReference;
import pl.commercelink.shipping.api.ShipmentRequest;
import pl.commercelink.shipping.api.ShippingEstimate;
import pl.commercelink.orders.*;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.orders.rma.RMACentersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractShippingController {

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    protected ShippingService shippingService;

    @Autowired
    private RMACentersRepository rmaCentersRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    protected MessageSource messageSource;

    @Autowired
    protected ShipmentCreationService shipmentCreationService;

    /** The unpaid amount in whole grosze: the form must show 59.98, not the double's 59.980000000000004. */
    protected static double roundedUnpaidAmount(Order order) {
        return java.math.BigDecimal.valueOf(order.getUnpaidAmount()).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    @PostMapping("/template")
    public String loadTemplates(@ModelAttribute ShippingForm form, Model model) {
        Store store = getStore();

        List<ParcelForm> parcels = shippingService.retrieveParcelsListBasedOnPackageTemplate(
                calculateShippingInsurance(form),
                form.getPackageTemplateId(),
                store
        );
        parcels.addAll(Collections.nCopies(4, ParcelForm.empty()));
        form.setParcels(parcels);

        double defaultCodAmount = 0;
        if (form.getShippingEntityType().equals("orders")) {
            Order order = ordersRepository.findById(getStoreId(), form.getShippingEntityId());
            defaultCodAmount = roundedUnpaidAmount(order);
        }
        form.setCashOnDeliveryAmount(defaultCodAmount);

        return renderShippingForm(store, form, retrieveShippingDetailsList(form), model);
    }

    @PostMapping("/estimate")
    public String estimateShipping(@ModelAttribute ShippingForm form, Model model, Locale locale) {
        Store store = getStore();

        try {
            DeliveryTarget deliveryTarget = resolveDeliveryTarget(form);
            List<ShippingEstimate> estimates = shippingService.estimateServicePrices(form, store, deliveryTarget);
            model.addAttribute("servicePrices", estimates);
        } catch (HttpClientException ex) {
            return handleHttpClientException(ex, store, form, model);
        } catch (ShippingUnavailableException ex) {
            // a store without a courier account (RMA and warehouse reach this page without the order's check): the
            // reason as the page's own alert, the form as it was
            model.addAttribute("shippingUnavailable", messageSource.getMessage(noProviderKey(), null, locale));
        }

        return renderShippingForm(store, form, retrieveShippingDetailsList(form), model);
    }

    @PostMapping("/create")
    public String createShipping(@ModelAttribute ShippingForm form, RedirectAttributes redirectAttributes, Locale locale) {
        String createdAfterAll = redirectIfCreatedAfterAll(form, redirectAttributes, locale);
        if (createdAfterAll != null) {
            return createdAfterAll;
        }
        // a tab left open, a page restored from the back/forward cache or a re-sent form must not book (and pay for) a
        // second label once the first booking is saved; the check is not atomic, so two requests in flight at the same
        // time are only kept apart by the button being disabled on submit (shipping-booking.js)
        String refusal = refuseBooking(form);
        if (refusal == null) {
            refusal = refuseIntegration(form);
        }
        if (refusal != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(refusal, null, locale));
            return "redirect:" + getEntityUrl(form);
        }
        Store store = getStore();
        ShipmentCreationStart start;
        try {
            DeliveryTarget target = resolveDeliveryTarget(form);
            ShipmentRequest request = ShippingService.withOrderReference(
                    shippingService.buildRequest(form, store, target), orderReference(form));
            start = shipmentCreationService.start(creationSeed(form).storeId(getStoreId()).build(), request, store,
                    placeholder(form, store, target));
        } catch (ShippingUnavailableException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(noProviderKey(), null, locale));
            return "redirect:" + getEntityUrl(form);
        }
        return redirectAfterStart(start, form, redirectAttributes, locale);
    }

    /** Where the operator lands after a creation started, was refused or found its owner gone. */
    protected String redirectAfterStart(ShipmentCreationStart start, ShippingForm form, RedirectAttributes redirectAttributes,
                                        Locale locale) {
        switch (start.outcome()) {
            case REFUSED -> {
                redirectAttributes.addFlashAttribute("errorMessage", start.error());
                return "redirect:" + form.getShippingAction();
            }
            case GONE -> redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(goneMessageKey(), null, locale));
            case STARTED -> redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage(startedMessageKey(), null, locale));
        }
        return "redirect:" + getEntityUrl(form);
    }

    /** The shipment shown while the provider creates it: the chosen carrier and the delivery point. */
    private static Shipment placeholder(ShippingForm form, Store store, DeliveryTarget target) {
        Shipment placeholder = new Shipment(target.pointCode() != null ? ShipmentType.PickupPoint : ShipmentType.Courier);
        placeholder.setCollectionPointCode(target.pointCode());
        if (store.getShippingConfiguration() != null) {
            store.getShippingConfiguration().getAuthorizedCarriers().stream()
                    .filter(carrier -> carrier.getId().equals(form.getServiceId()))
                    .findFirst()
                    .ifPresent(carrier -> placeholder.setCarrier(carrier.getName()));
        }
        return placeholder;
    }

    protected String renderShippingForm(Store store, ShippingForm shippingForm, List<ShippingDetails> shippingDetailsList, Model model) {
        if (shippingForm.getShippingDetails() == null && !shippingDetailsList.isEmpty()) {
            shippingForm.setShippingDetails(shippingDetailsList.get(0));
        }
        model.addAttribute("shippingForm", shippingForm);
        model.addAttribute("shippingEntityId", shippingForm.getShippingEntityId());
        model.addAttribute("shippingDetailsList", shippingDetailsList);
        model.addAttribute("deliveryPointCode", resolveDeliveryTarget(shippingForm).pointCode());
        model.addAttribute("pickUpAddresses", store.getPickUpAddresses());
        model.addAttribute("packageTemplates", store.getPackageTemplates());
        model.addAttribute("shippingPage", pageView(shippingForm));

        return "shipping";
    }

    protected String handleHttpClientException(HttpClientException ex, Store store, ShippingForm form, Model model) {
        String error = ex.getResponseBody();
        if (StringUtils.isBlank(error)) {
            error = ex.getMessage();
        }
        model.addAttribute("errorMessage", error);

        List<ShippingDetails> shippingDetailsList = retrieveShippingDetailsList(form);

        return renderShippingForm(store, form, shippingDetailsList, model);
    }

    protected List<ShippingDetails> retrieveRMACentersShippingDetailsList(String deliveryId) {
        var delivery = deliveriesRepository.findById(getStoreId(), deliveryId);

        return rmaCentersRepository.findByProviderName(getStoreId(), delivery.getProvider())
                .stream()
                .map(RMACenter::getShippingDetails)
                .collect(Collectors.toList());
    }

    protected String getEntityUrl(ShippingForm form) {
        String url = "/dashboard/" + form.getShippingEntityType();
        String entityId = form.getShippingEntityId();
        if (StringUtils.isNotBlank(entityId)) {
            url += "/" + entityId;
        }
        return url;
    }

    protected Store getStore() {
        return storesRepository.findById(getStoreId());
    }

    protected String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    /** Message key of the reason shown when the store has no courier account to price or book with. */
    protected String noProviderKey() {
        return "shipping.error.no.provider";
    }

    /**
     * Message key of the reason this booking must not be placed any more, or null. Checked again right before the
     * courier is booked, since the page may be older than the record.
     */
    protected String refuseBooking(ShippingForm form) {
        return null;
    }

    /**
     * Message key of the reason the integration named by the form must not book here, or null. These steps always book
     * through the store's default integration: another one (Wysyłam z Allegro) has its own step.
     */
    protected String refuseIntegration(ShippingForm form) {
        return null;
    }

    /**
     * Where to send the operator instead of booking, when an earlier creation command with an unknown outcome turns out
     * to have created the shipment after all (ShipmentCreationReconciler); null to go on booking.
     */
    protected String redirectIfCreatedAfterAll(ShippingForm form, RedirectAttributes redirectAttributes, Locale locale) {
        return null;
    }

    /** The marketplace order the shipment is for, null for any other record. */
    protected OrderReference orderReference(ShippingForm form) {
        return null;
    }

    protected abstract double calculateShippingInsurance(ShippingForm form);

    protected abstract List<ShippingDetails> retrieveShippingDetailsList(ShippingForm form);

    /** Who the shipment belongs to and what settling it needs; commandId, provider and attempt are filled in later. */
    protected abstract ShipmentCreationCheckRequest.ShipmentCreationCheckRequestBuilder creationSeed(ShippingForm form);

    /** Message key of the reason shown when the owner refused the new shipment (gone, or one is being created). */
    protected String goneMessageKey() {
        return "shipping.create.gone";
    }

    /** Message key of the note shown once the creation started. */
    protected String startedMessageKey() {
        return "shipping.create.started";
    }

    protected abstract DeliveryTarget resolveDeliveryTarget(ShippingForm form);

    protected abstract ShippingPageView pageView(ShippingForm form);

}

