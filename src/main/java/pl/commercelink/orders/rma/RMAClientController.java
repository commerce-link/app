package pl.commercelink.orders.rma;

import jakarta.servlet.http.HttpServletResponse;
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
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.clientreturn.ClientReturnFormCheck;
import pl.commercelink.web.clientreturn.ClientReturnNotice;
import pl.commercelink.web.clientreturn.ClientReturnState;
import pl.commercelink.web.clientreturn.ClientReturnView;
import pl.commercelink.web.clientreturn.ReturnPackageOption;
import pl.commercelink.web.clientreturn.ReturnPackageOptions;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/store/{storeId}/client/rma/{rmaId}")
public class RMAClientController {

    static final String UNAVAILABLE = "client-return/unavailable";
    static final String NOTICE = "clientReturnNotice";
    static final String FLASH_FORM = "clientReturnForm";
    static final String FLASH_ERRORS = "clientReturnErrors";
    private static final String FORM_ANCHOR = "#return-" + ClientReturnNotice.FORM;
    private static final String PARCEL_ANCHOR = "#return-" + ClientReturnNotice.PARCEL;

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
    public String getRMAForClient(@PathVariable("storeId") String storeId, @PathVariable("rmaId") String rmaId, Model model,
                                  HttpServletResponse response, Locale locale) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return unavailable(null, storeId, rmaId, model, response, locale);
        }
        RMA rma = rmaRepository.findById(storeId, rmaId);
        if (rma == null || rma.getStatus() == null || rma.hasOneOfTheStatuses(RMAStatus.New)) {
            return unavailable(store, storeId, rmaId, model, response, locale);
        }

        List<RMAItem> rmaItems = rmaItemsRepository.findByRmaId(rma.getRmaId());
        if (rmaItems.isEmpty()) {
            return unavailable(store, storeId, rmaId, model, response, locale);
        }

        ClientReturnView view = ClientReturnView.of(rma, rmaItems, store, LocalDateTime.now());
        if (view.state() == ClientReturnState.BOOK_UNAVAILABLE) {
            // the customer only learns the courier cannot be booked yet; the store has to fix its return settings
            log.error("Return form of RMA {} in store {} is not available: missing {}", rmaId, storeId,
                    view.missingConfiguration());
        }

        // A refused submission comes back flashed with the customer's own values and the errors of its fields.
        RMAReturnForm form = (RMAReturnForm) model.asMap().get(FLASH_FORM);
        if (form == null) {
            form = new RMAReturnForm();
            form.setShippingDetails(ShippingDetails._default());
        }
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) model.asMap().get(FLASH_ERRORS);
        List<ReturnPackageOption> packageOptions = ReturnPackageOptions.of(store);
        String path = returnPath(storeId, rmaId);

        model.addAttribute("publicPage", PublicPageFactory.of(store, path, locale));
        model.addAttribute("view", view);
        model.addAttribute("packageOptions", packageOptions);
        model.addAttribute("selectedPackage", ReturnPackageOptions.selected(packageOptions, form.getSelectedPackageTemplateId()));
        model.addAttribute("form", form);
        model.addAttribute("errors", errors != null ? errors : Map.of());
        model.addAttribute("returnPath", path);

        return "client-return";
    }

    @PostMapping("submit")
    public String postClientBillingShippingForm(@PathVariable("storeId") String storeId, @PathVariable("rmaId") String rmaId,
                                                @ModelAttribute RMAReturnForm rmaReturnForm, Model model,
                                                RedirectAttributes redirectAttributes, HttpServletResponse response,
                                                Locale locale) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return unavailable(null, storeId, rmaId, model, response, locale);
        }
        RMA rma = rmaRepository.findById(storeId, rmaId);
        if (rma == null || rma.getStatus() == null || rma.hasOneOfTheStatuses(RMAStatus.New)) {
            return unavailable(store, storeId, rmaId, model, response, locale);
        }
        if (!rma.hasOneOfTheStatuses(RMAStatus.Approved)) {
            // An old browser tab of a return the store has moved on (e.g. after a booking from another tab): the page
            // shows where the return is now, which a "form not available" page would hide; nothing is booked.
            List<RMAItem> rmaItems = rmaItemsRepository.findByRmaId(rma.getRmaId());
            if (rmaItems.isEmpty()) {
                return unavailable(store, storeId, rmaId, model, response, locale);
            }
            if (rma.hasOneOfTheStatuses(RMAStatus.WaitingForItems)) {
                redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.info(ClientReturnNotice.PARCEL, returnInProgress(locale)));
                return redirectTo(storeId, rmaId, anchorOf(ClientReturnView.of(rma, rmaItems, store, LocalDateTime.now()),
                        ClientReturnNotice.PARCEL));
            }
            return redirectTo(storeId, rmaId, "");
        }

        // An old browser tab may still show the form: once the store has the pickup address or the customer's return
        // parcel exists, a booking from here would be a second courier, and would overwrite the operator's address.
        if (hasCustomersReturnParcel(rma)) {
            redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.info(ClientReturnNotice.PARCEL, returnInProgress(locale)));
            return redirectTo(storeId, rmaId, PARCEL_ANCHOR);
        }
        if (rma.getShippingDetails() != null) {
            redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.info(ClientReturnNotice.PARCEL,
                    messageSource.getMessage("client.return.notice.storeBooks", null, locale)));
            // the cards do not depend on the items; without a parcel card the notice stands above the first card
            return redirectTo(storeId, rmaId, anchorOf(ClientReturnView.of(rma, List.of(), store, LocalDateTime.now()),
                    ClientReturnNotice.PARCEL, "address"));
        }

        String missingConfiguration = ClientReturnView.missingConfiguration(store);
        if (missingConfiguration != null) {
            // the page shows no form then, so field errors would never be seen; the store has to fix its settings
            log.error("Return form of RMA {} in store {} was submitted, but the store cannot book returns: missing {}",
                    rmaId, storeId, missingConfiguration);
            return refuse(redirectAttributes, rmaReturnForm, storeId, rmaId, unavailableReason(locale));
        }
        List<ReturnPackageOption> packageOptions = ReturnPackageOptions.of(store);
        Map<String, String> errors = ClientReturnFormCheck.validate(rmaReturnForm, packageOptions);
        if (!errors.isEmpty()) {
            // nothing invalid may reach the carrier: the booking is paid; the typed values come back with the errors
            redirectAttributes.addFlashAttribute(FLASH_FORM, rmaReturnForm);
            redirectAttributes.addFlashAttribute(FLASH_ERRORS, errors);
            return redirectTo(storeId, rmaId, FORM_ANCHOR);
        }
        RMAReturnForm checkedForm = ClientReturnFormCheck.normalized(rmaReturnForm);

        RMAShipmentRequest request = new RMAShipmentRequest();
        request.setRmaId(rmaId);
        request.setPackageTemplateId(checkedForm.getSelectedPackageTemplateId());
        request.setCustomerAddress(checkedForm.getShippingDetails());
        request.setInsuranceValue(rma.getShippingInsurance());

        // resolved before the carrier is called: a failed lookup afterwards would land in the generic catch and tell
        // the customer that a booking which did start had failed
        String bookedText = messageSource.getMessage("client.return.notice.booked", null, locale);
        String inProgressText = returnInProgress(locale);
        String failedText = messageSource.getMessage("client.return.notice.failed", null, locale);
        String unavailableText = unavailableReason(locale);

        try {
            ShipmentCreationStart start = rmaShippingService.startReturnShipment(request, store);
            if (start.outcome() == ShipmentCreationStart.Outcome.GONE) {
                // a return already on the RMA (being created, created, or unconfirmed with a label that may be paid)
                // is the store's to settle: a second submission would book a second courier to the customer
                redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.info(ClientReturnNotice.PARCEL, inProgressText));
                return redirectTo(storeId, rmaId, PARCEL_ANCHOR);
            }
            boolean unconfirmed = start.outcome() == ShipmentCreationStart.Outcome.REFUSED && holdsReturn(storeId, rmaId);
            if (start.outcome() == ShipmentCreationStart.Outcome.REFUSED && !unconfirmed) {
                // a clean refusal left nothing on the RMA: the customer corrects the data and submits again; only the
                // provider's own answer (e.g. a wrong postcode) is meant for the customer, the adapter's words are not
                String reason = start.providerAnswer() && start.error() != null ? start.error()
                        : failedText;
                return refuse(redirectAttributes, rmaReturnForm, storeId, rmaId, reason);
            }
            // the creation saved its placeholder on the RMA: these changes go onto a fresh read; an unconfirmed return
            // gets them too, so the operator can book it again with what the customer chose
            try {
                optimisticLockingExecutor.modifyAndSave(
                        () -> rmaRepository.findById(storeId, rmaId),
                        fresh -> {
                            fresh.markAsWaitingForItems();
                            fresh.setShippingDetails(checkedForm.getShippingDetails());
                            fresh.setReturnPackageTemplateId(checkedForm.getSelectedPackageTemplateId());
                        },
                        rmaRepository::save);
            } catch (RuntimeException e) {
                // the return is already being booked: telling the customer it failed would invite a second one
                log.error("Return shipment of RMA {} in store {} was started, but the RMA was not moved to waiting "
                        + "for the items nor given the customer's address", rmaId, storeId, e);
            }
            if (unconfirmed) {
                // its reason is the operator's (check the provider's panel), never the customer's
                redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.info(ClientReturnNotice.PARCEL, inProgressText));
                return redirectTo(storeId, rmaId, PARCEL_ANCHOR);
            }
            redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.ok(ClientReturnNotice.PARCEL, bookedText));
            return redirectTo(storeId, rmaId, PARCEL_ANCHOR);
        } catch (InvalidReturnConfigurationException e) {
            // the exception's words are the store administrator's, not the customer's
            log.error("Return shipment of RMA {} in store {} was not started: {}", rmaId, storeId, e.getMessage());
            redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.bad(ClientReturnNotice.FORM, unavailableText));
            return redirectTo(storeId, rmaId, FORM_ANCHOR);
        } catch (Exception e) {
            log.error("Return shipment of RMA {} in store {} failed to start", rmaId, storeId, e);
            return refuse(redirectAttributes, rmaReturnForm, storeId, rmaId, failedText);
        }
    }

    // The page does not confirm that the return exists: a missing one and a closed one read the same. The language
    // link points to the return page, also after a refused submission.
    private static String unavailable(Store store, String storeId, String rmaId, Model model, HttpServletResponse response,
                                      Locale locale) {
        String path = returnPath(storeId, rmaId);
        model.addAttribute("publicPage", store != null
                ? PublicPageFactory.of(store, path, locale)
                : PublicPageFactory.unknownStore(path, locale));
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return UNAVAILABLE;
    }

    /** A refusal that left the return on the RMA was not a clean refusal of the provider, which removes it. */
    private boolean holdsReturn(String storeId, String rmaId) {
        RMA fresh = rmaRepository.findById(storeId, rmaId);
        return fresh != null && fresh.getShipments() != null
                && fresh.getShipments().stream().anyMatch(CustomerReturnRetry::isCustomerReturn);
    }

    /** The customer's own return parcel; a marketplace imports the customer's parcel without a provider. */
    private static boolean hasCustomersReturnParcel(RMA rma) {
        return rma.getShipments() != null && rma.getShipments().stream()
                .anyMatch(s -> CustomerReturnRetry.isCustomerReturn(s) || (rma.isMarketplaceReturn() && s.getProvider() == null));
    }

    /** Nothing was booked: the customer sees the reason above the form, still filled in as it was sent. */
    private static String refuse(RedirectAttributes redirectAttributes, RMAReturnForm postedForm, String storeId, String rmaId,
                                 String reason) {
        redirectAttributes.addFlashAttribute(NOTICE, ClientReturnNotice.bad(ClientReturnNotice.FORM, reason));
        redirectAttributes.addFlashAttribute(FLASH_FORM, postedForm);
        return redirectTo(storeId, rmaId, FORM_ANCHOR);
    }

    /** The anchor of the first of the cards the page shows, or none when it shows none of them. */
    private static String anchorOf(ClientReturnView view, String... cards) {
        for (String card : cards) {
            if (view.cards().contains(card)) {
                return "#return-" + card;
            }
        }
        return "";
    }

    private static String returnPath(String storeId, String rmaId) {
        return "/store/" + storeId + "/client/rma/" + rmaId;
    }

    private static String redirectTo(String storeId, String rmaId, String anchor) {
        return "redirect:" + returnPath(storeId, rmaId) + anchor;
    }

    private String returnInProgress(Locale locale) {
        return messageSource.getMessage("client.return.notice.inProgress", null, locale);
    }

    private String unavailableReason(Locale locale) {
        return messageSource.getMessage("client.return.notice.unavailable", null, locale);
    }
}
