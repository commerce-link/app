package pl.commercelink.orders.rma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;

@Slf4j
@Controller
@RequestMapping("/store/{storeId}/client/rma/{rmaId}")
public class RMAClientController {

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private RMAItemsRepository rmaItemsRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private RMAShippingService rmaShippingService;

    @Autowired
    private OptimisticLockingExecutor optimisticLockingExecutor;

    @Autowired
    private MessageSource messageSource;

    @GetMapping("")
    public String getRMAForClient(@PathVariable("storeId") String storeId, @PathVariable("rmaId") String rmaId, Model model) {
        RMA rma = rmaRepository.findById(storeId, rmaId);
        if (rma == null || rma.hasOneOfTheStatuses(RMAStatus.New, RMAStatus.Rejected, RMAStatus.Completed)) {
            return "error/404";
        }

        List<RMAItem> rmaItems = rmaItemsRepository.findByRmaId(rma.getRmaId());
        if (rmaItems.isEmpty()) {
            return "error/404";
        }

        Store store = storesRepository.findById(storeId);

        try {
            rmaShippingService.validateStoreReturnConfiguration(store);
        } catch (InvalidReturnConfigurationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "error/404";
        }

        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(ShippingDetails._default());

        List<RMAReturnOption> returnOptions = rmaShippingService.getAvailableReturnOptions(store);

        model.addAttribute("rma", rma);
        model.addAttribute("rmaItems", rmaItems);
        model.addAttribute("storeId", storeId);
        model.addAttribute("branding", store.getBranding() != null ? store.getBranding() : new Branding());
        model.addAttribute("submitted", rma.getShippingDetails() != null);
        model.addAttribute("form", form);
        model.addAttribute("returnedPackageTemplates", returnOptions);

        return "client-return";
    }

    @PostMapping("submit")
    public String postClientBillingShippingForm(@PathVariable("storeId") String storeId, @PathVariable("rmaId") String rmaId, @ModelAttribute RMAReturnForm rmaReturnForm, Model model, RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(storeId, rmaId);
        if (rma == null || !rma.hasOneOfTheStatuses(RMAStatus.Approved)) {
            return "error/404";
        }

        Store store = storesRepository.findById(storeId);

        RMAShipmentRequest request = new RMAShipmentRequest();
        request.setRmaId(rmaId);
        request.setPackageTemplateId(rmaReturnForm.getSelectedPackageTemplateId());
        request.setCustomerAddress(rmaReturnForm.getShippingDetails());
        request.setInsuranceValue(rma.getShippingInsurance());

        try {
            ShipmentCreationStart start = rmaShippingService.startReturnShipment(request, store);
            if (start.outcome() == ShipmentCreationStart.Outcome.GONE) {
                // a return already on the RMA (being created, created, or unconfirmed with a label that may be paid)
                // is the store's to settle: a second submission would book a second courier to the customer
                redirectAttributes.addFlashAttribute("warningMessage", returnInProgress(locale));
                return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
            }
            boolean unconfirmed = start.outcome() == ShipmentCreationStart.Outcome.REFUSED && holdsReturn(storeId, rmaId);
            if (start.outcome() == ShipmentCreationStart.Outcome.REFUSED && !unconfirmed) {
                // a clean refusal left nothing on the RMA: the customer corrects the data and submits again
                String reason = start.error() != null ? start.error()
                        : messageSource.getMessage("rma.shipment.creation.failed", null, locale);
                redirectAttributes.addFlashAttribute("errorMessage", reason);
                return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
            }
            // the creation saved its placeholder on the RMA: these changes go onto a fresh read; an unconfirmed return
            // gets them too, so the operator can book it again with what the customer chose
            try {
                optimisticLockingExecutor.modifyAndSave(
                        () -> rmaRepository.findById(storeId, rmaId),
                        fresh -> {
                            fresh.markAsWaitingForItems();
                            fresh.setShippingDetails(rmaReturnForm.getShippingDetails());
                            fresh.setReturnPackageTemplateId(rmaReturnForm.getSelectedPackageTemplateId());
                        },
                        rmaRepository::save);
            } catch (RuntimeException e) {
                // the return is already being booked: telling the customer it failed would invite a second one
                log.error("Return shipment of RMA {} in store {} was started, but the RMA was not moved to waiting "
                        + "for the items nor given the customer's address", rmaId, storeId, e);
            }
            if (unconfirmed) {
                // its reason is the operator's (check the provider's panel), never the customer's
                redirectAttributes.addFlashAttribute("warningMessage", returnInProgress(locale));
                return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
            }
            redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("rma.shipment.has.been.created", null, locale));
            return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
        } catch (InvalidReturnConfigurationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.shipment.creation.failed", null, locale));
            return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
        }
    }

    /** A refusal that left the return on the RMA was not a clean refusal of the provider, which removes it. */
    private boolean holdsReturn(String storeId, String rmaId) {
        RMA fresh = rmaRepository.findById(storeId, rmaId);
        return fresh != null && fresh.getShipments() != null
                && fresh.getShipments().stream().anyMatch(CustomerReturnRetry::isCustomerReturn);
    }

    private String returnInProgress(Locale locale) {
        return messageSource.getMessage("rma.shipment.return.in.progress", null, locale);
    }
}
