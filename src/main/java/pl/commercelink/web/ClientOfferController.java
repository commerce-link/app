package pl.commercelink.web;

import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.checkout.Checkout;
import pl.commercelink.checkout.CheckoutResponse;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.invoicing.api.SplitPaymentPolicy;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ClientDataDto;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@Controller
@RequestMapping("/store/{storeId}/individual/offer/{offerId}")
public class ClientOfferController {

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private Checkout checkout;

    @Autowired
    private InvoicingService invoicingService;

    @Autowired
    private MessageSource messageSource;

    @GetMapping("")
    public String getOfferForClient(@PathVariable("storeId") String storeId, @PathVariable("offerId") String offerId, Model model) {
        Optional<Basket> existingOfferOpt = basketsRepository.findById(storeId, offerId);
        if (!existingOfferOpt.isPresent()) {
            model.addAttribute("error", "Offer not found");
            return "error";
        }
        Basket existingOffer = existingOfferOpt.get();

        Store store = storesRepository.findById(storeId);

        ClientDataDto form = new ClientDataDto();
        form.setBillingDetails(BillingDetails._default());
        form.setShippingDetails(ShippingDetails._default());

        boolean isSplitPaymentRequired = existingOffer.getBillingDetails() != null && SplitPaymentPolicy.isRequired(
                existingOffer.getBillingDetails().toBillingParty(),
                existingOffer.getTotalPrice(),
                store.getInvoicingConfiguration().isSplitPaymentsEnabled()
        );

        DeliveryOption deliveryOption = existingOffer.resolveDeliveryOption(store).orElse(null);
        double totalPrice = existingOffer.getTotalPrice() + existingOffer.getDeliveryPrice(store);
        model.addAttribute("offer", existingOffer);
        model.addAttribute("deliveryOption", deliveryOption);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("totalPriceNet", Price.fromGross(totalPrice, DEFAULT_VAT_RATE).netValue());
        model.addAttribute("store", store);
        model.addAttribute("bankAccount", store.getDefaultBankAccount() != null ? store.getDefaultBankAccount() : null);
        model.addAttribute("branding", store.getBranding() != null ? store.getBranding() : new Branding());
        model.addAttribute("form", form);
        model.addAttribute("submitted",  existingOffer.getBillingDetails() != null && existingOffer.getShippingDetails() != null);
        model.addAttribute("isSplitPaymentRequired", isSplitPaymentRequired);

        return "clientOffer";
    }

    @PostMapping("/submit")
    public String submitClientOfferForm(@PathVariable("storeId") String storeId, @PathVariable String offerId, @ModelAttribute ClientDataDto clientDataDto) {
        Optional<Basket> basketOpt = basketsRepository.findById(storeId, offerId);
        Basket basket = basketOpt.get();

        basket.setBillingDetails(clientDataDto.getBillingDetails());
        basket.setShippingDetails(clientDataDto.getShippingDetails());

        basketsRepository.save(basket);
        return "redirect:/store/" + storeId + "/individual/offer/" + offerId;
    }

    @PostMapping("/checkout")
    public RedirectView payByStripe(@PathVariable("storeId") String storeId, @PathVariable("offerId") String offerId, Locale locale) throws StripeException, IOException {
        CheckoutResponse checkoutResponse = checkout.create(storeId, offerId);
        return new RedirectView(checkoutResponse.getUrl());
    }

    @PostMapping("/send-proforma-invoice")
    public String createProformaInvoice(@PathVariable("storeId") String storeId, @PathVariable String offerId, Locale locale, RedirectAttributes redirectAttributes) {
        Basket basket = basketsRepository.findById(storeId, offerId).get();

        InvoicingService.OperationResult op = invoicingService.createProforma(basket, locale, true);

        if (op.hasError()) {
            redirectAttributes.addFlashAttribute("errorMessage", op.getErrorMessage());
            return "redirect:/store/" + storeId + "/individual/offer/" + offerId;
        }

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("offers.send.invoice.success", null, locale));
        return "redirect:/store/" + storeId + "/individual/offer/" + offerId;
    }

}
