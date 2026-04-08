package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
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
    private EmailClient emailClient;

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
            RMAShipmentResult result = rmaShippingService.createReturnShipment(request, store);
            if (!result.getShipments().isSuccess()) {
                redirectAttributes.addFlashAttribute("errorMessage", result.getShipments().getMessage());
                return "redirect:/store/" + storeId + "/client/rma/" + rmaId;
            }

            rma.markAsWaitingForItems();
            rma.setShippingDetails(rmaReturnForm.getShippingDetails());
            rma.setShipments(result.getShipments().getPayload());

            sendEmail(rma, result);

            rmaRepository.save(rma);
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

    private void sendEmail(RMA rma, RMAShipmentResult result) {
        RMACarrierConfirmationEmailNotification msg = new RMACarrierConfirmationEmailNotification(
                rma.getEmail(),
                rma.getEmail(),
                rma.getRmaId(),
                rma.getOrderId(),
                rma.getShippingDetails()
        );

        for (String trackingUrl : result.getTrackingUrls()) {
            msg.addTrackingUrl(trackingUrl);
        }

        boolean emailSentSuccess = emailClient.send(
                rma.getStoreId(),
                EmailNotificationType.RMA_CARRIER_CONFIRMATION,
                msg
        );

        if (emailSentSuccess) {
            rma.addEvent(new Event(
                    EventType.email,
                    EmailNotificationType.RMA_CARRIER_CONFIRMATION.name(),
                    LocalDateTime.now()
            ));
        }
    }

}