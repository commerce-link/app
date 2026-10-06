package pl.commercelink.orders.rma;

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
            if (start.outcome() != ShipmentCreationStart.Outcome.STARTED) {
                String reason = start.error() != null ? start.error()
                        : messageSource.getMessage("rma.shipment.creation.failed", null, locale);
                redirectAttributes.addFlashAttribute("errorMessage", reason);
                return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
            }
            // the creation saved its placeholder on the RMA: these changes go onto a fresh read
            optimisticLockingExecutor.modifyAndSave(
                    () -> rmaRepository.findById(storeId, rmaId),
                    fresh -> {
                        fresh.markAsWaitingForItems();
                        fresh.setShippingDetails(rmaReturnForm.getShippingDetails());
                        fresh.setReturnPackageTemplateId(rmaReturnForm.getSelectedPackageTemplateId());
                    },
                    rmaRepository::save);
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
}
