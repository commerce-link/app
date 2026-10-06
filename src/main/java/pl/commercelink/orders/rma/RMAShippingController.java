package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.AbstractShippingController;
import pl.commercelink.shipping.ShipmentCreationCheckRequest;
import pl.commercelink.shipping.ShipmentOwnerType;
import pl.commercelink.stores.RMAConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import pl.commercelink.shipping.DeliveryTarget;
import pl.commercelink.shipping.ShippingPageView;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

@Controller
@RequestMapping("/dashboard/rma/{rmaId}/shipping")
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class RMAShippingController extends AbstractShippingController {

    @Autowired
    private DeliveredPredicate deliveredPredicate;

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private RMAItemsRepository rmaItemsRepository;


    @PostMapping("/shipItemsToDistributor")
    public String initiateShippingToDistributor(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, Model model, RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);

        List<RMAItem> qualifiedRmaItems = form.getSelectedRMAItems().stream()
                .filter(i -> i.hasOneOfTheStatuses(RMAItemStatus.Received))
                .toList();

        if (qualifiedRmaItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.ship.to.distributor.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        if (!deliveredPredicate.isFromSameSource(getStoreId(), qualifiedRmaItems)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.ship.to.distributor.different.providers", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        List<ShippingDetails> shippingDetailsList = retrieveRMACentersShippingDetailsList(
                qualifiedRmaItems.get(0).getDeliveryId()
        );

        ShippingForm shippingForm = new ShippingForm(rma.getRmaId(), "rma");
        shippingForm.setOrderItemIds(qualifiedRmaItems.stream().map(RMAItem::getItemId).collect(Collectors.toList()));
        shippingForm.setToClient(false);

        return renderShippingForm(getStore(), shippingForm, shippingDetailsList, model);
    }

    @PostMapping("/shipItemsToClient")
    public String initiateShippingToClient(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Model model, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);

        List<RMAItem> qualifiedRmaItems = form.getSelectedRMAItems().stream()
                .filter(i -> i.hasOneOfTheStatuses(RMAItemStatus.Received))
                .toList();

        if (qualifiedRmaItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.ship.to.client.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        if (rma.getShippingDetails() == null || !rma.getShippingDetails().isProperlyFilled()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.missing.shipping.details", null, locale));
            return "redirect:/dashboard/rma/" + rma.getRmaId();
        }

        ShippingForm shippingForm = new ShippingForm(rma.getRmaId(), "rma");
        shippingForm.setOrderItemIds(qualifiedRmaItems.stream().map(RMAItem::getItemId).collect(Collectors.toList()));
        shippingForm.setToClient(true);

        return renderShippingForm(getStore(), shippingForm, Collections.singletonList(rma.getShippingDetails()), model);
    }

    @Override
    protected double calculateShippingInsurance(ShippingForm form) {
        return rmaRepository.findById(getStoreId(), form.getShippingEntityId()).getShippingInsurance();
    }

    @Override
    protected List<ShippingDetails> retrieveShippingDetailsList(ShippingForm form) {
        RMA rma = rmaRepository.findById(getStoreId(), form.getShippingEntityId());

        if (form.isToClient()) {
            return Collections.singletonList(rma.getShippingDetails());
        }

        List<RMAItem> selectedItems = filterSelectedItems(rma, form);

        return retrieveRMACentersShippingDetailsList(selectedItems.get(0).getDeliveryId());
    }

    @Override
    protected ShipmentCreationCheckRequest.ShipmentCreationCheckRequestBuilder creationSeed(ShippingForm form) {
        return ShipmentCreationCheckRequest.builder()
                .ownerType(ShipmentOwnerType.RMA)
                .ownerId(form.getShippingEntityId())
                .pickUpAddressId(form.getPickUpAddressId())
                .toClient(form.isToClient())
                .itemIds(List.copyOf(form.getOrderItemIds()));
    }

    /** A shipment still being created would be replaced by this booking, and its paid label lost with it. */
    @Override
    protected String refuseBooking(ShippingForm form) {
        RMA rma = rmaRepository.findById(getStoreId(), form.getShippingEntityId());
        return rma != null && rma.getShipments().stream().anyMatch(Shipment::isCreating) ? "shipping.error.creating" : null;
    }

    private List<RMAItem> filterSelectedItems(RMA rma, ShippingForm form) {
        List<RMAItem> rmaItems = rmaItemsRepository.findByRmaId(rma.getRmaId());
        return rmaItems.stream()
                .filter(item -> form.getOrderItemIds().contains(item.getItemId()))
                .collect(Collectors.toList());
    }

    @Override
    protected DeliveryTarget resolveDeliveryTarget(ShippingForm form) {
        RMAConfiguration rmaConfiguration = getStore().getRmaConfiguration();
        if (rmaConfiguration == null || rmaConfiguration.getCarrier() == null) {
            return new DeliveryTarget(null, null, null);
        }
        return new DeliveryTarget(getStore().getConfigurationValue(IntegrationType.SHIPPING_PROVIDER),
                rmaConfiguration.getCarrier().getName(), null);
    }

    @Override
    protected ShippingPageView pageView(ShippingForm form) {
        return new ShippingPageView("/dashboard/rma/" + form.getShippingEntityId(), "rma.details", null,
                form.isToClient() ? "shipping.lead.rma.client" : "shipping.lead.rma.center", form.getShippingEntityId());
    }
}
